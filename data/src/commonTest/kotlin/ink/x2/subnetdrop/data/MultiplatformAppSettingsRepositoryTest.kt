package ink.x2.subnetdrop.data

import com.russhwolf.settings.MapSettings
import ink.x2.subnetdrop.domain.model.AppLanguage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiplatformAppSettingsRepositoryTest {
    @Test
    fun defaultsToSystemLanguage() {
        val repository = MultiplatformAppSettingsRepository(MapSettings())

        assertEquals(AppLanguage.SYSTEM, repository.language.value)
    }

    @Test
    fun persistsSelectedLanguage() = runTest {
        val storage = MapSettings()
        val repository = MultiplatformAppSettingsRepository(storage)

        repository.updateLanguage(AppLanguage.JAPANESE)

        val restored = MultiplatformAppSettingsRepository(storage)
        assertEquals(AppLanguage.JAPANESE, restored.language.value)
    }

    @Test
    fun invalidStoredLanguageFallsBackToSystem() {
        val storage = MapSettings("app.language" to "UNKNOWN")

        val repository = MultiplatformAppSettingsRepository(storage)

        assertEquals(AppLanguage.SYSTEM, repository.language.value)
    }
}
