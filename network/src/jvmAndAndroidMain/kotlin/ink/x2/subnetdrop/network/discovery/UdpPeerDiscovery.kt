package ink.x2.subnetdrop.network.discovery

import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.domain.port.DiscoveryEvent
import ink.x2.subnetdrop.domain.port.PeerDiscovery
import ink.x2.subnetdrop.domain.port.PeerReachabilityProbe
import ink.x2.subnetdrop.domain.port.TimestampProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.Collections

internal class UdpPeerDiscovery(
    private val timestampProvider: TimestampProvider,
    private val reachabilityProbe: PeerReachabilityProbe,
    private val acquireMulticast: () -> Unit = {},
    private val releaseMulticast: () -> Unit = {},
) : PeerDiscovery {
    private val mutableEvents = MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = EVENT_BUFFER_SIZE)
    private val lifecycleMutex = Mutex()
    private val peerMutex = Mutex()
    private val livenessTracker = PeerLivenessTracker(OFFLINE_FAILURE_THRESHOLD)
    private val probesInFlight = mutableSetOf<String>()
    private var sessionScope: CoroutineScope? = null
    private var sessionJob: Job? = null
    private var sockets: List<MulticastSocket> = emptyList()
    private var localAnnouncement: DiscoveryPacket? = null

    override val events: Flow<DiscoveryEvent> = mutableEvents.asSharedFlow()

    override suspend fun start(
        localDeviceId: String,
        displayName: String,
        servicePort: Int,
        knownPeers: List<Peer>,
    ) {
        validateLocalDevice(localDeviceId, displayName, servicePort)
        lifecycleMutex.withLock {
            if (sessionJob != null) return
            acquireMulticast()
            val openedSockets = try {
                withContext(Dispatchers.IO) { openSockets() }
            } catch (exception: Exception) {
                releaseMulticast()
                throw exception
            }
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.IO)
            sessionJob = job
            sessionScope = scope
            sockets = openedSockets
            localAnnouncement = DiscoveryPacket(
                deviceId = localDeviceId,
                displayName = displayName,
                servicePort = servicePort,
                replyRequested = true,
            )
            startSession(scope, openedSockets, knownPeers, localDeviceId)
        }
    }

    override suspend fun stop() {
        val job = lifecycleMutex.withLock {
            val activeJob = sessionJob ?: return
            sessionJob = null
            sessionScope = null
            activeJob.cancel()
            sockets.forEach(MulticastSocket::close)
            sockets = emptyList()
            localAnnouncement = null
            activeJob
        }
        withContext(NonCancellable) {
            job.cancelAndJoin()
            peerMutex.withLock {
                livenessTracker.clear()
                probesInFlight.clear()
            }
            releaseMulticast()
        }
    }

    private fun startSession(
        scope: CoroutineScope,
        openedSockets: List<MulticastSocket>,
        knownPeers: List<Peer>,
        localDeviceId: String,
    ) {
        if (openedSockets.isEmpty()) {
            mutableEvents.tryEmit(DiscoveryEvent.Failure("没有可用的局域网组播接口"))
        } else {
            openedSockets.forEach { socket -> scope.launch { receiveLoop(socket, localDeviceId) } }
            scope.launch { announcementLoop() }
        }
        knownPeers.filterNot { it.id == localDeviceId }.forEach(::scheduleProbe)
        scope.launch { heartbeatLoop() }
    }

    private suspend fun receiveLoop(socket: MulticastSocket, localDeviceId: String) {
        val buffer = ByteArray(MAX_PACKET_SIZE_BYTES)
        while (currentCoroutineContext().isActive) {
            val datagram = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(datagram)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (exception: SocketException) {
                socket.close()
                if (currentCoroutineContext().isActive && sockets.all { it.isClosed }) {
                    emitSocketFailure(exception)
                }
                return
            }
            val packet = DiscoveryPacketCodec.decode(datagram.data, datagram.offset, datagram.length) ?: continue
            if (packet.deviceId == localDeviceId) continue
            if (packet.replyRequested) sendReply(socket, datagram.socketAddress)
            packet.toPeer(datagram.address)?.let(::scheduleProbe)
        }
    }

    private suspend fun announcementLoop() {
        for (delayMillis in ANNOUNCEMENT_DELAYS_MS) {
            delay(delayMillis)
            sendAnnouncement()
        }
        while (currentCoroutineContext().isActive) {
            delay(PERIODIC_ANNOUNCEMENT_INTERVAL_MS)
            sendAnnouncement()
        }
    }

    private suspend fun heartbeatLoop() {
        while (currentCoroutineContext().isActive) {
            delay(HEARTBEAT_INTERVAL_MS)
            val peers = peerMutex.withLock { livenessTracker.peers() }
            peers.forEach(::scheduleProbe)
        }
    }

    private fun scheduleProbe(peer: Peer) {
        val scope = sessionScope ?: return
        scope.launch {
            val shouldProbe = peerMutex.withLock { probesInFlight.add(peer.id) }
            if (!shouldProbe) return@launch
            try {
                recordProbeResult(peer, reachabilityProbe.isReachable(peer))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                recordProbeResult(peer, reachable = false)
            } finally {
                peerMutex.withLock { probesInFlight.remove(peer.id) }
            }
        }
    }

    private suspend fun recordProbeResult(peer: Peer, reachable: Boolean) {
        val event = peerMutex.withLock {
            livenessTracker.record(peer, reachable, timestampProvider.nowMillis())
        }
        event?.let { mutableEvents.emit(it) }
    }

    private fun sendAnnouncement() {
        val packet = localAnnouncement ?: return
        val bytes = DiscoveryPacketCodec.encode(packet)
        val target = InetSocketAddress(MULTICAST_GROUP, DISCOVERY_PORT)
        sockets.forEach { socket -> runCatching { socket.send(DatagramPacket(bytes, bytes.size, target)) } }
    }

    private fun sendReply(socket: MulticastSocket, target: java.net.SocketAddress) {
        val packet = localAnnouncement?.copy(replyRequested = false) ?: return
        val bytes = DiscoveryPacketCodec.encode(packet)
        runCatching { socket.send(DatagramPacket(bytes, bytes.size, target)) }
    }

    private fun openSockets(): List<MulticastSocket> = networkInterfaces().mapNotNull { networkInterface ->
        runCatching { openSocket(networkInterface) }.getOrNull()
    }

    private fun openSocket(networkInterface: NetworkInterface): MulticastSocket = MulticastSocket(null).apply {
        reuseAddress = true
        bind(InetSocketAddress(DISCOVERY_PORT))
        soTimeout = RECEIVE_TIMEOUT_MS
        timeToLive = 1
        this.networkInterface = networkInterface
        joinGroup(InetSocketAddress(MULTICAST_GROUP, DISCOVERY_PORT), networkInterface)
    }

    private fun networkInterfaces(): List<NetworkInterface> = Collections
        .list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUsableForDiscovery() }

    private fun NetworkInterface.isUsableForDiscovery(): Boolean = runCatching {
        isUp && !isLoopback && supportsMulticast() && Collections.list(inetAddresses).any { it is Inet4Address }
    }.getOrDefault(false)

    private fun DiscoveryPacket.toPeer(source: InetAddress): Peer? {
        val host = source.hostAddress ?: return null
        return Peer(
            id = deviceId,
            displayName = displayName,
            host = host,
            port = servicePort,
            availability = PeerAvailability.OFFLINE,
            trustState = TrustState.UNPAIRED,
            lastSeenAt = 0L,
        )
    }

    private fun emitSocketFailure(exception: SocketException) {
        mutableEvents.tryEmit(
            DiscoveryEvent.Failure("局域网组播监听失败：${exception.message ?: "socket error"}"),
        )
    }

    private fun validateLocalDevice(localDeviceId: String, displayName: String, servicePort: Int) {
        require(DEVICE_ID_REGEX.matches(localDeviceId)) { "localDeviceId is invalid" }
        require(displayName.isNotBlank() && displayName.length <= MAX_DISPLAY_NAME_LENGTH) {
            "displayName is invalid"
        }
        require(servicePort in 1..65_535) { "servicePort is invalid" }
    }

    private companion object {
        val MULTICAST_GROUP: InetAddress = InetAddress.getByName("224.0.0.167")
        val ANNOUNCEMENT_DELAYS_MS = longArrayOf(100L, 500L, 2_000L)
        val DEVICE_ID_REGEX = Regex("^[A-Za-z0-9._:-]{1,128}$")
        const val DISCOVERY_PORT = 45_893
        const val MAX_DISPLAY_NAME_LENGTH = 100
        const val MAX_PACKET_SIZE_BYTES = 2_048
        const val RECEIVE_TIMEOUT_MS = 1_000
        const val HEARTBEAT_INTERVAL_MS = 5_000L
        const val PERIODIC_ANNOUNCEMENT_INTERVAL_MS = 30_000L
        const val OFFLINE_FAILURE_THRESHOLD = 3
        const val EVENT_BUFFER_SIZE = 64
    }
}

internal class PeerLivenessTracker(
    private val offlineFailureThreshold: Int,
) {
    private val confirmedPeers = mutableMapOf<String, PeerHealth>()

    init {
        require(offlineFailureThreshold > 0) { "offlineFailureThreshold must be positive" }
    }

    fun peers(): List<Peer> = confirmedPeers.values.map(PeerHealth::peer)

    fun clear() {
        confirmedPeers.clear()
    }

    fun record(peer: Peer, reachable: Boolean, timestamp: Long): DiscoveryEvent? {
        val current = confirmedPeers[peer.id]
        if (reachable) {
            val confirmed = peer.copy(
                availability = PeerAvailability.ONLINE,
                lastSeenAt = timestamp,
            )
            confirmedPeers[peer.id] = PeerHealth(confirmed, 0)
            return if (current == null || current.peer.endpointChanged(confirmed) || current.failedProbes > 0) {
                DiscoveryEvent.Found(confirmed)
            } else {
                null
            }
        }
        if (current == null) return null
        val failedProbes = current.failedProbes + 1
        if (failedProbes < offlineFailureThreshold) {
            confirmedPeers[peer.id] = current.copy(failedProbes = failedProbes)
            return null
        }
        confirmedPeers.remove(peer.id)
        return DiscoveryEvent.Lost(peer.id)
    }

    private fun Peer.endpointChanged(other: Peer): Boolean =
        host != other.host || port != other.port || displayName != other.displayName

    private data class PeerHealth(
        val peer: Peer,
        val failedProbes: Int,
    )
}

@Serializable
internal data class DiscoveryPacket(
    val protocolVersion: Int = 1,
    val deviceId: String,
    val displayName: String,
    val servicePort: Int,
    val replyRequested: Boolean,
)

internal object DiscoveryPacketCodec {
    private val json = Json { ignoreUnknownKeys = false }
    private val deviceIdRegex = Regex("^[A-Za-z0-9._:-]{1,128}$")

    fun encode(packet: DiscoveryPacket): ByteArray = json.encodeToString(packet).encodeToByteArray()

    fun decode(bytes: ByteArray, offset: Int, length: Int): DiscoveryPacket? {
        if (length !in 1..2_048 || offset < 0 || offset + length > bytes.size) return null
        val packet = runCatching {
            json.decodeFromString<DiscoveryPacket>(bytes.decodeToString(offset, offset + length))
        }.getOrNull() ?: return null
        return packet.takeIf {
            it.protocolVersion == 1 &&
                deviceIdRegex.matches(it.deviceId) &&
                it.displayName.isNotBlank() &&
                it.displayName.length <= 100 &&
                it.servicePort in 1..65_535
        }
    }
}
