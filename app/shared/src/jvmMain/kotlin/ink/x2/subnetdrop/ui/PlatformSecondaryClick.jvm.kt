package ink.x2.subnetdrop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton

@OptIn(ExperimentalFoundationApi::class)
internal actual fun Modifier.platformSecondaryClick(onClick: () -> Unit): Modifier = onClick(
    matcher = PointerMatcher.mouse(PointerButton.Secondary),
    onClick = onClick,
)
