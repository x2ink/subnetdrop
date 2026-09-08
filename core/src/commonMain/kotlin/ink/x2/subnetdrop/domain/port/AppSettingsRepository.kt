package ink.x2.subnetdrop.domain.port

import ink.x2.subnetdrop.domain.model.AppLanguage
import kotlinx.coroutines.flow.StateFlow

interface AppSettingsRepository {
    val language: StateFlow<AppLanguage>

    suspend fun updateLanguage(language: AppLanguage)
}
