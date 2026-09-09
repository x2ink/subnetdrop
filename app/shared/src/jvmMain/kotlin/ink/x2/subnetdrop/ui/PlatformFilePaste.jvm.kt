package ink.x2.subnetdrop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.github.vinceglb.filekit.PlatformFile
import ink.x2.subnetdrop.domain.model.LocalFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File

@Composable
internal actual fun Modifier.platformFilePasteTarget(
    maxFileSizeBytes: Long,
    onFilesPasted: (List<LocalFile>) -> Unit,
    onError: (FileInputError) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val currentMaxFileSizeBytes by rememberUpdatedState(maxFileSizeBytes)
    val currentOnFilesPasted by rememberUpdatedState(onFilesPasted)
    val currentOnError by rememberUpdatedState(onError)
    return onPreviewKeyEvent { event ->
        if (!event.isPasteShortcut()) return@onPreviewKeyEvent false
        val clipboardFiles = try {
            readClipboardFiles()
        } catch (_: Exception) {
            currentOnError(FileInputError.CLIPBOARD_FILES_READ_FAILED)
            return@onPreviewKeyEvent true
        } ?: return@onPreviewKeyEvent false
        scope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    clipboardFiles.map(::PlatformFile).toTransferFiles(currentMaxFileSizeBytes)
                }
                currentOnFilesPasted(files)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                currentOnError(
                    (exception as? FileInputException)?.error ?: FileInputError.CLIPBOARD_FILES_READ_FAILED,
                )
            }
        }
        true
    }
}

private fun KeyEvent.isPasteShortcut(): Boolean =
    type == KeyEventType.KeyDown && key == Key.V && (isCtrlPressed || isMetaPressed)

private fun readClipboardFiles(): List<File>? {
    val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null) ?: return null
    if (!contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null
    return contents.readRegularFiles()
}
