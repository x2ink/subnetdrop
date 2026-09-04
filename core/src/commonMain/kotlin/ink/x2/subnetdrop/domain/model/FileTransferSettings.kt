package ink.x2.subnetdrop.domain.model

data class FileTransferSettings(
    val saveDirectory: String,
    val requireIncomingConfirmation: Boolean = false,
)
