package ink.x2.subnetdrop.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ink.x2.subnetdrop.data.db.ChatDatabase
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SqlDelightPersistenceTest {
    @Test
    fun persistsPeerIdentityAndMessagesAcrossDatabaseReopen() = runTest {
        val databasePath = Files.createTempDirectory("subnetdrop-test").resolve("chat.db")
        val firstDriver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePathString()}")
        ChatDatabase.Schema.create(firstDriver)
        val firstDatabase = ChatDatabase(firstDriver)
        seedDatabase(firstDatabase)
        firstDriver.close()

        val secondDriver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePathString()}")
        try {
            val database = ChatDatabase(secondDriver)
            val peer = SqlDelightPeerRepository(database).findPeer(PEER_ID)
            val identity = SqlDelightTrustedIdentityRepository(database).find(PEER_ID)
            val chatRepository = SqlDelightChatRepository(database)
            val messages = chatRepository.observeMessages(CONVERSATION_ID).first()
            val conversation = chatRepository.observeConversations().first().single()

            assertEquals(TrustState.TRUSTED, peer?.trustState)
            assertNotNull(identity)
            assertEquals(listOf("hello", "reply"), messages.map(Message::body))
            assertEquals(1L, conversation.unreadCount)
            chatRepository.markConversationRead(CONVERSATION_ID)
            chatRepository.markOutgoingMessagesRead(PEER_ID, listOf("message-1"))
            val updatedMessages = chatRepository.observeMessages(CONVERSATION_ID).first()
            assertEquals(DeliveryStatus.READ, updatedMessages.first().status)
            assertEquals(true, updatedMessages.last().isRead)
            assertEquals(0L, chatRepository.observeConversations().first().single().unreadCount)
        } finally {
            secondDriver.close()
            Files.deleteIfExists(databasePath)
            Files.deleteIfExists(databasePath.parent)
        }
    }

    private suspend fun seedDatabase(database: ChatDatabase) {
        SqlDelightPeerRepository(database).upsertPeer(
            Peer(
                id = PEER_ID,
                displayName = "Peer",
                host = "192.168.1.8",
                port = 45_892,
                availability = PeerAvailability.ONLINE,
                trustState = TrustState.UNPAIRED,
                lastSeenAt = 100L,
            ),
        )
        SqlDelightTrustedIdentityRepository(database).save(
            identity = ink.x2.subnetdrop.domain.model.PublicIdentity(
                deviceId = PEER_ID,
                displayName = "Peer",
                encryptionPublicKey = byteArrayOf(1, 2),
                signingPublicKey = byteArrayOf(3, 4),
            ),
            verifiedAt = 101L,
        )
        SqlDelightChatRepository(database).saveMessage(
            Message(
                id = "message-1",
                conversationId = CONVERSATION_ID,
                senderId = LOCAL_ID,
                recipientId = PEER_ID,
                body = "hello",
                createdAt = 102L,
                direction = MessageDirection.OUTGOING,
                status = DeliveryStatus.DELIVERED,
            ),
        )
        SqlDelightChatRepository(database).saveMessage(
            Message(
                id = "message-2",
                conversationId = CONVERSATION_ID,
                senderId = PEER_ID,
                recipientId = LOCAL_ID,
                body = "reply",
                createdAt = 103L,
                direction = MessageDirection.INCOMING,
                status = DeliveryStatus.DELIVERED,
            ),
        )
    }

    private companion object {
        const val LOCAL_ID = "local"
        const val PEER_ID = "peer"
        const val CONVERSATION_ID = "local:peer"
    }
}
