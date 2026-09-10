package ink.x2.subnetdrop

import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus

internal sealed interface FileTransferNotificationState {
    data object Idle : FileTransferNotificationState

    data class Active(
        val direction: NotificationTransferDirection,
        val fileCount: Int,
        val fileName: String?,
        val transferredBytes: Long,
        val totalBytes: Long,
    ) : FileTransferNotificationState {
        val progress: Int
            get() = if (totalBytes == 0L) {
                0
            } else {
                ((transferredBytes.toDouble() / totalBytes) * NOTIFICATION_PROGRESS_MAX)
                    .toInt()
                    .coerceIn(0, NOTIFICATION_PROGRESS_MAX)
            }

        val percentage: Int
            get() = (progress / 10f).toInt().coerceIn(0, 100)
    }
}

internal enum class NotificationTransferDirection {
    INCOMING,
    OUTGOING,
    MIXED,
}

internal fun List<FileTransfer>.toNotificationState(): FileTransferNotificationState {
    val active = filter(FileTransfer::isNotificationActive)
    if (active.isEmpty()) return FileTransferNotificationState.Idle
    return FileTransferNotificationState.Active(
        direction = active.notificationDirection(),
        fileCount = active.size,
        fileName = active.singleOrNull()?.fileName,
        transferredBytes = active.sumOf { it.transferredBytes.coerceIn(0L, it.size) },
        totalBytes = active.sumOf(FileTransfer::size),
    )
}

private fun FileTransfer.isNotificationActive(): Boolean = when (status) {
    FileTransferStatus.PREPARING,
    FileTransferStatus.WAITING_FOR_ACCEPTANCE,
    FileTransferStatus.TRANSFERRING,
    -> true
    FileTransferStatus.COMPLETED,
    FileTransferStatus.REJECTED,
    FileTransferStatus.CANCELLED,
    FileTransferStatus.FAILED,
    -> false
}

private fun List<FileTransfer>.notificationDirection(): NotificationTransferDirection = when {
    all { it.direction == FileTransferDirection.OUTGOING } -> NotificationTransferDirection.OUTGOING
    all { it.direction == FileTransferDirection.INCOMING } -> NotificationTransferDirection.INCOMING
    else -> NotificationTransferDirection.MIXED
}

internal const val NOTIFICATION_PROGRESS_MAX = 1_000
