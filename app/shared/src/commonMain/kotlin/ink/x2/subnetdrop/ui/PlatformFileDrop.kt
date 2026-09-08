package ink.x2.subnetdrop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ink.x2.subnetdrop.domain.model.LocalFile

@Composable
internal expect fun Modifier.platformFileDropTarget(
    maxFileSizeBytes: Long,
    onFilesDropped: (List<LocalFile>) -> Unit,
    onError: (String) -> Unit,
    onDragActiveChanged: (Boolean) -> Unit,
): Modifier
