package ink.x2.subnetdrop.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

internal actual val supportsFileManagerReveal: Boolean
    get() = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)

internal actual suspend fun revealFileInManager(path: String) = withContext(Dispatchers.IO) {
    val file = File(path).absoluteFile
    require(file.exists()) { "File does not exist: ${file.path}" }
    check(supportsFileManagerReveal) { "File manager integration is unavailable" }
    Desktop.getDesktop().browseFileDirectory(file)
    Unit
}
