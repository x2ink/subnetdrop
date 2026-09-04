package ink.x2.kmp.network.protocol

import kotlinx.serialization.Serializable

@Serializable
data class TransportFrame(
    val protocolVersion: Int = 1,
    val type: FrameType,
    val senderId: String,
    val recipientId: String,
    val payload: String,
    val signature: String? = null,
)

@Serializable
enum class FrameType {
    PAIR_REQUEST,
    PAIR_RESPONSE,
    CHAT_MESSAGE,
    DELIVERY_ACK,
    READ_RECEIPT,
    FILE_OFFER,
    FILE_DECISION,
    FILE_CHUNK,
    FILE_CANCEL,
    ERROR,
    PING,
    PONG,
}

@Serializable
data class PublicIdentityPayload(
    val deviceId: String,
    val displayName: String,
    val encryptionPublicKey: String,
    val signingPublicKey: String,
)

@Serializable
data class DeliveryAckPayload(
    val messageId: String,
)

@Serializable
data class ReadReceiptPayload(
    val messageIds: List<String>,
)

@Serializable
data class FileOfferPayload(
    val transferId: String,
    val fileName: String,
    val size: Long,
    val contentType: String?,
    val sha256: String,
)

@Serializable
data class FileDecisionPayload(
    val transferId: String,
    val accepted: Boolean,
)

@Serializable
data class FileChunkPayload(
    val transferId: String,
    val index: Long,
    val data: String,
    val isLast: Boolean,
)

@Serializable
data class FileCancelPayload(
    val transferId: String,
)

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String,
)
