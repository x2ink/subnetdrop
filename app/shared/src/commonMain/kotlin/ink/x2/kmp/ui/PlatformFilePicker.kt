package ink.x2.kmp.ui

import androidx.compose.runtime.Composable
import ink.x2.kmp.domain.model.LocalFile

@Composable
expect fun rememberFilePickerLauncher(
    onFileSelected: (LocalFile) -> Unit,
    onError: (String) -> Unit,
): () -> Unit
