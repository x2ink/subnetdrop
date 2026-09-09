package ink.x2.subnetdrop.domain.usecase

import ink.x2.subnetdrop.domain.port.ChatRepository
import kotlinx.coroutines.CancellationException

class DeleteMessagesUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(conversationId: String, messageIds: List<String>): Result<Unit> {
        val distinctIds = messageIds.distinct()
        if (distinctIds.isEmpty()) return Result.success(Unit)
        return try {
            chatRepository.deleteMessages(conversationId, distinctIds)
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
