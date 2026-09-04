package ink.x2.subnetdrop.ui

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData

internal actual suspend fun persistDirectoryAccess(directory: PlatformFile) {
    directory.bookmarkData()
}
