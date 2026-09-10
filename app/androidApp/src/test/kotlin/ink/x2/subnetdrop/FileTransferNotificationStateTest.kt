package ink.x2.subnetdrop

import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FileTransferNotificationStateTest {
    @Test
    fun returnsIdleWhenNoTransferIsActive() {
        val state = listOf(
            transfer(status = FileTransferStatus.COMPLETED),
            transfer(status = FileTransferStatus.FAILED),
        ).toNotificationState()

        assertEquals(FileTransferNotificationState.Idle, state)
    }

    @Test
    fun exposesReceiverConfirmedProgressForOneOutgoingFile() {
        val state = assertIs<FileTransferNotificationState.Active>(
            listOf(transfer(transferredBytes = 25L)).toNotificationState(),
        )

        assertEquals(NotificationTransferDirection.OUTGOING, state.direction)
        assertEquals("file.bin", state.fileName)
        assertEquals(250, state.progress)
        assertEquals(25, state.percentage)
    }

    @Test
    fun aggregatesParallelTransfersAndClampsReportedBytes() {
        val state = assertIs<FileTransferNotificationState.Active>(
            listOf(
                transfer(size = 100L, transferredBytes = 80L),
                transfer(
                    id = "incoming",
                    size = 300L,
                    transferredBytes = 500L,
                    direction = FileTransferDirection.INCOMING,
                ),
            ).toNotificationState(),
        )

        assertEquals(NotificationTransferDirection.MIXED, state.direction)
        assertEquals(2, state.fileCount)
        assertEquals(null, state.fileName)
        assertEquals(380L, state.transferredBytes)
        assertEquals(400L, state.totalBytes)
        assertEquals(950, state.progress)
        assertEquals(95, state.percentage)
    }

    private fun transfer(
        id: String = "transfer",
        size: Long = 100L,
        transferredBytes: Long = 0L,
        direction: FileTransferDirection = FileTransferDirection.OUTGOING,
        status: FileTransferStatus = FileTransferStatus.TRANSFERRING,
    ) = FileTransfer(
        id = id,
        conversationId = "conversation",
        peerId = "peer",
        fileName = "file.bin",
        size = size,
        createdAt = 1L,
        direction = direction,
        status = status,
        transferredBytes = transferredBytes,
    )
}
