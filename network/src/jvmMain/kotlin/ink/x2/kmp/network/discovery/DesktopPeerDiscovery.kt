package ink.x2.kmp.network.discovery

import ink.x2.kmp.domain.model.Peer
import ink.x2.kmp.domain.model.PeerAvailability
import ink.x2.kmp.domain.model.TrustState
import ink.x2.kmp.domain.port.DiscoveryEvent
import ink.x2.kmp.domain.port.PeerDiscovery
import ink.x2.kmp.domain.port.TimestampProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.jmdns.JmmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class DesktopPeerDiscovery(
    private val timestampProvider: TimestampProvider,
) : PeerDiscovery {
    private val mutableEvents = MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = EVENT_BUFFER_SIZE)
    private val jmmDns = JmmDNS.Factory.getInstance()
    private var localDeviceId: String? = null
    private var serviceInfo: ServiceInfo? = null
    private var started = false

    override val events: Flow<DiscoveryEvent> = mutableEvents.asSharedFlow()

    override suspend fun start(localDeviceId: String, displayName: String, servicePort: Int) {
        if (started) return
        require(localDeviceId.isNotBlank()) { "localDeviceId cannot be blank" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
        require(servicePort in 1..65_535) { "servicePort is invalid" }

        this.localDeviceId = localDeviceId
        jmmDns.addServiceListener(SERVICE_TYPE, listener)
        serviceInfo = ServiceInfo.create(
            SERVICE_TYPE,
            "LanChat-${localDeviceId.take(12)}",
            servicePort,
            0,
            0,
            mapOf(
                ATTRIBUTE_DEVICE_ID to localDeviceId,
                ATTRIBUTE_DISPLAY_NAME to displayName,
                ATTRIBUTE_PROTOCOL_VERSION to PROTOCOL_VERSION,
            ),
        ).also(jmmDns::registerService)
        started = true
    }

    override suspend fun stop() {
        if (!started) return
        serviceInfo?.let(jmmDns::unregisterService)
        jmmDns.removeServiceListener(SERVICE_TYPE, listener)
        serviceInfo = null
        localDeviceId = null
        started = false
    }

    private val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmmDns.requestServiceInfo(event.type, event.name, RESOLVE_TIMEOUT_MS)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            val peerId = event.info.getPropertyString(ATTRIBUTE_DEVICE_ID) ?: return
            if (peerId != localDeviceId) {
                mutableEvents.tryEmit(DiscoveryEvent.Lost(peerId))
            }
        }

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info
            val peerId = info.getPropertyString(ATTRIBUTE_DEVICE_ID) ?: return
            if (peerId == localDeviceId) return
            if (info.getPropertyString(ATTRIBUTE_PROTOCOL_VERSION) != PROTOCOL_VERSION) return
            val host = info.inet4Addresses.firstOrNull()?.hostAddress
                ?: info.inetAddresses.firstOrNull()?.hostAddress
                ?: return
            val displayName = info.getPropertyString(ATTRIBUTE_DISPLAY_NAME)?.takeIf(String::isNotBlank)
                ?: event.name
            mutableEvents.tryEmit(
                DiscoveryEvent.Found(
                    Peer(
                        id = peerId,
                        displayName = displayName,
                        host = host,
                        port = info.port,
                        availability = PeerAvailability.ONLINE,
                        trustState = TrustState.UNPAIRED,
                        lastSeenAt = timestampProvider.nowMillis(),
                    ),
                ),
            )
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_lanchat._tcp.local."
        const val ATTRIBUTE_DEVICE_ID = "id"
        const val ATTRIBUTE_DISPLAY_NAME = "name"
        const val ATTRIBUTE_PROTOCOL_VERSION = "v"
        const val PROTOCOL_VERSION = "1"
        const val RESOLVE_TIMEOUT_MS = 3_000L
        const val EVENT_BUFFER_SIZE = 64
    }
}
