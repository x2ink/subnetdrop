package ink.x2.subnetdrop.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import ink.x2.subnetdrop.data.db.ChatDatabase
import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.domain.port.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class SqlDelightChatRepository(
    database: ChatDatabase,
) : ChatRepository {
    private val queries = database.chatQueries

    override fun observeConversations(): Flow<List<Conversation>> = queries
        .selectConversationSummaries(::mapConversation)
        .asFlow()
        .mapToList(Dispatchers.Default)

    override fun observeMessages(conversationId: String): Flow<List<Message>> = queries
        .selectMessages(conversationId, ::mapMessage)
        .asFlow()
        .mapToList(Dispatchers.Default)

    override suspend fun saveMessage(message: Message) {
        val peerId = when (message.direction) {
            MessageDirection.INCOMING -> message.senderId
            MessageDirection.OUTGOING -> message.recipientId
        }
        queries.transaction {
            queries.insertConversation(
                id = message.conversationId,
                peer_id = peerId,
                updated_at = message.createdAt,
            )
            queries.updateConversationTimestamp(
                updated_at = message.createdAt,
                id = message.conversationId,
            )
            queries.insertMessage(
                id = message.id,
                conversation_id = message.conversationId,
                sender_id = message.senderId,
                recipient_id = message.recipientId,
                body = message.body,
                created_at = message.createdAt,
                direction = message.direction.name,
                status = message.status.name,
            )
        }
    }

    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) {
        queries.updateMessageStatus(status.name, messageId)
    }

    override suspend fun unreadIncomingMessageIds(conversationId: String): List<String> =
        queries.selectUnreadIncomingMessageIds(conversationId).executeAsList()

    override suspend fun markConversationRead(conversationId: String) {
        queries.markConversationRead(conversationId)
    }

    override suspend fun markOutgoingMessagesRead(peerId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        queries.markOutgoingMessagesRead(peerId, messageIds)
    }

    override suspend fun containsMessage(messageId: String): Boolean =
        queries.containsMessage(messageId).executeAsOne()

    private fun mapConversation(
        id: String,
        peerId: String,
        peerDisplayName: String,
        lastMessage: String?,
        updatedAt: Long,
        unreadCount: Long,
    ): Conversation = Conversation(
        id = id,
        peerId = peerId,
        peerDisplayName = peerDisplayName,
        lastMessage = lastMessage,
        updatedAt = updatedAt,
        unreadCount = unreadCount,
    )

    private fun mapMessage(
        id: String,
        conversationId: String,
        senderId: String,
        recipientId: String,
        body: String,
        createdAt: Long,
        direction: String,
        status: String,
        isRead: Boolean,
    ): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        recipientId = recipientId,
        body = body,
        createdAt = createdAt,
        direction = MessageDirection.valueOf(direction),
        status = DeliveryStatus.valueOf(status),
        isRead = isRead,
    )
}
