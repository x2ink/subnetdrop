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
    onError: (String) -> Unit,
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
                        currentOnError(failure.message ?: "无法读取拖入的文件")
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
                        currentOnError(exception.message ?: "无法读取拖入的文件")
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
    require(isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { "仅支持拖入文件" }
    val entries = getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        ?: throw IllegalArgumentException("无法读取拖入的文件")
    val files = entries.filterIsInstance<File>()
    require(files.size == entries.size && files.isNotEmpty()) { "仅支持拖入文件" }
    require(files.size <= FileTransferService.MAX_FILES_PER_BATCH) {
        "一次最多发送 ${FileTransferService.MAX_FILES_PER_BATCH} 个文件"
    }
    require(files.all { it.isFile }) { "仅支持拖入普通文件，不支持文件夹" }
    return files
}
