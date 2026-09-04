package ink.x2.kmp.network.crypto

import ink.x2.kmp.domain.model.DeliveryStatus
import ink.x2.kmp.domain.model.Message
import ink.x2.kmp.domain.model.MessageDirection
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class TinkSecureMessageCodecTest {
    @Test
    fun encryptAndDecryptBetweenTwoIdentities() {
        runBlocking {
            val alice = TinkSecureMessageCodec(InMemorySecureStore())
            val bob = TinkSecureMessageCodec(InMemorySecureStore())
            val aliceIdentity = alice.createPublicIdentity("alice-device", "Alice")
            val bobIdentity = bob.createPublicIdentity("bob-device", "Bob")
            val message = outgoingMessage()

            val envelope = alice.encrypt(message, bobIdentity)
            val decrypted = bob.decrypt(envelope, aliceIdentity, bobIdentity.deviceId)

            assertEquals(message.id, decrypted.id)
            assertEquals(message.body, decrypted.body)
            assertEquals(MessageDirection.INCOMING, decrypted.direction)
            assertEquals(DeliveryStatus.DELIVERED, decrypted.status)
        }
    }

    @Test
    fun tamperedEnvelopeIsRejected() {
        runBlocking {
            val alice = TinkSecureMessageCodec(InMemorySecureStore())
            val bob = TinkSecureMessageCodec(InMemorySecureStore())
            val aliceIdentity = alice.createPublicIdentity("alice-device", "Alice")
            val bobIdentity = bob.createPublicIdentity("bob-device", "Bob")
            val envelope = alice.encrypt(outgoingMessage(), bobIdentity)
            val tampered = envelope.replace("alice-device", "other-device")

            assertFails {
                bob.decrypt(tampered, aliceIdentity, bobIdentity.deviceId)
            }
        }
    }

    @Test
    fun safetyCodeIsSymmetricAndIdentityBound() {
        runBlocking {
            val alice = TinkSecureMessageCodec(InMemorySecureStore())
            val bob = TinkSecureMessageCodec(InMemorySecureStore())
            val charlie = TinkSecureMessageCodec(InMemorySecureStore())
            val aliceIdentity = alice.createPublicIdentity("alice-device", "Alice")
            val bobIdentity = bob.createPublicIdentity("bob-device", "Bob")
            val charlieIdentity = charlie.createPublicIdentity("charlie-device", "Charlie")

            val aliceBob = alice.calculateSafetyCode(aliceIdentity, bobIdentity)

            assertEquals(aliceBob, bob.calculateSafetyCode(bobIdentity, aliceIdentity))
            assertNotEquals(aliceBob, alice.calculateSafetyCode(aliceIdentity, charlieIdentity))
        }
    }

    @Test
    fun genericPayloadIsEncryptedAndBoundToAssociatedData() {
        runBlocking {
            val alice = TinkSecureMessageCodec(InMemorySecureStore())
            val bob = TinkSecureMessageCodec(InMemorySecureStore())
            val aliceIdentity = alice.createPublicIdentity("alice-device", "Alice")
            val bobIdentity = bob.createPublicIdentity("bob-device", "Bob")
            val payload = ByteArray(24_000) { index -> (index % 199).toByte() }
            val associatedData = "file|alice-device|bob-device|chunk-1".encodeToByteArray()

            val envelope = alice.encryptPayload(payload, associatedData, bobIdentity)
            val decrypted = bob.decryptPayload(envelope, associatedData, aliceIdentity)

            assertEquals(payload.toList(), decrypted.toList())
            assertFails {
                bob.decryptPayload(envelope, "file|tampered".encodeToByteArray(), aliceIdentity)
            }
        }
    }

    private fun outgoingMessage(): Message = Message(
        id = "message-1",
        conversationId = "alice-device:bob-device",
        senderId = "alice-device",
        recipientId = "bob-device",
        body = "hello encrypted world",
        createdAt = 1_000L,
        direction = MessageDirection.OUTGOING,
        status = DeliveryStatus.SENDING,
    )

    private class InMemorySecureStore : SecureKeyValueStore {
        private val values = mutableMapOf<String, ByteArray>()

        override suspend fun read(key: String): ByteArray? = values[key]?.copyOf()

        override suspend fun write(key: String, value: ByteArray) {
            values[key] = value.copyOf()
        }
    }
}
