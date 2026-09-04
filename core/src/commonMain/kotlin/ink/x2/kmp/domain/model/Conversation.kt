package ink.x2.kmp.domain.model

data class Conversation(
    val id: String,
    val peerId: String,
    val peerDisplayName: String,
    val lastMessage: String?,
    val updatedAt: Long,
    val unreadCount: Long,
)
