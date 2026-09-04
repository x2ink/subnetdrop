package ink.x2.subnetdrop

import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.ui.ChatTimelineItem
import ink.x2.subnetdrop.ui.buildChatTimeline
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedCommonTest {

    @Test
    fun chatTimelineInterleavesItemsAndFiltersOtherConversationsAndPeers() {
        val messages = listOf(
            message(id = "first", createdAt = 100L),
            message(id = "last", createdAt = 300L),
            message(id = "other-conversation", createdAt = 250L, conversationId = "alice:carol"),
        )
        val transfers = listOf(
            transfer(id = "middle", peerId = "bob", createdAt = 200L),
            transfer(id = "other-peer", peerId = "carol", createdAt = 150L),
        )

        val timeline = buildChatTimeline(
            messages = messages,
            transfers = transfers,
            conversationId = "alice:bob",
            peerId = "bob",
        )

        assertEquals(
            listOf("message:first", "file:middle", "message:last"),
            timeline.map(ChatTimelineItem::stableKey),
        )
    }

    private fun message(
        id: String,
        createdAt: Long,
        conversationId: String = "alice:bob",
    ) = Message(
        id = id,
        conversationId = conversationId,
        senderId = "alice",
        recipientId = "bob",
        body = id,
        createdAt = createdAt,
        direction = MessageDirection.OUTGOING,
        status = DeliveryStatus.SENT,
    )

    private fun transfer(id: String, peerId: String, createdAt: Long) = FileTransfer(
        id = id,
        peerId = peerId,
        fileName = "$id.txt",
        size = 10L,
        createdAt = createdAt,
        direction = FileTransferDirection.OUTGOING,
        status = FileTransferStatus.TRANSFERRING,
    )
}
