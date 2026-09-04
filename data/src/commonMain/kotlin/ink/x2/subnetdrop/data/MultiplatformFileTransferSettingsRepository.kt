package ink.x2.subnetdrop.data

import com.russhwolf.settings.Settings
import ink.x2.subnetdrop.domain.model.FileTransferSettings
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

    private companion object {
        const val SAVE_DIRECTORY_KEY = "file_transfer.save_directory"
        const val REQUIRE_CONFIRMATION_KEY = "file_transfer.require_incoming_confirmation"
    }
}
