package ink.x2.subnetdrop.domain.usecase

import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.port.TimestampProvider
import kotlinx.coroutines.CancellationException

class ForwardMessagesUseCase(
    private val sendMessage: SendMessageUseCase,
    private val timestampProvider: TimestampProvider,
) {
    suspend operator fun invoke(
        messages: List<Message>,
        targetConversationId: String,
        senderId: String,
        recipientId: String,
    ): Result<List<Message>> {
        if (messages.isEmpty()) return Result.success(emptyList())
        return try {
            val forwarded = mutableListOf<Message>()
            var firstFailure: Throwable? = null
            val firstCreatedAt = timestampProvider.nowMillis()
            messages
                .sortedWith(compareBy<Message>(Message::createdAt).thenBy(Message::id))
                .forEachIndexed { index, message ->
                    sendMessage.sendAt(
                        conversationId = targetConversationId,
                        senderId = senderId,
                        recipientId = recipientId,
                        body = message.body,
                        createdAt = firstCreatedAt + index,
                    )
                        .onSuccess(forwarded::add)
                        .onFailure { failure -> if (firstFailure == null) firstFailure = failure }
                }
            val failure = firstFailure
            if (failure == null) Result.success(forwarded) else Result.failure(failure)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
