package ink.x2.subnetdrop.domain.usecase

import ink.x2.subnetdrop.domain.port.ChatRepository
import kotlinx.coroutines.CancellationException

class DeleteFileMessagesUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(conversationId: String, transferIds: List<String>): Result<Unit> {
        val distinctIds = transferIds.distinct()
        if (distinctIds.isEmpty()) return Result.success(Unit)
        return try {
            chatRepository.deleteFileMessages(conversationId, distinctIds)
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
