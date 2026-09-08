package ink.x2.subnetdrop.network.storage

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileKitIncomingFileStoreTest {
    @Test
    fun publishesValidatedTemporaryFileWithoutOverwritingExistingFile() = runBlocking {
        val directory = Files.createTempDirectory("subnetdrop-incoming-").toFile()
        try {
            directory.resolve("report.txt").writeText("existing")
            val target = FileKitIncomingFileStore().create(
                saveDirectory = directory.path,
                transferId = "transfer-1",
                fileName = "report.txt",
                contentType = "text/plain",
                reservedFinalPaths = emptySet(),
            )
            val payload = "received".encodeToByteArray()

            target.outputSink.write(payload)
            target.outputSink.flush()
            target.outputSink.close()

            assertTrue(target.temporaryPath.contains(".subnetdrop-partials"))
            assertTrue(target.finalPath.endsWith("report (1).txt"))
            assertEquals(payload.size.toLong(), target.persistedSizeOrNull())

            target.publish()

            assertEquals("existing", directory.resolve("report.txt").readText())
            assertEquals("received", directory.resolve("report (1).txt").readText())
            assertFalse(Files.exists(java.nio.file.Path.of(target.temporaryPath)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun discardRemovesTemporaryFileWithoutPublishingDestination() = runBlocking {
        val directory = Files.createTempDirectory("subnetdrop-incoming-").toFile()
        try {
            val target = FileKitIncomingFileStore().create(
                saveDirectory = directory.path,
                transferId = "transfer-2",
                fileName = "cancelled.bin",
                contentType = null,
                reservedFinalPaths = emptySet(),
            )

            target.discard()

            assertFalse(Files.exists(java.nio.file.Path.of(target.temporaryPath)))
            assertFalse(directory.resolve("cancelled.bin").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
