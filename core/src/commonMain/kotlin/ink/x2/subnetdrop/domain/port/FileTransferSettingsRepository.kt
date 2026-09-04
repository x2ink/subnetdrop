package ink.x2.subnetdrop.domain.port

import ink.x2.subnetdrop.domain.model.FileTransferSettings
import kotlinx.coroutines.flow.StateFlow

interface FileTransferSettingsRepository {
    val settings: StateFlow<FileTransferSettings>

    suspend fun updateSaveDirectory(path: String)

    suspend fun updateRequireIncomingConfirmation(required: Boolean)
}
