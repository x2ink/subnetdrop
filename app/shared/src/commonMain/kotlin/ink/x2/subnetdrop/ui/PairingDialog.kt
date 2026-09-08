package ink.x2.subnetdrop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.x2.subnetdrop.domain.port.PairingCandidate
import ink.x2.subnetdrop.resources.AppString
import ink.x2.subnetdrop.resources.appString

@Composable
fun PairingDialog(
    candidate: PairingCandidate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(AppString.PAIRING_TITLE, candidate.identity.displayName)) },
        text = {
            Column {
                Text(appString(AppString.PAIRING_INSTRUCTION))
                Text(
                    text = candidate.safetyCode,
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = appString(AppString.PAIRING_WARNING),
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(appString(AppString.PAIRING_CONFIRM)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(appString(AppString.ACTION_CANCEL)) } },
    )
}
