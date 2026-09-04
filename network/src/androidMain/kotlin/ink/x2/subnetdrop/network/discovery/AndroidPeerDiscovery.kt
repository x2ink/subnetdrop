package ink.x2.subnetdrop.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.domain.port.DiscoveryEvent
import ink.x2.subnetdrop.domain.port.PeerDiscovery
import ink.x2.subnetdrop.domain.port.TimestampProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Suppress("DEPRECATION")
class AndroidPeerDiscovery(
    context: Context,
    private val timestampProvider: TimestampProvider,
) : PeerDiscovery {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val mutableEvents = MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = EVENT_BUFFER_SIZE)
    private val resolutionLock = Any()
    private val resolvingServiceKeys = mutableSetOf<String>()
    private val peerIdsByServiceKey = mutableMapOf<String, String>()
    private var localDeviceId: String? = null
    private var registeredService: NsdServiceInfo? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var started = false

    override val events: Flow<DiscoveryEvent> = mutableEvents.asSharedFlow()

    override suspend fun start(localDeviceId: String, displayName: String, servicePort: Int) {
        if (started) return
        require(localDeviceId.isNotBlank()) { "localDeviceId cannot be blank" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
        require(servicePort in 1..65_535) { "servicePort is invalid" }

        this.localDeviceId = localDeviceId
        multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
        registeredService = NsdServiceInfo().apply {
            serviceName = "SubnetDrop-${localDeviceId.take(12)}"
            serviceType = SERVICE_TYPE
            port = servicePort
            setAttribute(ATTRIBUTE_DEVICE_ID, localDeviceId)
            setAttribute(ATTRIBUTE_DISPLAY_NAME, displayName)
            setAttribute(ATTRIBUTE_PROTOCOL_VERSION, PROTOCOL_VERSION)
        }
        nsdManager.registerService(
            registeredService,
            NsdManager.PROTOCOL_DNS_SD,
            registrationListener,
        )
        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )
        started = true
    }

    override suspend fun stop() {
        if (!started) return
        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        runCatching { nsdManager.unregisterService(registrationListener) }
        multicastLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        registeredService = null
        multicastLock = null
        localDeviceId = null
        synchronized(resolutionLock) {
            resolvingServiceKeys.clear()
            peerIdsByServiceKey.clear()
        }
        started = false
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            registeredService = serviceInfo
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            mutableEvents.tryEmit(DiscoveryEvent.Failure("Service registration failed: $errorCode"))
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            mutableEvents.tryEmit(DiscoveryEvent.Failure("Service unregistration failed: $errorCode"))
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            resolveService(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val serviceKey = serviceInfo.serviceKey()
            val peerId = synchronized(resolutionLock) {
                peerIdsByServiceKey.remove(serviceKey)
            } ?: serviceInfo.attributes[ATTRIBUTE_DEVICE_ID]?.decodeToString() ?: return
            if (peerId != localDeviceId) {
                mutableEvents.tryEmit(DiscoveryEvent.Lost(peerId))
            }
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            mutableEvents.tryEmit(DiscoveryEvent.Failure("Discovery start failed: $errorCode"))
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            mutableEvents.tryEmit(DiscoveryEvent.Failure("Discovery stop failed: $errorCode"))
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val serviceKey = serviceInfo.serviceKey()
        val shouldResolve = synchronized(resolutionLock) {
            resolvingServiceKeys.add(serviceKey)
        }
        if (!shouldResolve) return
        nsdManager.resolveService(serviceInfo, createResolveListener(serviceKey))
    }

    private fun createResolveListener(serviceKey: String) = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            finishResolution(serviceKey)
            mutableEvents.tryEmit(DiscoveryEvent.Failure("Service resolve failed: $errorCode"))
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            finishResolution(serviceKey)
            publishResolvedService(serviceKey, serviceInfo)
        }
    }

    private fun publishResolvedService(serviceKey: String, serviceInfo: NsdServiceInfo) {
        val peerId = serviceInfo.attributes[ATTRIBUTE_DEVICE_ID]?.decodeToString() ?: return
        synchronized(resolutionLock) { peerIdsByServiceKey[serviceKey] = peerId }
        if (peerId == localDeviceId) return
        val version = serviceInfo.attributes[ATTRIBUTE_PROTOCOL_VERSION]?.decodeToString()
        if (version != PROTOCOL_VERSION) return
        val host = serviceInfo.host?.hostAddress ?: return
        val displayName = serviceInfo.attributes[ATTRIBUTE_DISPLAY_NAME]
            ?.decodeToString()
            ?.takeIf(String::isNotBlank)
            ?: serviceInfo.serviceName
        mutableEvents.tryEmit(DiscoveryEvent.Found(serviceInfo.toPeer(peerId, displayName, host)))
    }

    private fun NsdServiceInfo.toPeer(peerId: String, displayName: String, host: String) = Peer(
        id = peerId,
        displayName = displayName,
        host = host,
        port = port,
        availability = PeerAvailability.ONLINE,
        trustState = TrustState.UNPAIRED,
        lastSeenAt = timestampProvider.nowMillis(),
    )

    private fun finishResolution(serviceKey: String) {
        synchronized(resolutionLock) { resolvingServiceKeys.remove(serviceKey) }
    }

    private fun NsdServiceInfo.serviceKey(): String = "$serviceType|$serviceName"

    private companion object {
        const val SERVICE_TYPE = "_subnetdrop._tcp."
        const val ATTRIBUTE_DEVICE_ID = "id"
        const val ATTRIBUTE_DISPLAY_NAME = "name"
        const val ATTRIBUTE_PROTOCOL_VERSION = "v"
        const val PROTOCOL_VERSION = "1"
        const val MULTICAST_LOCK_TAG = "subnetdrop-discovery"
        const val EVENT_BUFFER_SIZE = 64
    }
}
