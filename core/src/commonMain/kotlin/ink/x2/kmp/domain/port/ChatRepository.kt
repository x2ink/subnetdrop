package ink.x2.kmp.domain.port

import ink.x2.kmp.domain.model.Conversation
import ink.x2.kmp.domain.model.DeliveryStatus
import ink.x2.kmp.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeMessages(conversationId: String): Flow<List<Message>>

    suspend fun saveMessage(message: Message)

    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus)

    suspend fun unreadIncomingMessageIds(conversationId: String): List<String>

    suspend fun markConversationRead(conversationId: String)

    suspend fun markOutgoingMessagesRead(peerId: String, messageIds: List<String>)

    suspend fun containsMessage(messageId: String): Boolean
}
