package ink.x2.subnetdrop.data

import com.russhwolf.settings.MapSettings
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.BYTES_PER_GIB
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.DEFAULT_MAX_FILE_SIZE_BYTES
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiplatformFileTransferSettingsRepositoryTest {
    @Test
    fun defaultsToAutomaticReceptionAndPersistsUpdates() = runTest {
        val storage = MapSettings()
        val repository = MultiplatformFileTransferSettingsRepository(storage, "/default")

        assertEquals("/default", repository.settings.value.saveDirectory)
        assertFalse(repository.settings.value.requireIncomingConfirmation)
        assertEquals(DEFAULT_MAX_FILE_SIZE_BYTES, repository.settings.value.maxFileSizeBytes)

        repository.updateSaveDirectory("/chosen")
        repository.updateRequireIncomingConfirmation(true)
        repository.updateMaxFileSizeBytes(25L * BYTES_PER_GIB)

        val restored = MultiplatformFileTransferSettingsRepository(storage, "/other-default")
        assertEquals("/chosen", restored.settings.value.saveDirectory)
        assertTrue(restored.settings.value.requireIncomingConfirmation)
        assertEquals(25L * BYTES_PER_GIB, restored.settings.value.maxFileSizeBytes)
    }

    @Test
    fun rejectsFileSizeLimitOutsideConfigurableRange() = runTest {
        val repository = MultiplatformFileTransferSettingsRepository(MapSettings(), "/default")

        assertFailsWith<IllegalArgumentException> {
            repository.updateMaxFileSizeBytes(0L)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.updateMaxFileSizeBytes(MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES + 1L)
        }
    }
}
