package ink.x2.kmp.network.transport

import ink.x2.kmp.domain.model.Conversation
import ink.x2.kmp.domain.model.DeliveryStatus
import ink.x2.kmp.domain.model.DeviceProfile
import ink.x2.kmp.domain.model.Message
import ink.x2.kmp.domain.model.MessageDirection
import ink.x2.kmp.domain.model.FileTransferStatus
import ink.x2.kmp.domain.model.LocalFile
import ink.x2.kmp.domain.model.Peer
import ink.x2.kmp.domain.model.PeerAvailability
import ink.x2.kmp.domain.model.PublicIdentity
import ink.x2.kmp.domain.model.TrustState
import ink.x2.kmp.domain.model.conversationIdFor
import ink.x2.kmp.domain.port.ChatRepository
import ink.x2.kmp.domain.port.DeviceProfileRepository
import ink.x2.kmp.domain.port.IdGenerator
import ink.x2.kmp.domain.port.PeerRepository
import ink.x2.kmp.domain.port.TrustedIdentityRepository
import ink.x2.kmp.network.crypto.SecureKeyValueStore
import ink.x2.kmp.network.crypto.TinkSecureMessageCodec
import ink.x2.kmp.network.identity.LocalIdentityService
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LanChatTransportTest {
    @Test
    fun pairsAndDeliversEncryptedMessageWithDeduplication() {
        runBlocking {
            val alice = TestNode("alice", availablePort())
            val bob = TestNode("bob", availablePort())
            alice.discover(bob)
            bob.discover(alice)
            alice.transport.start()
            bob.transport.start()

            try {
                alice.transport.requestPairing(bob.id)
                val aliceCandidate = alice.transport.candidates.value.single()
                val bobCandidate = bob.transport.candidates.value.single()
                assertEquals(aliceCandidate.safetyCode, bobCandidate.safetyCode)
                alice.transport.confirmPairing(bob.id)
                bob.transport.confirmPairing(alice.id)

                val message = alice.messageTo(bob, "hello over Wi-Fi")
                alice.chatRepository.saveMessage(message)
                alice.transport.send(bob.id, message)
                alice.transport.send(bob.id, message)

                val received = bob.chatRepository.messages.value
                assertEquals(1, received.size)
                assertEquals("hello over Wi-Fi", received.single().body)
                assertEquals(MessageDirection.INCOMING, received.single().direction)
                bob.transport.sendReadReceipt(alice.id, listOf(message.id))
                assertEquals(DeliveryStatus.READ, alice.chatRepository.messages.value.single().status)
                assertNotNull(alice.trustedIdentities.find(bob.id))
                assertNotNull(bob.trustedIdentities.find(alice.id))
            } finally {
                alice.transport.stop()
                bob.transport.stop()
            }
        }
    }

    @Test
    fun offersAcceptsAndTransfersEncryptedFileInChunks() {
        runBlocking {
            val alice = TestNode("alice-file", availablePort())
            val bob = TestNode("bob-file", availablePort())
            alice.discover(bob)
            bob.discover(alice)
            alice.transport.start()
            bob.transport.start()
            try {
                alice.pairWith(bob)
                val sourceBytes = ByteArray(70_000) { index -> (index % 251).toByte() }
                val source = File(alice.workingDirectory, "transfer sample.bin").apply {
                    writeBytes(sourceBytes)
                }
                val sending = async {
                    alice.transport.sendFile(
                        bob.id,
                        LocalFile(source.name, source.path, source.length(), "application/octet-stream"),
                    )
                }
                val offer = withTimeout(5_000L) {
                    bob.transport.incomingOffers.first { it.isNotEmpty() }.single()
                }
                bob.transport.acceptOffer(offer.transferId)
                sending.await()

                val outgoing = alice.transport.transfers.value.single()
                val incoming = bob.transport.transfers.value.single()
                assertEquals(FileTransferStatus.COMPLETED, outgoing.status)
                assertEquals(FileTransferStatus.COMPLETED, incoming.status)
                assertContentEquals(sourceBytes, File(requireNotNull(incoming.localPath)).readBytes())
            } finally {
                alice.transport.stop()
                bob.transport.stop()
            }
        }
    }

    @Test
    fun rejectedOfferDoesNotTransferFileBytes() {
        runBlocking {
            val alice = TestNode("alice-reject", availablePort())
            val bob = TestNode("bob-reject", availablePort())
            alice.discover(bob)
            bob.discover(alice)
            alice.transport.start()
            bob.transport.start()
            try {
                alice.pairWith(bob)
                val source = File(alice.workingDirectory, "private.txt").apply { writeText("not accepted") }
                val sending = async {
                    alice.transport.sendFile(bob.id, LocalFile(source.name, source.path, source.length()))
                }
                val offer = withTimeout(5_000L) {
                    bob.transport.incomingOffers.first { it.isNotEmpty() }.single()
                }
                bob.transport.rejectOffer(offer.transferId)
                sending.await()

                assertEquals(FileTransferStatus.REJECTED, alice.transport.transfers.value.single().status)
                assertEquals(FileTransferStatus.REJECTED, bob.transport.transfers.value.single().status)
                assertEquals(false, File(bob.workingDirectory, "received").exists())
            } finally {
                alice.transport.stop()
                bob.transport.stop()
            }
        }
    }
}

private class TestNode(
    val id: String,
    val port: Int,
) {
    val workingDirectory: File = Files.createTempDirectory("lan-chat-$id-").toFile()
    private var transferSequence = 0
    private val profileRepository = TestDeviceProfileRepository(id)
    private val codec = TinkSecureMessageCodec(MemorySecureKeyValueStore())
    private val identityService = LocalIdentityService(
        deviceProfileRepository = profileRepository,
        secureMessageCodec = codec,
        idGenerator = IdGenerator { id },
        defaultDisplayName = id.replaceFirstChar(Char::uppercase),
    )
    private val peers = TestPeerRepository()
    val chatRepository = TestChatRepository()
    val trustedIdentities = TestTrustedIdentityRepository(peers)
    val transport = LanChatTransport(
        localIdentityService = identityService,
        peerRepository = peers,
        trustedIdentityRepository = trustedIdentities,
        chatRepository = chatRepository,
        secureMessageCodec = codec,
        timestampProvider = { 1_000L },
        idGenerator = IdGenerator { "$id-transfer-${transferSequence++}" },
        receivedFilesDirectory = File(workingDirectory, "received"),
        listenerPort = port,
    )

    suspend fun discover(other: TestNode) {
        peers.upsertPeer(
            Peer(
                id = other.id,
                displayName = other.id,
                host = "127.0.0.1",
                port = other.port,
                availability = PeerAvailability.ONLINE,
                trustState = TrustState.UNPAIRED,
                lastSeenAt = 1L,
            ),
        )
    }

    fun messageTo(other: TestNode, body: String) = Message(
        id = "message-1",
        conversationId = conversationIdFor(id, other.id),
        senderId = id,
        recipientId = other.id,
        body = body,
        createdAt = 2_000L,
        direction = MessageDirection.OUTGOING,
        status = DeliveryStatus.SENDING,
    )

    suspend fun pairWith(other: TestNode) {
        transport.requestPairing(other.id)
        other.transport.requestPairing(id)
        transport.confirmPairing(other.id)
        other.transport.confirmPairing(id)
    }
}

private class MemorySecureKeyValueStore : SecureKeyValueStore {
    private val values = mutableMapOf<String, ByteArray>()

    override suspend fun read(key: String): ByteArray? = values[key]?.copyOf()

    override suspend fun write(key: String, value: ByteArray) {
        values[key] = value.copyOf()
    }
}

private class TestDeviceProfileRepository(
    private val deviceId: String,
) : DeviceProfileRepository {
    override suspend fun getOrCreate(defaultDeviceId: String, defaultDisplayName: String) =
        DeviceProfile(deviceId, defaultDisplayName)

    override suspend fun updateDisplayName(displayName: String) = Unit
}

private class TestPeerRepository : PeerRepository {
    private val peers = MutableStateFlow<List<Peer>>(emptyList())

    override fun observePeers(): Flow<List<Peer>> = peers

    override suspend fun upsertPeer(peer: Peer) {
        peers.value = peers.value.filterNot { it.id == peer.id } + peer
    }

    override suspend fun findPeer(peerId: String): Peer? = peers.value.firstOrNull { it.id == peerId }

    override suspend fun markAllOffline() {
        peers.value = peers.value.map { it.copy(availability = PeerAvailability.OFFLINE) }
    }
}

private class TestTrustedIdentityRepository(
    private val peers: PeerRepository,
) : TrustedIdentityRepository {
    private val values = mutableMapOf<String, PublicIdentity>()

    override suspend fun find(peerId: String): PublicIdentity? = values[peerId]

    override suspend fun save(identity: PublicIdentity, verifiedAt: Long) {
        values[identity.deviceId] = identity
        val peer = peers.findPeer(identity.deviceId) ?: return
        peers.upsertPeer(peer.copy(trustState = TrustState.TRUSTED))
    }
}

private class TestChatRepository : ChatRepository {
    val messages = MutableStateFlow<List<Message>>(emptyList())

    override fun observeConversations(): Flow<List<Conversation>> = MutableStateFlow(emptyList())

    override fun observeMessages(conversationId: String): Flow<List<Message>> = messages

    override suspend fun saveMessage(message: Message) {
        if (messages.value.none { it.id == message.id }) messages.value += message
    }

    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) {
        messages.value = messages.value.map { message ->
            if (message.id == messageId) message.copy(status = status) else message
        }
    }

    override suspend fun unreadIncomingMessageIds(conversationId: String): List<String> = messages.value
        .filter { it.conversationId == conversationId && it.direction == MessageDirection.INCOMING && !it.isRead }
        .map(Message::id)

    override suspend fun markConversationRead(conversationId: String) {
        messages.value = messages.value.map { message ->
            if (message.conversationId == conversationId && message.direction == MessageDirection.INCOMING) {
                message.copy(isRead = true)
            } else {
                message
            }
        }
    }

    override suspend fun markOutgoingMessagesRead(peerId: String, messageIds: List<String>) {
        messages.value = messages.value.map { message ->
            if (message.recipientId == peerId && message.id in messageIds) {
                message.copy(status = DeliveryStatus.READ)
            } else {
                message
            }
        }
    }

    override suspend fun containsMessage(messageId: String): Boolean = messages.value.any { it.id == messageId }
}

private fun availablePort(): Int = ServerSocket(0).use(ServerSocket::getLocalPort)
