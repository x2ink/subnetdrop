package ink.x2.subnetdrop.domain.port

import ink.x2.subnetdrop.domain.model.Peer

fun interface PeerReachabilityProbe {
    suspend fun isReachable(peer: Peer): Boolean
}
