package ink.x2.subnetdrop.network.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.PUBLIC_DOWNLOADS_LOCATION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.buffered

class AndroidIncomingFileStore(
    context: Context,
    private val fallback: IncomingFileStore = FileKitIncomingFileStore(),
) : IncomingFileStore {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun create(
        saveDirectory: String,
        transferId: String,
        fileName: String,
        contentType: String?,
        reservedFinalPaths: Set<String>,
    ): IncomingFileTarget {
        if (saveDirectory != PUBLIC_DOWNLOADS_LOCATION) {
            return fallback.create(saveDirectory, transferId, fileName, contentType, reservedFinalPaths)
        }
        return withContext(Dispatchers.IO) {
            createMediaStoreTarget(fileName, contentType)
        }
    }

    private fun createMediaStoreTarget(fileName: String, contentType: String?): IncomingFileTarget {
        val displayName = uniqueDisplayName(fileName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, contentType ?: DEFAULT_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, PUBLIC_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(downloadsCollection(), values)
            ?: error("Unable to create a public Downloads entry")
        val file = PlatformFile(uri)
        return try {
            MediaStoreIncomingFileTarget(resolver, uri, file)
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }

    private fun uniqueDisplayName(fileName: String): String {
        if (!exists(fileName)) return fileName
        val extensionIndex = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        val base = fileName.substring(0, extensionIndex)
        val extension = fileName.substring(extensionIndex)
        var suffix = 1
        while (true) {
            val candidate = "$base ($suffix)$extension"
            if (!exists(candidate)) return candidate
            suffix += 1
        }
    }

    private fun exists(displayName: String): Boolean = resolver.query(
        downloadsCollection(),
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
        arrayOf(displayName, PUBLIC_RELATIVE_PATH),
        null,
    )?.use { it.moveToFirst() } == true

    private fun downloadsCollection() = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private class MediaStoreIncomingFileTarget(
        private val resolver: ContentResolver,
        private val uri: Uri,
        private val file: PlatformFile,
    ) : IncomingFileTarget {
        override val temporaryPath: String = file.path
        override val finalPath: String = file.path
        override val outputSink: Sink = file.sink().buffered()

        override fun persistedSizeOrNull(): Long? = resolver.openFileDescriptor(uri, READ_MODE)?.use { descriptor ->
            descriptor.statSize.takeIf { it >= 0 }
        }

        override suspend fun publish() = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            require(resolver.update(uri, values, null, null) == 1) {
                "Unable to publish received file"
            }
        }

        override suspend fun discard() = withContext(Dispatchers.IO) {
            runCatching { outputSink.close() }
            resolver.delete(uri, null, null)
            Unit
        }
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val READ_MODE = "r"
        val PUBLIC_RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/SubnetDrop/"
    }
}
