package ink.x2.subnetdrop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher as rememberFileKitPickerLauncher
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.PUBLIC_DOWNLOADS_LOCATION
import ink.x2.subnetdrop.domain.model.LocalFile
import ink.x2.subnetdrop.domain.port.FileTransferService
import ink.x2.subnetdrop.resources.AppString
import ink.x2.subnetdrop.resources.appString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun rememberFilePickerLauncher(
    maxFileSizeBytes: Long,
    onFilesSelected: (List<LocalFile>) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val messages = fileInputMessages()
    val launcher = rememberFileKitPickerLauncher(
        mode = FileKitMode.Multiple(maxItems = FileTransferService.MAX_FILES_PER_BATCH),
        onError = { failure -> onError(messages.withDetail(messages.filePickerOpenFailed, failure.message)) },
        onResult = { selected ->
            selected ?: return@rememberFileKitPickerLauncher
            scope.launch {
                try {
                    onFilesSelected(selected.toTransferFiles(maxFileSizeBytes))
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    onError(messages.forException(exception, messages.selectedFilesReadFailed))
                }
            }
        },
    )
    return launcher::launch
}

@Composable
fun rememberSaveDirectoryPickerLauncher(
    currentDirectory: String,
    onDirectorySelected: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val messages = fileInputMessages()
    val launcher = rememberDirectoryPickerLauncher(
        directory = currentDirectory
            .takeIf { it.isNotBlank() && it != PUBLIC_DOWNLOADS_LOCATION }
            ?.let(::PlatformFile),
        onError = { failure -> onError(messages.withDetail(messages.directoryPickerOpenFailed, failure.message)) },
        onResult = { selected ->
            selected ?: return@rememberDirectoryPickerLauncher
            scope.launch {
                runCatching {
                    persistDirectoryAccess(selected)
                    selected.path
                }
                    .onSuccess(onDirectorySelected)
                    .onFailure { failure ->
                        onError(messages.withDetail(messages.directoryAccessSaveFailed, failure.message))
                    }
            }
        },
    )
    return launcher::launch
}

internal suspend fun List<PlatformFile>.toTransferFiles(maxFileSizeBytes: Long): List<LocalFile> {
    if (isEmpty()) throw FileInputException(FileInputError.NO_FILES)
    if (size > FileTransferService.MAX_FILES_PER_BATCH) {
        throw FileInputException(FileInputError.TOO_MANY_FILES)
    }
    return map { it.toTransferFile(maxFileSizeBytes) }
}

internal suspend fun List<FileTransfer>.toForwardFiles(maxFileSizeBytes: Long): List<LocalFile> {
    if (isEmpty()) return emptyList()
    if (size > FileTransferService.MAX_FILES_PER_BATCH) {
        throw FileInputException(FileInputError.TOO_MANY_FILES)
    }
    return map { transfer -> transfer.toForwardFile(maxFileSizeBytes) }
}

private suspend fun FileTransfer.toForwardFile(maxFileSizeBytes: Long): LocalFile {
    val sourcePath = localPath
    if (status != FileTransferStatus.COMPLETED || sourcePath == null) {
        throw FileInputException(FileInputError.FILE_CHANGED)
    }
    if (size > maxFileSizeBytes) throw FileInputException(FileInputError.FILE_TOO_LARGE)
    val original = PlatformFile(sourcePath)
    if (original.size() != size) throw FileInputException(FileInputError.FILE_CHANGED)
    val source = if (sourcePath.startsWith(CONTENT_URI_PREFIX)) original.copyProviderFileToCache() else original
    if (source.size() != size) throw FileInputException(FileInputError.FILE_CHANGED)
    return LocalFile(name = fileName, path = source.path, size = size, contentType = contentType)
}

private suspend fun PlatformFile.toTransferFile(maxFileSizeBytes: Long): LocalFile {
    val originalName = name
    val originalContentType = mimeType()?.toString()
    val originalSize = size()
    if (originalSize < 0) throw FileInputException(FileInputError.SIZE_UNKNOWN)
    if (originalSize > maxFileSizeBytes) throw FileInputException(FileInputError.FILE_TOO_LARGE)
    val transferSource = if (path.startsWith(CONTENT_URI_PREFIX)) copyProviderFileToCache() else this
    val fileSize = transferSource.size()
    if (fileSize != originalSize) throw FileInputException(FileInputError.FILE_CHANGED)
    return LocalFile(
        name = originalName,
        path = transferSource.path,
        size = fileSize,
        contentType = originalContentType,
    )
}

private suspend fun PlatformFile.copyProviderFileToCache(): PlatformFile {
    val cacheDirectory = PlatformFile(FileKit.cacheDir, OUTGOING_CACHE_DIRECTORY)
    cacheDirectory.createDirectories()
    val extensionSuffix = extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
    val cacheName = "upload-${Random.nextLong().toULong().toString(16)}$extensionSuffix"
    val cachedFile = PlatformFile(cacheDirectory, cacheName)
    copyTo(cachedFile)
    return cachedFile
}

private const val CONTENT_URI_PREFIX = "content://"
private const val OUTGOING_CACHE_DIRECTORY = "outgoing-files"

internal fun displaySaveDirectory(saveDirectory: String, publicDownloadsLabel: String): String =
    if (saveDirectory == PUBLIC_DOWNLOADS_LOCATION) publicDownloadsLabel else saveDirectory

internal enum class FileInputError {
    NO_FILES,
    TOO_MANY_FILES,
    SIZE_UNKNOWN,
    FILE_TOO_LARGE,
    FILE_CHANGED,
    DROPPED_FILES_READ_FAILED,
    FILES_ONLY,
    REGULAR_FILES_ONLY,
}

internal class FileInputException(val error: FileInputError) : IllegalArgumentException()

internal data class FileInputMessages(
    val filePickerOpenFailed: String,
    val selectedFilesReadFailed: String,
    val directoryPickerOpenFailed: String,
    val directoryAccessSaveFailed: String,
    private val messages: Map<FileInputError, String>,
) {
    fun forError(error: FileInputError): String = checkNotNull(messages[error])

    fun forException(exception: Exception, fallback: String): String =
        (exception as? FileInputException)?.let { forError(it.error) } ?: withDetail(fallback, exception.message)

    fun withDetail(prefix: String, detail: String?): String =
        detail?.takeIf(String::isNotBlank)?.let { "$prefix — $it" } ?: prefix
}

@Composable
internal fun fileInputMessages(): FileInputMessages = FileInputMessages(
    filePickerOpenFailed = appString(AppString.FILE_PICKER_OPEN_FAILED),
    selectedFilesReadFailed = appString(AppString.SELECTED_FILES_READ_FAILED),
    directoryPickerOpenFailed = appString(AppString.DIRECTORY_PICKER_OPEN_FAILED),
    directoryAccessSaveFailed = appString(AppString.DIRECTORY_ACCESS_SAVE_FAILED),
    messages = mapOf(
        FileInputError.NO_FILES to appString(AppString.FILE_NONE_SELECTED),
        FileInputError.TOO_MANY_FILES to appString(
            AppString.FILE_BATCH_TOO_LARGE,
            FileTransferService.MAX_FILES_PER_BATCH,
        ),
        FileInputError.SIZE_UNKNOWN to appString(AppString.FILE_SIZE_UNKNOWN),
        FileInputError.FILE_TOO_LARGE to appString(AppString.FILE_TOO_LARGE),
        FileInputError.FILE_CHANGED to appString(AppString.FILE_CHANGED),
        FileInputError.DROPPED_FILES_READ_FAILED to appString(AppString.DROPPED_FILES_READ_FAILED),
        FileInputError.FILES_ONLY to appString(AppString.DROP_FILES_ONLY),
        FileInputError.REGULAR_FILES_ONLY to appString(AppString.DROP_REGULAR_FILES_ONLY),
    ),
)
