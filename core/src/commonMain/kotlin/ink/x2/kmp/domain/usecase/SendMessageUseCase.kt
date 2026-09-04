package ink.x2.kmp.domain.usecase

import ink.x2.kmp.domain.model.DeliveryStatus
import ink.x2.kmp.domain.model.Message
import ink.x2.kmp.domain.model.MessageDirection
import ink.x2.kmp.domain.port.ChatRepository
import ink.x2.kmp.domain.port.ChatTransport
import ink.x2.kmp.domain.port.IdGenerator
import ink.x2.kmp.domain.port.TimestampProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val chatTransport: ChatTransport,
    private val idGenerator: IdGenerator,
    private val timestampProvider: TimestampProvider,
) {
    suspend operator fun invoke(
        conversationId: String,
        senderId: String,
        recipientId: String,
        body: String,
    ): Result<Message> {
        val normalizedBody = body.trim()
        require(normalizedBody.isNotEmpty()) { "Message body cannot be blank" }
        require(normalizedBody.length <= MAX_TEXT_LENGTH) { "Message body is too long" }

        val message = Message(
            id = idGenerator.generate(),
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            body = normalizedBody,
            createdAt = timestampProvider.nowMillis(),
            direction = MessageDirection.OUTGOING,
            status = DeliveryStatus.PENDING,
        )
        chatRepository.saveMessage(message)
        return deliver(message)
    }

    suspend fun retry(message: Message): Result<Message> {
        require(message.direction == MessageDirection.OUTGOING) { "Only outgoing messages can be retried" }
        require(message.status == DeliveryStatus.FAILED) { "Only failed messages can be retried" }
        return deliver(message)
    }

    private suspend fun deliver(message: Message): Result<Message> {
        return try {
            chatRepository.updateMessageStatus(message.id, DeliveryStatus.SENDING)
            sendWithRetry(message.recipientId, message)
            chatRepository.updateMessageStatus(message.id, DeliveryStatus.DELIVERED)
            Result.success(message.copy(status = DeliveryStatus.DELIVERED))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            chatRepository.updateMessageStatus(message.id, DeliveryStatus.FAILED)
            Result.failure(exception)
        }
    }

    private suspend fun sendWithRetry(recipientId: String, message: Message) {
        var lastFailure: Exception? = null
        repeat(MAX_SEND_ATTEMPTS) { attempt ->
            try {
                chatTransport.send(recipientId, message)
                return
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                lastFailure = exception
                if (attempt < RETRY_DELAYS_MS.size) delay(RETRY_DELAYS_MS[attempt])
            }
        }
        throw requireNotNull(lastFailure) { "Message delivery failed without an error" }
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 8_192
        const val MAX_SEND_ATTEMPTS = 3
        val RETRY_DELAYS_MS = listOf(300L, 1_000L)
    }
}
