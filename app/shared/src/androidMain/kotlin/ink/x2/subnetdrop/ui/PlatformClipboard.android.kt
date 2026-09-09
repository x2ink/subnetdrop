package ink.x2.subnetdrop.ui

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(
    ClipData.newPlainText("SubnetDrop message", text),
)
