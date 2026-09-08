package ink.x2.subnetdrop.data

import com.russhwolf.settings.Settings
import ink.x2.subnetdrop.domain.model.AppLanguage
import ink.x2.subnetdrop.domain.port.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MultiplatformAppSettingsRepository(
    private val storage: Settings,
) : AppSettingsRepository {
    private val updateMutex = Mutex()
    private val mutableLanguage = MutableStateFlow(
        decodeLanguage(storage.getString(LANGUAGE_KEY, AppLanguage.SYSTEM.name)),
    )

    override val language: StateFlow<AppLanguage> = mutableLanguage.asStateFlow()

    override suspend fun updateLanguage(language: AppLanguage) {
        updateMutex.withLock {
            storage.putString(LANGUAGE_KEY, language.name)
            mutableLanguage.value = language
        }
    }

    private fun decodeLanguage(value: String): AppLanguage =
        AppLanguage.entries.firstOrNull { it.name == value } ?: AppLanguage.SYSTEM

    private companion object {
        const val LANGUAGE_KEY = "app.language"
    }
}
