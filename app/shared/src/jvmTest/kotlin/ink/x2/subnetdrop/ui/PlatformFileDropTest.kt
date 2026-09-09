package ink.x2.subnetdrop.ui

import io.github.vinceglb.filekit.PlatformFile
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.domain.port.FileTransferService
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlatformFileDropTest {
    @Test
    fun readsRegularFilesFromDesktopTransferable() {
        val file = Files.createTempFile("subnetdrop-drop-", ".txt").toFile().apply(File::deleteOnExit)

        assertEquals(listOf(file), FileListTransferable(listOf(file)).readRegularFiles())
    }

    @Test
    fun rejectsDirectories() {
        val directory = Files.createTempDirectory("subnetdrop-drop-").toFile().apply(File::deleteOnExit)

        assertFailsWith<IllegalArgumentException> {
            FileListTransferable(listOf(directory)).readRegularFiles()
        }
    }

    @Test
    fun rejectsBatchesAboveLimit() {
        val file = Files.createTempFile("subnetdrop-drop-", ".txt").toFile().apply(File::deleteOnExit)
        val files = List(FileTransferService.MAX_FILES_PER_BATCH + 1) { file }

        assertFailsWith<IllegalArgumentException> {
            FileListTransferable(files).readRegularFiles()
        }
    }

    @Test
    fun droppedFileUsesSharedTransferValidation() {
        runBlocking {
            val file = Files.createTempFile("subnetdrop-drop-", ".txt").toFile().apply {
                writeText("drop payload")
                deleteOnExit()
            }

            val localFile = listOf(PlatformFile(file)).toTransferFiles(file.length()).single()

            assertEquals(file.name, localFile.name)
            assertEquals(file.absolutePath, localFile.path)
            assertEquals(file.length(), localFile.size)
            assertFailsWith<IllegalArgumentException> {
                listOf(PlatformFile(file)).toTransferFiles(file.length() - 1L)
            }
        }
    }

    @Test
    fun completedFileMessageCanBePreparedForForwarding() {
        runBlocking {
            val file = Files.createTempFile("subnetdrop-forward-", ".txt").toFile().apply {
                writeText("forward payload")
                deleteOnExit()
            }
            val transfer = FileTransfer(
                id = "transfer-1",
                conversationId = "alice:bob",
                peerId = "bob",
                fileName = "original-name.txt",
                size = file.length(),
                createdAt = 100L,
                contentType = "text/plain",
                direction = FileTransferDirection.INCOMING,
                status = FileTransferStatus.COMPLETED,
                transferredBytes = file.length(),
                localPath = file.absolutePath,
            )

            val forwarded = listOf(transfer).toForwardFiles(file.length()).single()

            assertEquals("original-name.txt", forwarded.name)
            assertEquals(file.absolutePath, forwarded.path)
            assertEquals(file.length(), forwarded.size)
            assertEquals("text/plain", forwarded.contentType)
        }
    }
}

private class FileListTransferable(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        require(isDataFlavorSupported(flavor))
        return files
    }
}
