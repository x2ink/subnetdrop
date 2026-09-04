package ink.x2.subnetdrop.domain.port

import ink.x2.subnetdrop.domain.model.Peer
import kotlinx.coroutines.flow.Flow

interface PeerDiscovery {
    val events: Flow<DiscoveryEvent>

    suspend fun start(
        localDeviceId: String,
        displayName: String,
        servicePort: Int,
        knownPeers: List<Peer>,
    )

    suspend fun stop()
}

sealed interface DiscoveryEvent {
    data class Found(val peer: Peer) : DiscoveryEvent

    data class Lost(val peerId: String) : DiscoveryEvent

    data class Failure(val reason: String) : DiscoveryEvent
}
