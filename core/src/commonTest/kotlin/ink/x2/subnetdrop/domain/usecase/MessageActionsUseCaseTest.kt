package ink.x2.subnetdrop.domain.usecase

import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.domain.port.ChatRepository
import ink.x2.subnetdrop.domain.port.ChatTransport
import ink.x2.subnetdrop.domain.port.IdGenerator
import ink.x2.subnetdrop.domain.port.TimestampProvider
import ink.x2.subnetdrop.domain.port.TransportEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageActionsUseCaseTest {
    @Test
    fun forwardsMessagesInChronologicalOrder() = runTest {
        val repository = ActionChatRepository()
        val transport = ActionChatTransport()
        var nextId = 0
        val send = SendMessageUseCase(
            chatRepository = repository,
            chatTransport = transport,
            idGenerator = IdGenerator { "forward-${++nextId}" },
            timestampProvider = TimestampProvider { nextId.toLong() },
        )
        val forward = ForwardMessagesUseCase(send, TimestampProvider { 100L })

        val result = forward(
            messages = listOf(message("newer", 20L), message("older", 10L)),
            targetConversationId = "local:target",
            senderId = "local",
            recipientId = "target",
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("older", "newer"), transport.sent.map(Message::body))
        assertEquals(listOf("local:target", "local:target"), repository.saved.map(Message::conversationId))
        assertEquals(listOf(100L, 101L), repository.saved.map(Message::createdAt))
    }

    @Test
    fun continuesForwardingAfterOneMessageFails() = runTest {
        val repository = ActionChatRepository()
        val transport = ActionChatTransport(failureBody = "older")
        var nextId = 0
        val send = SendMessageUseCase(
            chatRepository = repository,
            chatTransport = transport,
            idGenerator = IdGenerator { "forward-${++nextId}" },
            timestampProvider = TimestampProvider { nextId.toLong() },
        )

        val result = ForwardMessagesUseCase(send, TimestampProvider { 100L })(
            messages = listOf(message("newer", 20L), message("older", 10L)),
            targetConversationId = "local:target",
            senderId = "local",
            recipientId = "target",
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("newer"), transport.sent.map(Message::body))
        assertEquals(listOf("older", "newer"), repository.saved.map(Message::body))
    }

    private fun message(body: String, createdAt: Long) = Message(
        id = body,
        conversationId = "local:source",
        senderId = "source",
        recipientId = "local",
        body = body,
        createdAt = createdAt,
        direction = MessageDirection.INCOMING,
        status = DeliveryStatus.READ,
    )
}

private class ActionChatRepository : ChatRepository {
    val saved = mutableListOf<Message>()

    override fun observeConversations(): Flow<List<Conversation>> = flowOf(emptyList())
    override fun observeMessages(conversationId: String): Flow<List<Message>> = flowOf(saved)
    override fun observeFileMessages(conversationId: String): Flow<List<FileTransfer>> = flowOf(emptyList())

    override suspend fun saveMessage(message: Message) {
        saved += message
    }

    override suspend fun saveFileMessage(transfer: FileTransfer) = Unit
    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) = Unit
    override suspend fun deleteMessages(conversationId: String, messageIds: List<String>) = Unit
    override suspend fun unreadIncomingMessageIds(conversationId: String): List<String> = emptyList()
    override suspend fun markConversationRead(conversationId: String) = Unit
    override suspend fun markOutgoingMessagesRead(peerId: String, messageIds: List<String>) = Unit
    override suspend fun containsMessage(messageId: String): Boolean = saved.any { it.id == messageId }
}

private class ActionChatTransport(
    private val failureBody: String? = null,
) : ChatTransport {
    override val events: Flow<TransportEvent> = emptyFlow()
    override val listenerPort: Int = 45_892
    val sent = mutableListOf<Message>()

    override suspend fun start() = Unit
    override suspend fun stop() = Unit

    override suspend fun send(peerId: String, message: Message) {
        if (message.body == failureBody) error("offline")
        sent += message
    }

    override suspend fun sendReadReceipt(peerId: String, messageIds: List<String>) = Unit
}
