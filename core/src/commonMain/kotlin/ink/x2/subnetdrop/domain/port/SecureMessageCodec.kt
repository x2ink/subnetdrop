package ink.x2.subnetdrop.domain.port

import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.PublicIdentity

interface SecureMessageCodec {
    suspend fun createPublicIdentity(deviceId: String, displayName: String): PublicIdentity

    suspend fun encrypt(message: Message, recipient: PublicIdentity): String

    suspend fun decrypt(
        encodedEnvelope: String,
        expectedSender: PublicIdentity,
        localDeviceId: String,
    ): Message

    suspend fun encryptPayload(
        plaintext: ByteArray,
        associatedData: ByteArray,
        recipient: PublicIdentity,
    ): String

    suspend fun decryptPayload(
        encodedEnvelope: String,
        associatedData: ByteArray,
        expectedSender: PublicIdentity,
    ): ByteArray

    suspend fun sign(data: ByteArray): ByteArray

    fun verify(data: ByteArray, signature: ByteArray, sender: PublicIdentity)

    fun calculateSafetyCode(first: PublicIdentity, second: PublicIdentity): String
}
