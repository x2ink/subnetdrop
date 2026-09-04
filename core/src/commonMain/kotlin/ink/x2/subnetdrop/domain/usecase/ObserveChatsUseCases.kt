package ink.x2.subnetdrop.domain.usecase

import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.port.ChatRepository
import kotlinx.coroutines.flow.Flow

class ObserveConversationsUseCase(
    private val chatRepository: ChatRepository,
) {
    operator fun invoke(): Flow<List<Conversation>> = chatRepository.observeConversations()
}

class ObserveMessagesUseCase(
    private val chatRepository: ChatRepository,
) {
    operator fun invoke(conversationId: String): Flow<List<Message>> =
        chatRepository.observeMessages(conversationId)
}
