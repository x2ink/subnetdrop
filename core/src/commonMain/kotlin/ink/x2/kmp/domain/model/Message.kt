package ink.x2.kmp.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val createdAt: Long,
    val direction: MessageDirection,
    val status: DeliveryStatus,
    val isRead: Boolean = false,
)

enum class MessageDirection {
    INCOMING,
    OUTGOING,
}

enum class DeliveryStatus {
    PENDING,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED,
}
