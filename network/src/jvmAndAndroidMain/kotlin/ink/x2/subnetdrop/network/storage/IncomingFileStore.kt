package ink.x2.subnetdrop.network.storage

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.buffered

interface IncomingFileStore {
    suspend fun create(
        saveDirectory: String,
        transferId: String,
        fileName: String,
        contentType: String?,
        reservedFinalPaths: Set<String>,
    ): IncomingFileTarget
}

interface IncomingFileTarget {
    val temporaryPath: String
    val finalPath: String
    val outputSink: Sink

    fun persistedSizeOrNull(): Long?

    suspend fun publish()

    suspend fun discard()
}

class FileKitIncomingFileStore : IncomingFileStore {
    override suspend fun create(
        saveDirectory: String,
        transferId: String,
        fileName: String,
        contentType: String?,
        reservedFinalPaths: Set<String>,
    ): IncomingFileTarget = withContext(Dispatchers.IO) {
        val directory = PlatformFile(saveDirectory)
        directory.createDirectories()
        require(directory.isDirectory()) { "Unable to create received-files directory" }
        val finalFile = uniqueDestinationFile(directory, fileName, reservedFinalPaths)
        val partialDirectory = PlatformFile(directory, PARTIAL_DIRECTORY_NAME)
        partialDirectory.createDirectories()
        val temporaryFile = PlatformFile(partialDirectory, "$transferId-$fileName")
        require(!temporaryFile.exists()) { "Temporary file already exists" }
        try {
            FileKitIncomingFileTarget(
                temporaryFile = temporaryFile,
                finalFile = finalFile,
                outputSink = temporaryFile.sink().buffered(),
            )
        } catch (exception: Exception) {
            temporaryFile.delete(mustExist = false)
            throw exception
        }
    }

    private fun uniqueDestinationFile(
        directory: PlatformFile,
        fileName: String,
        reservedFinalPaths: Set<String>,
    ): PlatformFile {
        val direct = PlatformFile(directory, fileName)
        if (!direct.exists() && direct.path !in reservedFinalPaths) return direct
        val extensionIndex = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        val base = fileName.substring(0, extensionIndex)
        val extension = fileName.substring(extensionIndex)
        var suffix = 1
        while (true) {
            val candidate = PlatformFile(directory, "$base ($suffix)$extension")
            if (!candidate.exists() && candidate.path !in reservedFinalPaths) return candidate
            suffix += 1
        }
    }

    private class FileKitIncomingFileTarget(
        private val temporaryFile: PlatformFile,
        private val finalFile: PlatformFile,
        override val outputSink: Sink,
    ) : IncomingFileTarget {
        override val temporaryPath: String = temporaryFile.path
        override val finalPath: String = finalFile.path

        override fun persistedSizeOrNull(): Long? = temporaryFile.size().takeIf { it >= 0 }

        override suspend fun publish() = withContext(Dispatchers.IO) {
            require(!finalFile.exists()) { "Destination file appeared during transfer" }
            temporaryFile.atomicMove(finalFile)
        }

        override suspend fun discard() = withContext(Dispatchers.IO) {
            runCatching { outputSink.close() }
            temporaryFile.delete(mustExist = false)
        }
    }

    private companion object {
        const val PARTIAL_DIRECTORY_NAME = ".subnetdrop-partials"
    }
}
