package ink.x2.subnetdrop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ink.x2.subnetdrop.domain.model.LocalFile

@Composable
internal actual fun Modifier.platformFilePasteTarget(
    maxFileSizeBytes: Long,
    onFilesPasted: (List<LocalFile>) -> Unit,
    onError: (FileInputError) -> Unit,
): Modifier = this
