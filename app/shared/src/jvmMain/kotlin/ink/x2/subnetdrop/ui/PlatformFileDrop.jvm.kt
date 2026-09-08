package ink.x2.subnetdrop.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ink.x2.subnetdrop.domain.model.LocalFile
import ink.x2.subnetdrop.domain.port.FileTransferService
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.platformFileDropTarget(
    maxFileSizeBytes: Long,
    onFilesDropped: (List<LocalFile>) -> Unit,
    onError: (FileInputError) -> Unit,
    onDragActiveChanged: (Boolean) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val currentMaxFileSizeBytes by rememberUpdatedState(maxFileSizeBytes)
    val currentOnFilesDropped by rememberUpdatedState(onFilesDropped)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnDragActiveChanged by rememberUpdatedState(onDragActiveChanged)
    val target = remember(scope) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                currentOnDragActiveChanged(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                currentOnDragActiveChanged(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnDragActiveChanged(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentOnDragActiveChanged(false)
                val droppedFiles = runCatching { event.awtTransferable.readRegularFiles() }
                    .getOrElse { failure ->
                        currentOnError(
                            (failure as? FileInputException)?.error ?: FileInputError.DROPPED_FILES_READ_FAILED,
                        )
                        return false
                    }
                scope.launch {
                    try {
                        val files = withContext(Dispatchers.IO) {
                            droppedFiles.map(::PlatformFile).toTransferFiles(currentMaxFileSizeBytes)
                        }
                        currentOnFilesDropped(files)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        currentOnError(
                            (exception as? FileInputException)?.error ?: FileInputError.DROPPED_FILES_READ_FAILED,
                        )
                    }
                }
                return true
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        },
        target = target,
    )
}

internal fun Transferable.readRegularFiles(): List<File> {
    if (!isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        throw FileInputException(FileInputError.FILES_ONLY)
    }
    val entries = getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        ?: throw FileInputException(FileInputError.DROPPED_FILES_READ_FAILED)
    val files = entries.filterIsInstance<File>()
    if (files.size != entries.size || files.isEmpty()) {
        throw FileInputException(FileInputError.FILES_ONLY)
    }
    if (files.size > FileTransferService.MAX_FILES_PER_BATCH) {
        throw FileInputException(FileInputError.TOO_MANY_FILES)
    }
    if (files.any { !it.isFile }) throw FileInputException(FileInputError.REGULAR_FILES_ONLY)
    return files
}
