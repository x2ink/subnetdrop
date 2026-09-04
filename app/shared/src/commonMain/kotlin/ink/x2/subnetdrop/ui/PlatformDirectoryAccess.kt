package ink.x2.subnetdrop.ui

import io.github.vinceglb.filekit.PlatformFile

internal expect suspend fun persistDirectoryAccess(directory: PlatformFile)
