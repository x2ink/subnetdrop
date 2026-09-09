package ink.x2.subnetdrop.ui

import androidx.compose.ui.platform.ClipEntry

internal expect fun plainTextClipEntry(text: String): ClipEntry

internal expect fun localFileClipEntry(path: String): ClipEntry
