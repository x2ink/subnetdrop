package ink.x2.subnetdrop.network.discovery

import android.content.Context
import android.net.wifi.WifiManager
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.port.DiscoveryEvent
import ink.x2.subnetdrop.domain.port.PeerDiscovery
import ink.x2.subnetdrop.domain.port.PeerReachabilityProbe
import ink.x2.subnetdrop.domain.port.TimestampProvider
import kotlinx.coroutines.flow.Flow

class AndroidPeerDiscovery(
    context: Context,
    timestampProvider: TimestampProvider,
    reachabilityProbe: PeerReachabilityProbe,
) : PeerDiscovery {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private var multicastLock: WifiManager.MulticastLock? = null
    private val delegate = UdpPeerDiscovery(
        timestampProvider = timestampProvider,
        reachabilityProbe = reachabilityProbe,
        acquireMulticast = ::acquireMulticastLock,
        releaseMulticast = ::releaseMulticastLock,
    )

    override val events: Flow<DiscoveryEvent> = delegate.events

    override suspend fun start(
        localDeviceId: String,
        displayName: String,
        servicePort: Int,
        knownPeers: List<Peer>,
    ) {
        delegate.start(localDeviceId, displayName, servicePort, knownPeers)
    }

    override suspend fun stop() {
        delegate.stop()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.takeIf(WifiManager.MulticastLock::isHeld)?.release()
        multicastLock = null
    }

    private companion object {
        const val MULTICAST_LOCK_TAG = "subnetdrop-discovery"
    }
}
