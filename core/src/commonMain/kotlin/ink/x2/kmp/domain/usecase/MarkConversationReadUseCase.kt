package ink.x2.kmp.domain.usecase

import ink.x2.kmp.domain.port.ChatRepository
import ink.x2.kmp.domain.port.ChatTransport
import kotlinx.coroutines.CancellationException

class MarkConversationReadUseCase(
    private val chatRepository: ChatRepository,
    private val chatTransport: ChatTransport,
) {
    suspend operator fun invoke(conversationId: String, peerId: String): Result<Unit> {
        val messageIds = chatRepository.unreadIncomingMessageIds(conversationId)
        if (messageIds.isEmpty()) return Result.success(Unit)
        chatRepository.markConversationRead(conversationId)
        return try {
            messageIds.chunked(MAX_RECEIPT_MESSAGE_COUNT).forEach { batch ->
                chatTransport.sendReadReceipt(peerId, batch)
            }
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private companion object {
        const val MAX_RECEIPT_MESSAGE_COUNT = 128
    }
}
