package ink.x2.kmp.network.protocol

import kotlinx.serialization.Serializable

@Serializable
data class SecureEnvelope(
    val protocolVersion: Int,
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val createdAt: Long,
    val ciphertext: String,
    val signature: String,
)

@Serializable
data class EncryptedMessageBody(
    val body: String,
)

@Serializable
data class SecurePayloadEnvelope(
    val ciphertext: String,
    val signature: String,
)

@Serializable
internal data class SignedEnvelopeHeader(
    val protocolVersion: Int,
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val createdAt: Long,
)
