package ink.x2.subnetdrop.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import ink.x2.subnetdrop.domain.model.IncomingFileOffer
import ink.x2.subnetdrop.resources.AppString
import ink.x2.subnetdrop.resources.appString

@Composable
fun IncomingFileDialog(
    offer: IncomingFileOffer,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(appString(AppString.RECEIVE_FILE_TITLE)) },
        text = {
            Text(
                appString(
                    AppString.RECEIVE_FILE_MESSAGE,
                    offer.peerDisplayName,
                    offer.fileName,
                    formatFileSize(offer.size),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(appString(AppString.ACTION_RECEIVE)) }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(appString(AppString.ACTION_REJECT)) }
        },
    )
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes < KIBIBYTE -> "$bytes B"
    bytes < MEBIBYTE -> "${oneDecimal(bytes.toDouble() / KIBIBYTE)} KB"
    bytes < GIBIBYTE -> "${oneDecimal(bytes.toDouble() / MEBIBYTE)} MB"
    else -> "${oneDecimal(bytes.toDouble() / GIBIBYTE)} GB"
}

private fun oneDecimal(value: Double): String {
    val roundedTenths = (value * 10).toLong()
    return "${roundedTenths / 10}.${roundedTenths % 10}"
}

private const val KIBIBYTE = 1_024L
private const val MEBIBYTE = KIBIBYTE * 1_024L
private const val GIBIBYTE = MEBIBYTE * 1_024L
