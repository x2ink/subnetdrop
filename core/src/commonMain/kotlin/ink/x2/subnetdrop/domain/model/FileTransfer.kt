package ink.x2.subnetdrop.domain.model

data class LocalFile(
    val name: String,
    val path: String,
    val size: Long,
    val contentType: String? = null,
)

data class IncomingFileOffer(
    val transferId: String,
    val peerId: String,
    val peerDisplayName: String,
    val fileName: String,
    val size: Long,
    val contentType: String? = null,
)

data class FileTransfer(
    val id: String,
    val peerId: String,
    val fileName: String,
    val size: Long,
    val createdAt: Long,
    val contentType: String? = null,
    val direction: FileTransferDirection,
    val status: FileTransferStatus,
    val transferredBytes: Long = 0,
    val localPath: String? = null,
    val error: String? = null,
) {
    val progress: Float
        get() = if (size == 0L) 1f else (transferredBytes.toDouble() / size).toFloat().coerceIn(0f, 1f)
}

enum class FileTransferDirection {
    INCOMING,
    OUTGOING,
}

enum class FileTransferStatus {
    PREPARING,
    WAITING_FOR_ACCEPTANCE,
    TRANSFERRING,
    COMPLETED,
    REJECTED,
    CANCELLED,
    FAILED,
}
