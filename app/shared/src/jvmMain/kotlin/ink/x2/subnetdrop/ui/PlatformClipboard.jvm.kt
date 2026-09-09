package ink.x2.subnetdrop.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(StringSelection(text))

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun localFileClipEntry(path: String): ClipEntry {
    val file = File(path)
    require(file.isFile) { "File is not available" }
    return ClipEntry(localFileListTransferable(listOf(file)))
}

internal fun localFileListTransferable(files: List<File>): Transferable = object : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        require(isDataFlavorSupported(flavor)) { "Unsupported clipboard format" }
        return files
    }
}
