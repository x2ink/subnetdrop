package ink.x2.subnetdrop.domain.port

import ink.x2.subnetdrop.domain.model.Peer
import kotlinx.coroutines.flow.Flow

interface PeerRepository {
    fun observePeers(): Flow<List<Peer>>

    suspend fun upsertPeer(peer: Peer)

    suspend fun findPeer(peerId: String): Peer?

    suspend fun markAllOffline()
}
