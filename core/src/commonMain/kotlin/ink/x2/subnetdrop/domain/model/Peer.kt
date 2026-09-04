package ink.x2.subnetdrop.domain.model

data class Peer(
    val id: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val availability: PeerAvailability,
    val trustState: TrustState,
    val lastSeenAt: Long,
)

enum class PeerAvailability {
    ONLINE,
    OFFLINE,
}

enum class TrustState {
    UNPAIRED,
    PENDING,
    TRUSTED,
    KEY_CHANGED,
}
