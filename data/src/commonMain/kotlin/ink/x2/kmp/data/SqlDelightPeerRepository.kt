package ink.x2.kmp.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import ink.x2.kmp.data.db.ChatDatabase
import ink.x2.kmp.domain.model.Peer
import ink.x2.kmp.domain.model.PeerAvailability
import ink.x2.kmp.domain.model.TrustState
import ink.x2.kmp.domain.port.PeerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class SqlDelightPeerRepository(
    database: ChatDatabase,
) : PeerRepository {
    private val queries = database.chatQueries

    override fun observePeers(): Flow<List<Peer>> = queries
        .selectAllPeers(::mapPeer)
        .asFlow()
        .mapToList(Dispatchers.Default)

    override suspend fun upsertPeer(peer: Peer) {
        queries.transaction {
            queries.insertPeer(
                id = peer.id,
                display_name = peer.displayName,
                host = peer.host,
                port = peer.port.toLong(),
                availability = peer.availability.name,
                trust_state = peer.trustState.name,
                last_seen_at = peer.lastSeenAt,
            )
            queries.updatePeer(
                display_name = peer.displayName,
                host = peer.host,
                port = peer.port.toLong(),
                availability = peer.availability.name,
                trust_state = peer.trustState.name,
                last_seen_at = peer.lastSeenAt,
                id = peer.id,
            )
        }
    }

    override suspend fun findPeer(peerId: String): Peer? = queries
        .findPeerById(peerId, ::mapPeer)
        .executeAsOneOrNull()

    override suspend fun markAllOffline() {
        queries.markAllPeersOffline()
    }

    private fun mapPeer(
        id: String,
        displayName: String,
        host: String,
        port: Long,
        availability: String,
        trustState: String,
        lastSeenAt: Long,
    ): Peer = Peer(
        id = id,
        displayName = displayName,
        host = host,
        port = port.toInt(),
        availability = PeerAvailability.valueOf(availability),
        trustState = TrustState.valueOf(trustState),
        lastSeenAt = lastSeenAt,
    )
}
