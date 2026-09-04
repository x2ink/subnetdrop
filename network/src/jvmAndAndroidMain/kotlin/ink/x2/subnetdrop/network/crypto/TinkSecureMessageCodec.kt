package ink.x2.subnetdrop.network.crypto

import com.google.crypto.tink.Configuration
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.signature.PredefinedSignatureParameters
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.domain.model.PublicIdentity
import ink.x2.subnetdrop.domain.port.SecureMessageCodec
import ink.x2.subnetdrop.network.protocol.EncryptedMessageBody
import ink.x2.subnetdrop.network.protocol.SecureEnvelope
import ink.x2.subnetdrop.network.protocol.SecurePayloadEnvelope
import ink.x2.subnetdrop.network.protocol.SignedEnvelopeHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64

class TinkSecureMessageCodec(
    private val secureStore: SecureKeyValueStore,
) : SecureMessageCodec {
    private val initializationMutex = Mutex()
    private var keyMaterial: KeyMaterial? = null
    private val tinkConfiguration by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TinkConfig.register()
        RegistryConfiguration.get()
    }
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    override suspend fun createPublicIdentity(deviceId: String, displayName: String): PublicIdentity {
        validateIdentifier(deviceId, "deviceId")
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
        val keys = loadKeyMaterial()
        return PublicIdentity(
            deviceId = deviceId,
            displayName = displayName.trim(),
            encryptionPublicKey = keys.encryptionPrivate.getPublicKeysetHandle().encodePublicKeyset(),
            signingPublicKey = keys.signingPrivate.getPublicKeysetHandle().encodePublicKeyset(),
        )
    }

    override suspend fun encrypt(message: Message, recipient: PublicIdentity): String {
        validateMessageMetadata(message)
        validatePublicIdentity(recipient)
        require(message.recipientId == recipient.deviceId) { "Recipient identity does not match message" }

        val header = message.toHeader()
        val headerBytes = json.encodeToString(header).encodeToByteArray()
        val plaintext = json.encodeToString(EncryptedMessageBody(message.body)).encodeToByteArray()
        val recipientKeys = TinkProtoKeysetFormat.parseKeysetWithoutSecret(recipient.encryptionPublicKey)
        val ciphertext = recipientKeys
            .getPrimitive(tinkConfiguration, HybridEncrypt::class.java)
            .encrypt(plaintext, headerBytes)
        val signature = loadKeyMaterial().signingPrivate
            .getPrimitive(tinkConfiguration, PublicKeySign::class.java)
            .sign(headerBytes + ciphertext)

        return json.encodeToString(
            SecureEnvelope(
                protocolVersion = header.protocolVersion,
                messageId = header.messageId,
                conversationId = header.conversationId,
                senderId = header.senderId,
                recipientId = header.recipientId,
                createdAt = header.createdAt,
                ciphertext = Base64.getEncoder().encodeToString(ciphertext),
                signature = Base64.getEncoder().encodeToString(signature),
            ),
        )
    }

    override suspend fun decrypt(
        encodedEnvelope: String,
        expectedSender: PublicIdentity,
        localDeviceId: String,
    ): Message {
        require(encodedEnvelope.length <= MAX_ENVELOPE_LENGTH) { "Envelope is too large" }
        validatePublicIdentity(expectedSender)
        validateIdentifier(localDeviceId, "localDeviceId")

        val envelope = json.decodeFromString<SecureEnvelope>(encodedEnvelope)
        val header = envelope.toHeader()
        validateHeader(header)
        require(header.senderId == expectedSender.deviceId) { "Sender identity does not match envelope" }
        require(header.recipientId == localDeviceId) { "Envelope is addressed to another device" }

        val headerBytes = json.encodeToString(header).encodeToByteArray()
        val ciphertext = Base64.getDecoder().decode(envelope.ciphertext)
        val signature = Base64.getDecoder().decode(envelope.signature)
        val senderKeys = TinkProtoKeysetFormat.parseKeysetWithoutSecret(expectedSender.signingPublicKey)
        senderKeys
            .getPrimitive(tinkConfiguration, PublicKeyVerify::class.java)
            .verify(signature, headerBytes + ciphertext)

        val plaintext = loadKeyMaterial().encryptionPrivate
            .getPrimitive(tinkConfiguration, HybridDecrypt::class.java)
            .decrypt(ciphertext, headerBytes)
        val body = json.decodeFromString<EncryptedMessageBody>(plaintext.decodeToString())
        require(body.body.length <= MAX_MESSAGE_LENGTH) { "Message body is too long" }

        return Message(
            id = header.messageId,
            conversationId = header.conversationId,
            senderId = header.senderId,
            recipientId = header.recipientId,
            body = body.body,
            createdAt = header.createdAt,
            direction = MessageDirection.INCOMING,
            status = DeliveryStatus.DELIVERED,
        )
    }

    override suspend fun encryptPayload(
        plaintext: ByteArray,
        associatedData: ByteArray,
        recipient: PublicIdentity,
    ): String {
        require(plaintext.size <= MAX_PAYLOAD_LENGTH) { "Payload is too large" }
        require(associatedData.size <= MAX_ASSOCIATED_DATA_LENGTH) { "Associated data is too large" }
        validatePublicIdentity(recipient)
        val recipientKeys = TinkProtoKeysetFormat.parseKeysetWithoutSecret(recipient.encryptionPublicKey)
        val ciphertext = recipientKeys
            .getPrimitive(tinkConfiguration, HybridEncrypt::class.java)
            .encrypt(plaintext, associatedData)
        val signature = loadKeyMaterial().signingPrivate
            .getPrimitive(tinkConfiguration, PublicKeySign::class.java)
            .sign(associatedData + ciphertext)
        return json.encodeToString(
            SecurePayloadEnvelope(
                ciphertext = Base64.getEncoder().encodeToString(ciphertext),
                signature = Base64.getEncoder().encodeToString(signature),
            ),
        )
    }

    override suspend fun decryptPayload(
        encodedEnvelope: String,
        associatedData: ByteArray,
        expectedSender: PublicIdentity,
    ): ByteArray {
        require(encodedEnvelope.length <= MAX_ENVELOPE_LENGTH) { "Envelope is too large" }
        require(associatedData.size <= MAX_ASSOCIATED_DATA_LENGTH) { "Associated data is too large" }
        validatePublicIdentity(expectedSender)
        val envelope = json.decodeFromString<SecurePayloadEnvelope>(encodedEnvelope)
        val ciphertext = Base64.getDecoder().decode(envelope.ciphertext)
        val signature = Base64.getDecoder().decode(envelope.signature)
        val senderKeys = TinkProtoKeysetFormat.parseKeysetWithoutSecret(expectedSender.signingPublicKey)
        senderKeys
            .getPrimitive(tinkConfiguration, PublicKeyVerify::class.java)
            .verify(signature, associatedData + ciphertext)
        return loadKeyMaterial().encryptionPrivate
            .getPrimitive(tinkConfiguration, HybridDecrypt::class.java)
            .decrypt(ciphertext, associatedData)
            .also { require(it.size <= MAX_PAYLOAD_LENGTH) { "Decrypted payload is too large" } }
    }

    override fun calculateSafetyCode(first: PublicIdentity, second: PublicIdentity): String {
        validatePublicIdentity(first)
        validatePublicIdentity(second)
        val ordered = listOf(first, second).sortedBy(PublicIdentity::deviceId)
        val digest = MessageDigest.getInstance("SHA-256")
        ordered.forEach { identity ->
            digest.update(identity.deviceId.encodeToByteArray())
            digest.update(0)
            digest.update(identity.encryptionPublicKey)
            digest.update(0)
            digest.update(identity.signingPublicKey)
        }
        val hash = digest.digest()
        val number = ((hash[0].toInt() and 0xff) shl 24) or
            ((hash[1].toInt() and 0xff) shl 16) or
            ((hash[2].toInt() and 0xff) shl 8) or
            (hash[3].toInt() and 0xff)
        return (number.toLong() and 0xffffffffL).rem(1_000_000L).toString().padStart(6, '0')
    }

    override suspend fun sign(data: ByteArray): ByteArray {
        require(data.size <= MAX_SIGNED_DATA_LENGTH) { "Signed data is too large" }
        return loadKeyMaterial().signingPrivate
            .getPrimitive(tinkConfiguration, PublicKeySign::class.java)
            .sign(data)
    }

    override fun verify(data: ByteArray, signature: ByteArray, sender: PublicIdentity) {
        require(data.size <= MAX_SIGNED_DATA_LENGTH) { "Signed data is too large" }
        validatePublicIdentity(sender)
        TinkProtoKeysetFormat.parseKeysetWithoutSecret(sender.signingPublicKey)
            .getPrimitive(tinkConfiguration, PublicKeyVerify::class.java)
            .verify(signature, data)
    }

    private suspend fun loadKeyMaterial(): KeyMaterial = keyMaterial ?: initializationMutex.withLock {
        keyMaterial ?: createKeyMaterial().also { keyMaterial = it }
    }

    private suspend fun createKeyMaterial(): KeyMaterial {
        val configuration = tinkConfiguration
        return coroutineScope {
            val encryptionPrivate = async(Dispatchers.Default) {
                loadOrCreateKeyset(ENCRYPTION_KEY, configuration) {
                    KeysetHandle.generateNew(
                        HpkeParameters.builder()
                            .setKemId(HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256)
                            .setKdfId(HpkeParameters.KdfId.HKDF_SHA256)
                            .setAeadId(HpkeParameters.AeadId.AES_256_GCM)
                            .setVariant(HpkeParameters.Variant.TINK)
                            .build(),
                    )
                }
            }
            val signingPrivate = async(Dispatchers.Default) {
                loadOrCreateKeyset(SIGNING_KEY, configuration) {
                    KeysetHandle.generateNew(PredefinedSignatureParameters.ED25519)
                }
            }
            KeyMaterial(
                encryptionPrivate = encryptionPrivate.await(),
                signingPrivate = signingPrivate.await(),
            )
        }
    }

    private suspend fun loadOrCreateKeyset(
        key: String,
        configuration: Configuration,
        generator: () -> KeysetHandle,
    ): KeysetHandle {
        val stored = secureStore.read(key)
        if (stored != null) {
            return TinkProtoKeysetFormat.parseKeyset(
                stored,
                InsecureSecretKeyAccess.get(),
                configuration,
            )
        }

        val generated = generator()
        val serialized = TinkProtoKeysetFormat.serializeKeyset(
            generated,
            InsecureSecretKeyAccess.get(),
            configuration,
        )
        secureStore.write(key, serialized)
        return generated
    }

    private fun KeysetHandle.encodePublicKeyset(): ByteArray =
        TinkProtoKeysetFormat.serializeKeysetWithoutSecret(this)

    private fun Message.toHeader(): SignedEnvelopeHeader = SignedEnvelopeHeader(
        protocolVersion = PROTOCOL_VERSION,
        messageId = id,
        conversationId = conversationId,
        senderId = senderId,
        recipientId = recipientId,
        createdAt = createdAt,
    )

    private fun SecureEnvelope.toHeader(): SignedEnvelopeHeader = SignedEnvelopeHeader(
        protocolVersion = protocolVersion,
        messageId = messageId,
        conversationId = conversationId,
        senderId = senderId,
        recipientId = recipientId,
        createdAt = createdAt,
    )

    private fun validateMessageMetadata(message: Message) {
        validateHeader(message.toHeader())
        require(message.body.length <= MAX_MESSAGE_LENGTH) { "Message body is too long" }
    }

    private fun validateHeader(header: SignedEnvelopeHeader) {
        require(header.protocolVersion == PROTOCOL_VERSION) { "Unsupported protocol version" }
        validateIdentifier(header.messageId, "messageId")
        validateIdentifier(header.conversationId, "conversationId")
        validateIdentifier(header.senderId, "senderId")
        validateIdentifier(header.recipientId, "recipientId")
        require(header.createdAt >= 0) { "createdAt cannot be negative" }
    }

    private fun validatePublicIdentity(identity: PublicIdentity) {
        validateIdentifier(identity.deviceId, "deviceId")
        require(identity.encryptionPublicKey.isNotEmpty()) { "Encryption public key is empty" }
        require(identity.signingPublicKey.isNotEmpty()) { "Signing public key is empty" }
    }

    private fun validateIdentifier(value: String, field: String) {
        require(IDENTIFIER_REGEX.matches(value)) { "$field has an invalid format" }
    }

    private data class KeyMaterial(
        val encryptionPrivate: KeysetHandle,
        val signingPrivate: KeysetHandle,
    )

    private companion object {
        const val PROTOCOL_VERSION = 1
        const val MAX_MESSAGE_LENGTH = 8_192
        const val MAX_ENVELOPE_LENGTH = 64 * 1_024
        const val MAX_PAYLOAD_LENGTH = 40 * 1_024
        const val MAX_ASSOCIATED_DATA_LENGTH = 4 * 1_024
        const val MAX_SIGNED_DATA_LENGTH = 64 * 1_024
        const val ENCRYPTION_KEY = "identity.hpke.private"
        const val SIGNING_KEY = "identity.ed25519.private"
        val IDENTIFIER_REGEX = Regex("^[A-Za-z0-9._:-]{1,128}$")
    }
}
