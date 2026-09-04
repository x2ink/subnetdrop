package ink.x2.subnetdrop.network.crypto

import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    @Test
    fun concurrentIdentityRequestsReuseOneGeneratedKeyPair() {
        runBlocking {
            val store = InMemorySecureStore()
            val codec = TinkSecureMessageCodec(store)

            val identities = coroutineScope {
                List(8) {
                    async { codec.createPublicIdentity("device-id", "Device") }
                }.awaitAll()
            }

            val expected = identities.first()
            assertTrue(identities.all { it.encryptionPublicKey.contentEquals(expected.encryptionPublicKey) })
            assertTrue(identities.all { it.signingPublicKey.contentEquals(expected.signingPublicKey) })
            assertEquals(2, store.writeCount.get())
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
        private val lock = Any()
        val writeCount = AtomicInteger()

        override suspend fun read(key: String): ByteArray? = synchronized(lock) {
            values[key]?.copyOf()
        }

        override suspend fun write(key: String, value: ByteArray) {
            synchronized(lock) {
                values[key] = value.copyOf()
                writeCount.incrementAndGet()
            }
        }
    }
}
