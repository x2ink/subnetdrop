package ink.x2.subnetdrop.ui

import android.content.ClipData
import android.net.Uri
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(
    ClipData.newPlainText("SubnetDrop message", text),
)

internal actual fun localFileClipEntry(path: String): ClipEntry = ClipEntry(
    if (path.startsWith(CONTENT_URI_PREFIX)) {
        ClipData.newRawUri("SubnetDrop file", Uri.parse(path))
    } else {
        ClipData.newPlainText("SubnetDrop file path", path)
    },
)

private const val CONTENT_URI_PREFIX = "content://"
