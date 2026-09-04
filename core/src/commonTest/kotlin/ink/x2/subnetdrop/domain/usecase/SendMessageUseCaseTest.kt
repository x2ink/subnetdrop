package ink.x2.subnetdrop.domain.usecase

import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.Message
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

class SendMessageUseCaseTest {
    @Test
    fun sendsAndMarksMessageDelivered() = runTest {
        val repository = RecordingChatRepository()
        val transport = RecordingTransport()
        val useCase = createUseCase(repository, transport)

        val result = useCase("alice:bob", "alice", "bob", "  hello  ")

        assertTrue(result.isSuccess)
        assertEquals("hello", repository.saved.single().body)
        assertEquals(listOf(DeliveryStatus.SENDING, DeliveryStatus.DELIVERED), repository.statuses)
        assertEquals("bob", transport.sentPeerId)
    }

    @Test
    fun marksMessageFailedWhenTransportFails() = runTest {
        val repository = RecordingChatRepository()
        val transport = RecordingTransport(failure = IllegalStateException("offline"))
        val useCase = createUseCase(repository, transport)

        val result = useCase("alice:bob", "alice", "bob", "hello")

        assertTrue(result.isFailure)
        assertEquals(listOf(DeliveryStatus.SENDING, DeliveryStatus.FAILED), repository.statuses)
        assertEquals(3, transport.sendAttempts)
    }

    @Test
    fun retriesAnExistingFailedMessageWithoutCreatingADuplicate() = runTest {
        val repository = RecordingChatRepository()
        val transport = RecordingTransport(failure = IllegalStateException("offline"))
        val useCase = createUseCase(repository, transport)
        useCase("alice:bob", "alice", "bob", "hello")
        transport.failure = null
        repository.statuses.clear()

        val result = useCase.retry(repository.saved.single().copy(status = DeliveryStatus.FAILED))

        assertTrue(result.isSuccess)
        assertEquals(1, repository.saved.size)
        assertEquals(listOf(DeliveryStatus.SENDING, DeliveryStatus.DELIVERED), repository.statuses)
    }

    private fun createUseCase(
        repository: ChatRepository,
        transport: ChatTransport,
    ) = SendMessageUseCase(
        chatRepository = repository,
        chatTransport = transport,
        idGenerator = IdGenerator { "message-1" },
        timestampProvider = TimestampProvider { 1_234L },
    )
}

private class RecordingChatRepository : ChatRepository {
    val saved = mutableListOf<Message>()
    val statuses = mutableListOf<DeliveryStatus>()

    override fun observeConversations(): Flow<List<Conversation>> = flowOf(emptyList())

    override fun observeMessages(conversationId: String): Flow<List<Message>> = flowOf(saved)

    override suspend fun saveMessage(message: Message) {
        saved += message
    }

    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) {
        statuses += status
    }

    override suspend fun unreadIncomingMessageIds(conversationId: String): List<String> = emptyList()

    override suspend fun markConversationRead(conversationId: String) = Unit

    override suspend fun markOutgoingMessagesRead(peerId: String, messageIds: List<String>) = Unit

    override suspend fun containsMessage(messageId: String): Boolean = saved.any { it.id == messageId }
}

private class RecordingTransport(
    var failure: Exception? = null,
) : ChatTransport {
    override val events: Flow<TransportEvent> = emptyFlow()
    override val listenerPort: Int = 45_892
    var sentPeerId: String? = null
    var sendAttempts = 0

    override suspend fun start() = Unit

    override suspend fun stop() = Unit

    override suspend fun send(peerId: String, message: Message) {
        sendAttempts += 1
        failure?.let { throw it }
        sentPeerId = peerId
    }

    override suspend fun sendReadReceipt(peerId: String, messageIds: List<String>) = Unit
}
