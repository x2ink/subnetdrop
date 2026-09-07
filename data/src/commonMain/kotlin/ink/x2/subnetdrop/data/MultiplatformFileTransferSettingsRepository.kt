package ink.x2.subnetdrop.data

import com.russhwolf.settings.Settings
import ink.x2.subnetdrop.domain.model.FileTransferSettings
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.DEFAULT_MAX_FILE_SIZE_BYTES
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.MIN_CONFIGURABLE_MAX_FILE_SIZE_BYTES
import ink.x2.subnetdrop.domain.port.FileTransferSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MultiplatformFileTransferSettingsRepository(
    private val storage: Settings,
    defaultSaveDirectory: String,
) : FileTransferSettingsRepository {
    private val updateMutex = Mutex()
    private val mutableSettings = MutableStateFlow(
        FileTransferSettings(
            saveDirectory = storage.getString(SAVE_DIRECTORY_KEY, defaultSaveDirectory),
            requireIncomingConfirmation = storage.getBoolean(REQUIRE_CONFIRMATION_KEY, false),
            maxFileSizeBytes = storage.getLong(MAX_FILE_SIZE_BYTES_KEY, DEFAULT_MAX_FILE_SIZE_BYTES)
                .coerceIn(MIN_CONFIGURABLE_MAX_FILE_SIZE_BYTES, MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES),
        ),
    )

    override val settings: StateFlow<FileTransferSettings> = mutableSettings.asStateFlow()

    override suspend fun updateSaveDirectory(path: String) {
        val normalizedPath = path.trim()
        require(normalizedPath.isNotEmpty()) { "Save directory must not be empty" }
        updateMutex.withLock {
            storage.putString(SAVE_DIRECTORY_KEY, normalizedPath)
            mutableSettings.value = mutableSettings.value.copy(saveDirectory = normalizedPath)
        }
    }

    override suspend fun updateRequireIncomingConfirmation(required: Boolean) {
        updateMutex.withLock {
            storage.putBoolean(REQUIRE_CONFIRMATION_KEY, required)
            mutableSettings.value = mutableSettings.value.copy(requireIncomingConfirmation = required)
        }
    }

    override suspend fun updateMaxFileSizeBytes(maxFileSizeBytes: Long) {
        require(maxFileSizeBytes in MIN_CONFIGURABLE_MAX_FILE_SIZE_BYTES..MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES) {
            "File size limit is outside the configurable range"
        }
        updateMutex.withLock {
            storage.putLong(MAX_FILE_SIZE_BYTES_KEY, maxFileSizeBytes)
            mutableSettings.value = mutableSettings.value.copy(maxFileSizeBytes = maxFileSizeBytes)
        }
    }

    private companion object {
        const val SAVE_DIRECTORY_KEY = "file_transfer.save_directory"
        const val REQUIRE_CONFIRMATION_KEY = "file_transfer.require_incoming_confirmation"
        const val MAX_FILE_SIZE_BYTES_KEY = "file_transfer.max_file_size_bytes"
    }
}
