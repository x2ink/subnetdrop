package ink.x2.subnetdrop

import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.ui.ChatTimelineItem
import ink.x2.subnetdrop.ui.buildChatTimeline
import ink.x2.subnetdrop.ui.displaySaveDirectory
import ink.x2.subnetdrop.ui.isFileMessageExpired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun chatTimelineInterleavesItemsAndFiltersOtherConversationsAndPeers() {
        val messages = listOf(
            message(id = "first", createdAt = 100L),
            message(id = "last", createdAt = 300L),
            message(id = "other-conversation", createdAt = 250L, conversationId = "alice:carol"),
        )
        val storedFileMessages = listOf(
            transfer(id = "stored", peerId = "bob", createdAt = 150L, status = FileTransferStatus.COMPLETED),
            transfer(id = "middle", peerId = "bob", createdAt = 190L, status = FileTransferStatus.FAILED),
            transfer(id = "other-conversation", peerId = "bob", createdAt = 175L, conversationId = "alice:carol"),
        )
        val liveTransfers = listOf(
            transfer(id = "middle", peerId = "bob", createdAt = 200L),
            transfer(id = "other-peer", peerId = "carol", createdAt = 150L),
        )

        val timeline = buildChatTimeline(
            messages = messages,
            storedFileMessages = storedFileMessages,
            transfers = liveTransfers,
            conversationId = "alice:bob",
            peerId = "bob",
        )

        assertEquals(
            listOf("message:first", "file:stored", "file:middle", "message:last"),
            timeline.map(ChatTimelineItem::stableKey),
        )
    }

    @Test
    fun completedFileMessageExpiresOnlyAfterLocalFileIsKnownMissing() {
        val completed = transfer(
            id = "completed",
            peerId = "bob",
            createdAt = 100L,
            status = FileTransferStatus.COMPLETED,
        )

        assertFalse(isFileMessageExpired(completed, null))
        assertFalse(isFileMessageExpired(completed, true))
        assertTrue(isFileMessageExpired(completed, false))
        assertFalse(isFileMessageExpired(completed.copy(status = FileTransferStatus.FAILED), false))
    }

    @Test
    fun publicAndroidDownloadsMarkerHasReadableSettingsLabel() {
        assertEquals(
            "公共下载目录/Download/SubnetDrop",
            displaySaveDirectory("mediastore://downloads/SubnetDrop"),
        )
        assertEquals("/chosen", displaySaveDirectory("/chosen"))
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

    private fun transfer(
        id: String,
        peerId: String,
        createdAt: Long,
        conversationId: String = "alice:$peerId",
        status: FileTransferStatus = FileTransferStatus.TRANSFERRING,
    ) = FileTransfer(
        id = id,
        conversationId = conversationId,
        peerId = peerId,
        fileName = "$id.txt",
        size = 10L,
        createdAt = createdAt,
        direction = FileTransferDirection.OUTGOING,
        status = status,
    )
}
