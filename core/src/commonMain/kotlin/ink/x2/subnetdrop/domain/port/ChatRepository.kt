package ink.x2.subnetdrop.domain.port

import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeMessages(conversationId: String): Flow<List<Message>>

    fun observeFileMessages(conversationId: String): Flow<List<FileTransfer>>

    suspend fun saveMessage(message: Message)

    suspend fun saveFileMessage(transfer: FileTransfer)

    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus)

    suspend fun deleteMessages(conversationId: String, messageIds: List<String>)

    suspend fun deleteFileMessages(conversationId: String, transferIds: List<String>)

    suspend fun unreadIncomingMessageIds(conversationId: String): List<String>

    suspend fun markConversationRead(conversationId: String)

    suspend fun markOutgoingMessagesRead(peerId: String, messageIds: List<String>)

    suspend fun containsMessage(messageId: String): Boolean
}
