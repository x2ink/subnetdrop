package ink.x2.subnetdrop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher as rememberFileKitPickerLauncher
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import ink.x2.subnetdrop.domain.model.LocalFile
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun rememberFilePickerLauncher(
    onFileSelected: (LocalFile) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberFileKitPickerLauncher(
        onError = { failure -> onError(failure.message ?: "无法打开文件选择器") },
        onResult = { selected ->
            selected ?: return@rememberFileKitPickerLauncher
            scope.launch {
                runCatching { selected.toTransferFile() }
                    .onSuccess(onFileSelected)
                    .onFailure { failure -> onError(failure.message ?: "无法读取所选文件") }
            }
        },
    )
    return launcher::launch
}

private suspend fun PlatformFile.toTransferFile(): LocalFile {
    val transferSource = if (path.startsWith(CONTENT_URI_PREFIX)) copyProviderFileToCache() else this
    val fileSize = transferSource.size()
    require(fileSize >= 0) { "无法确定所选文件大小" }
    return LocalFile(
        name = name,
        path = transferSource.path,
        size = fileSize,
        contentType = mimeType()?.toString(),
    )
}

private suspend fun PlatformFile.copyProviderFileToCache(): PlatformFile {
    val cacheDirectory = PlatformFile(FileKit.cacheDir, OUTGOING_CACHE_DIRECTORY)
    cacheDirectory.createDirectories()
    val cacheName = "upload-${Random.nextLong().toULong().toString(16)}.tmp"
    val cachedFile = PlatformFile(cacheDirectory, cacheName)
    copyTo(cachedFile)
    return cachedFile
}

private const val CONTENT_URI_PREFIX = "content://"
private const val OUTGOING_CACHE_DIRECTORY = "outgoing-files"
