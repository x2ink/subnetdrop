package ink.x2.kmp.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ink.x2.kmp.domain.model.LocalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
actual fun rememberFilePickerLauncher(
    onFileSelected: (LocalFile) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { cacheSelectedFile(context, uri) } }
                .onSuccess(onFileSelected)
                .onFailure { exception -> onError(exception.message ?: "无法读取所选文件") }
        }
    }
    return { launcher.launch("*/*") }
}

private fun cacheSelectedFile(context: Context, uri: Uri): LocalFile {
    val resolver = context.contentResolver
    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }?.takeIf(String::isNotBlank) ?: "file-${UUID.randomUUID()}"
    val cacheDirectory = File(context.cacheDir, "outgoing-files")
    require(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) { "无法创建临时文件目录" }
    val cachedFile = File.createTempFile("upload-", ".tmp", cacheDirectory)
    val input = requireNotNull(resolver.openInputStream(uri)) { "无法打开所选文件" }
    input.use { source -> cachedFile.outputStream().use(source::copyTo) }
    return LocalFile(
        name = displayName,
        path = cachedFile.path,
        size = cachedFile.length(),
        contentType = resolver.getType(uri),
    )
}
