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
import ink.x2.subnetdrop.domain.model.LocalFile
import ink.x2.subnetdrop.domain.port.FileTransferService
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
    val launcher = rememberFileKitPickerLauncher(
        mode = FileKitMode.Multiple(maxItems = FileTransferService.MAX_FILES_PER_BATCH),
        onError = { failure -> onError(failure.message ?: "无法打开文件选择器") },
        onResult = { selected ->
            selected ?: return@rememberFileKitPickerLauncher
            scope.launch {
                try {
                    onFilesSelected(selected.map { it.toTransferFile(maxFileSizeBytes) })
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    onError(exception.message ?: "无法读取所选文件")
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
    val launcher = rememberDirectoryPickerLauncher(
        directory = currentDirectory.takeIf(String::isNotBlank)?.let(::PlatformFile),
        onError = { failure -> onError(failure.message ?: "无法打开目录选择器") },
        onResult = { selected ->
            selected ?: return@rememberDirectoryPickerLauncher
            scope.launch {
                runCatching {
                    persistDirectoryAccess(selected)
                    selected.path
                }
                    .onSuccess(onDirectorySelected)
                    .onFailure { failure -> onError(failure.message ?: "无法保存目录访问权限") }
            }
        },
    )
    return launcher::launch
}

private suspend fun PlatformFile.toTransferFile(maxFileSizeBytes: Long): LocalFile {
    val originalName = name
    val originalContentType = mimeType()?.toString()
    val originalSize = size()
    require(originalSize >= 0) { "无法确定所选文件大小" }
    require(originalSize <= maxFileSizeBytes) { "所选文件超过本机设置的大小上限" }
    val transferSource = if (path.startsWith(CONTENT_URI_PREFIX)) copyProviderFileToCache() else this
    val fileSize = transferSource.size()
    require(fileSize == originalSize) { "所选文件在准备传输时发生变化" }
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
