package ink.x2.subnetdrop.domain.usecase

import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.port.ChatRepository
import ink.x2.subnetdrop.domain.port.ChatTransport
import ink.x2.subnetdrop.domain.port.TransportEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkConversationReadUseCaseTest {
    @Test
    fun marksLocalMessagesAndSendsReceiptInBoundedBatches() = runTest {
        val messageIds = (1..130).map { "message-$it" }
        val repository = ReadStateRepository(messageIds)
        val transport = ReadStateTransport()
        val useCase = MarkConversationReadUseCase(repository, transport)

        val result = useCase("alice:bob", "bob")

        assertTrue(result.isSuccess)
        assertEquals(listOf(128, 2), transport.receiptBatches.map(List<String>::size))
        assertEquals(true, repository.markedRead)
    }
}

private class ReadStateRepository(
    private val unreadIds: List<String>,
) : ChatRepository {
    var markedRead = false

    override fun observeConversations(): Flow<List<Conversation>> = flowOf(emptyList())

    override fun observeMessages(conversationId: String): Flow<List<Message>> = flowOf(emptyList())

    override suspend fun saveMessage(message: Message) = Unit

    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) = Unit

    override suspend fun unreadIncomingMessageIds(conversationId: String): List<String> = unreadIds

    override suspend fun markConversationRead(conversationId: String) {
        markedRead = true
    }

    override suspend fun markOutgoingMessagesRead(peerId: String, messageIds: List<String>) = Unit

    override suspend fun containsMessage(messageId: String): Boolean = false
}

private class ReadStateTransport : ChatTransport {
    override val events: Flow<TransportEvent> = emptyFlow()
    override val listenerPort: Int = 45_892
    val receiptBatches = mutableListOf<List<String>>()

    override suspend fun start() = Unit

    override suspend fun stop() = Unit

    override suspend fun send(peerId: String, message: Message) = Unit

    override suspend fun sendReadReceipt(peerId: String, messageIds: List<String>) {
        receiptBatches += messageIds
    }
}
