package ink.x2.subnetdrop.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiplatformFileTransferSettingsRepositoryTest {
    @Test
    fun defaultsToAutomaticReceptionAndPersistsUpdates() = runTest {
        val storage = MapSettings()
        val repository = MultiplatformFileTransferSettingsRepository(storage, "/default")

        assertEquals("/default", repository.settings.value.saveDirectory)
        assertFalse(repository.settings.value.requireIncomingConfirmation)

        repository.updateSaveDirectory("/chosen")
        repository.updateRequireIncomingConfirmation(true)

        val restored = MultiplatformFileTransferSettingsRepository(storage, "/other-default")
        assertEquals("/chosen", restored.settings.value.saveDirectory)
        assertTrue(restored.settings.value.requireIncomingConfirmation)
    }
}
