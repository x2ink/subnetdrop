package ink.x2.kmp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ink.x2.kmp.domain.model.LocalFile
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberFilePickerLauncher(
    onFileSelected: (LocalFile) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    return remember(onFileSelected, onError) {
        {
            runCatching {
                val dialog = FileDialog(null as Frame?, "选择要发送的文件", FileDialog.LOAD).apply {
                    isMultipleMode = false
                    isVisible = true
                }
                val selected = dialog.file?.let { fileName -> File(dialog.directory, fileName) }
                selected?.takeIf(File::isFile)?.let { file ->
                    LocalFile(
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                    )
                }
            }.onSuccess { selected ->
                selected?.let(onFileSelected)
            }.onFailure { exception ->
                onError(exception.message ?: "无法打开文件选择器")
            }
        }
    }
}
