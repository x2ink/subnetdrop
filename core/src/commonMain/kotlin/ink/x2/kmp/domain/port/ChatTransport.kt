package ink.x2.kmp.domain.port

import ink.x2.kmp.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatTransport {
    val events: Flow<TransportEvent>
    val listenerPort: Int

    suspend fun start()

    suspend fun stop()

    suspend fun send(peerId: String, message: Message)

    suspend fun sendReadReceipt(peerId: String, messageIds: List<String>)
}

sealed interface TransportEvent {
    data class MessageReceived(val message: Message) : TransportEvent

    data class MessageDelivered(val messageId: String) : TransportEvent

    data class Failure(val peerId: String?, val reason: String) : TransportEvent
}
