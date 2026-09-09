package ink.x2.subnetdrop

import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.ui.ChatTimelineItem
import ink.x2.subnetdrop.ui.buildChatTimeline
import ink.x2.subnetdrop.ui.displaySaveDirectory
import ink.x2.subnetdrop.ui.eligibleForwardTargets
import ink.x2.subnetdrop.ui.isFileMessageExpired
import ink.x2.subnetdrop.ui.isForwardable
import ink.x2.subnetdrop.ui.isTerminal
import ink.x2.subnetdrop.ui.toggle
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
    fun onlyCompletedFileWithLocalPathCanBeForwarded() {
        val completed = transfer(
            id = "completed",
            peerId = "bob",
            createdAt = 100L,
            status = FileTransferStatus.COMPLETED,
        ).copy(localPath = "/downloads/completed.txt")

        assertTrue(completed.isTerminal())
        assertTrue(completed.isForwardable())
        assertFalse(completed.copy(localPath = null).isForwardable())
        assertFalse(completed.copy(status = FileTransferStatus.TRANSFERRING).isTerminal())
        assertFalse(completed.copy(status = FileTransferStatus.TRANSFERRING).isForwardable())
    }

    @Test
    fun publicAndroidDownloadsMarkerHasReadableSettingsLabel() {
        assertEquals(
            "公共下载目录/Download/SubnetDrop",
            displaySaveDirectory(
                "mediastore://downloads/SubnetDrop",
                "公共下载目录/Download/SubnetDrop",
            ),
        )
        assertEquals("/chosen", displaySaveDirectory("/chosen", "Public downloads"))
    }

    @Test
    fun forwardTargetsContainOnlyOnlineTrustedPeers() {
        val targets = eligibleForwardTargets(
            listOf(
                peer("trusted-online", "Bravo", PeerAvailability.ONLINE, TrustState.TRUSTED),
                peer("trusted-offline", "Alpha", PeerAvailability.OFFLINE, TrustState.TRUSTED),
                peer("unpaired-online", "Charlie", PeerAvailability.ONLINE, TrustState.UNPAIRED),
                peer("trusted-online-2", "Alpha", PeerAvailability.ONLINE, TrustState.TRUSTED),
            ),
        )

        assertEquals(listOf("trusted-online-2", "trusted-online"), targets.map(Peer::id))
    }

    @Test
    fun togglingMessageSelectionAddsAndRemovesOnlyRequestedId() {
        val selected = setOf("first")

        assertEquals(setOf("first", "second"), selected.toggle("second"))
        assertEquals(emptySet(), selected.toggle("first"))
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

    private fun peer(
        id: String,
        name: String,
        availability: PeerAvailability,
        trustState: TrustState,
    ) = Peer(
        id = id,
        displayName = name,
        host = "192.168.1.10",
        port = 45_892,
        availability = availability,
        trustState = trustState,
        lastSeenAt = 100L,
    )
}
