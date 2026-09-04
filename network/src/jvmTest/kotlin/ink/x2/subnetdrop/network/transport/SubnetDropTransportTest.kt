package ink.x2.subnetdrop.network.transport

import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.DeviceProfile
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.domain.model.FileTransferSettings
import ink.x2.subnetdrop.domain.model.LocalFile
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.PublicIdentity
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.domain.model.conversationIdFor
import ink.x2.subnetdrop.domain.port.ChatRepository
import ink.x2.subnetdrop.domain.port.DeviceProfileRepository
import ink.x2.subnetdrop.domain.port.IdGenerator
import ink.x2.subnetdrop.domain.port.FileTransferSettingsRepository
import ink.x2.subnetdrop.domain.port.PeerRepository
import ink.x2.subnetdrop.domain.port.TrustedIdentityRepository
import ink.x2.subnetdrop.network.crypto.SecureKeyValueStore
import ink.x2.subnetdrop.network.crypto.TinkSecureMessageCodec
import ink.x2.subnetdrop.network.identity.LocalIdentityService
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubnetDropTransportTest {
    @Test
    fun probesReachabilityWithoutPreparingCryptographicIdentity() {
        runBlocking {
            val alice = TestNode("alice-probe", availablePort())
            val bob = TestNode("bob-probe", availablePort())
            bob.transport.start()
            try {
                assertTrue(alice.transport.isReachable(alice.peerFor(bob)))
                assertTrue(alice.secureStoreIsEmpty())
                assertTrue(bob.secureStoreIsEmpty())
                assertFalse(alice.transport.isReachable(alice.peerFor(bob).copy(port = availablePort())))
            } finally {
                bob.transport.stop()
            }
        }
    }

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
    fun automaticallyAcceptsAndTransfersFileThroughBinaryStreamByDefault() {
        runBlocking {
            val alice = TestNode("alice-file", availablePort())
            val bob = TestNode("bob-file", availablePort())
            alice.discover(bob)
            bob.discover(alice)
            alice.transport.start()
            bob.transport.start()
            try {
                alice.pairWith(bob)
                val customSaveDirectory = File(bob.workingDirectory, "chosen-downloads")
                bob.fileSettings.updateSaveDirectory(customSaveDirectory.path)
                val sourceBytes = ByteArray(1_300_000) { index -> (index % 251).toByte() }
                val source = File(alice.workingDirectory, "transfer sample.bin").apply {
                    writeBytes(sourceBytes)
                }
                alice.transport.sendFile(
                    bob.id,
                    LocalFile(source.name, source.path, source.length(), "application/octet-stream"),
                )

                val outgoing = alice.transport.transfers.value.single()
                val incoming = bob.transport.transfers.value.single()
                assertTrue(bob.transport.incomingOffers.value.isEmpty())
                assertEquals(FileTransferStatus.COMPLETED, outgoing.status)
                assertEquals(FileTransferStatus.COMPLETED, incoming.status)
                assertEquals(1_000L, outgoing.createdAt)
                assertEquals(1_000L, incoming.createdAt)
                assertEquals("application/octet-stream", incoming.contentType)
                assertEquals(File(customSaveDirectory, source.name).path, incoming.localPath)
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
                bob.fileSettings.updateRequireIncomingConfirmation(true)
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

    @Test
    fun transfersAfterExplicitAcceptanceWhenConfirmationIsEnabled() {
        runBlocking {
            val alice = TestNode("alice-confirm", availablePort())
            val bob = TestNode("bob-confirm", availablePort())
            alice.discover(bob)
            bob.discover(alice)
            alice.transport.start()
            bob.transport.start()
            try {
                alice.pairWith(bob)
                bob.fileSettings.updateRequireIncomingConfirmation(true)
                val source = File(alice.workingDirectory, "accepted.txt").apply { writeText("accepted") }
                val sending = async {
                    alice.transport.sendFile(bob.id, LocalFile(source.name, source.path, source.length()))
                }
                val offer = withTimeout(5_000L) {
                    bob.transport.incomingOffers.first { it.isNotEmpty() }.single()
                }

                assertEquals(FileTransferStatus.WAITING_FOR_ACCEPTANCE, bob.transport.transfers.value.single().status)
                bob.transport.acceptOffer(offer.transferId)
                sending.await()

                assertEquals(FileTransferStatus.COMPLETED, alice.transport.transfers.value.single().status)
                assertEquals(FileTransferStatus.COMPLETED, bob.transport.transfers.value.single().status)
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
    val workingDirectory: File = Files.createTempDirectory("subnetdrop-$id-").toFile()
    private var transferSequence = 0
    private val profileRepository = TestDeviceProfileRepository(id)
    private val secureStore = MemorySecureKeyValueStore()
    private val codec = TinkSecureMessageCodec(secureStore)
    private val identityService = LocalIdentityService(
        deviceProfileRepository = profileRepository,
        secureMessageCodec = codec,
        idGenerator = IdGenerator { id },
        defaultDisplayName = id.replaceFirstChar(Char::uppercase),
    )
    private val peers = TestPeerRepository()
    val chatRepository = TestChatRepository()
    val fileSettings = TestFileTransferSettingsRepository(File(workingDirectory, "received").path)
    val trustedIdentities = TestTrustedIdentityRepository(peers)
    val transport = SubnetDropTransport(
        localIdentityService = identityService,
        peerRepository = peers,
        trustedIdentityRepository = trustedIdentities,
        chatRepository = chatRepository,
        secureMessageCodec = codec,
        timestampProvider = { 1_000L },
        idGenerator = IdGenerator { "$id-transfer-${transferSequence++}" },
        fileTransferSettingsRepository = fileSettings,
        listenerPort = port,
    )

    suspend fun discover(other: TestNode) {
        peers.upsertPeer(peerFor(other).copy(availability = PeerAvailability.ONLINE))
    }

    fun peerFor(other: TestNode) = Peer(
        id = other.id,
        displayName = other.id,
        host = "127.0.0.1",
        port = other.port,
        availability = PeerAvailability.OFFLINE,
        trustState = TrustState.UNPAIRED,
        lastSeenAt = 1L,
    )

    fun secureStoreIsEmpty(): Boolean = secureStore.isEmpty()

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

private class TestFileTransferSettingsRepository(defaultDirectory: String) : FileTransferSettingsRepository {
    private val mutableSettings = MutableStateFlow(FileTransferSettings(defaultDirectory))
    override val settings: StateFlow<FileTransferSettings> = mutableSettings

    override suspend fun updateSaveDirectory(path: String) {
        mutableSettings.value = mutableSettings.value.copy(saveDirectory = path)
    }

    override suspend fun updateRequireIncomingConfirmation(required: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(requireIncomingConfirmation = required)
    }
}

private class MemorySecureKeyValueStore : SecureKeyValueStore {
    private val values = mutableMapOf<String, ByteArray>()

    override suspend fun read(key: String): ByteArray? = values[key]?.copyOf()

    override suspend fun write(key: String, value: ByteArray) {
        values[key] = value.copyOf()
    }

    fun isEmpty(): Boolean = values.isEmpty()
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
