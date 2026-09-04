package ink.x2.kmp.ui

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
import ink.x2.kmp.domain.port.PairingCandidate

@Composable
fun PairingDialog(
    candidate: PairingCandidate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("与 ${candidate.identity.displayName} 安全配对") },
        text = {
            Column {
                Text("请在两台设备上核对下方安全码，完全一致后再确认。")
                Text(
                    text = candidate.safetyCode,
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "安全码不一致可能意味着连接被冒充，请取消配对。",
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("安全码一致") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } },
    )
}
