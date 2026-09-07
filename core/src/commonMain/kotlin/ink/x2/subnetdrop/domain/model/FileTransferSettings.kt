package ink.x2.subnetdrop.domain.model

data class FileTransferSettings(
    val saveDirectory: String,
    val requireIncomingConfirmation: Boolean = false,
    val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
) {
    init {
        require(maxFileSizeBytes in MIN_CONFIGURABLE_MAX_FILE_SIZE_BYTES..MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES) {
            "File size limit is outside the configurable range"
        }
    }

    companion object {
        const val PUBLIC_DOWNLOADS_LOCATION = "mediastore://downloads/SubnetDrop"
        const val BYTES_PER_GIB = 1_024L * 1_024L * 1_024L
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 10L * BYTES_PER_GIB
        const val MIN_CONFIGURABLE_MAX_FILE_SIZE_BYTES = BYTES_PER_GIB
        const val MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES = 1_024L * BYTES_PER_GIB
    }
}
