package ink.x2.subnetdrop.network.discovery

import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.port.DiscoveryEvent
import ink.x2.subnetdrop.domain.port.PeerDiscovery
import ink.x2.subnetdrop.domain.port.PeerReachabilityProbe
import ink.x2.subnetdrop.domain.port.TimestampProvider
import kotlinx.coroutines.flow.Flow

class DesktopPeerDiscovery(
    timestampProvider: TimestampProvider,
    reachabilityProbe: PeerReachabilityProbe,
) : PeerDiscovery {
    private val delegate = UdpPeerDiscovery(timestampProvider, reachabilityProbe)

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
}
