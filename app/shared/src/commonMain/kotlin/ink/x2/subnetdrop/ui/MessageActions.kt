package ink.x2.subnetdrop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.resources.AppString
import ink.x2.subnetdrop.resources.appString

internal enum class MessageAction {
    COPY,
    FORWARD,
    PARTIAL_SELECT,
    DELETE,
    MULTI_SELECT,
}

@Composable
internal fun MessageActionMenu(
    expanded: Boolean,
    actions: List<MessageAction> = MessageAction.entries,
    enabledActions: Set<MessageAction> = actions.toSet(),
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            actions.forEach { action ->
                MessageActionButton(
                    action = action,
                    enabled = action in enabledActions,
                    onClick = { onAction(action) },
                )
            }
        }
    }
}

@Composable
private fun MessageActionButton(action: MessageAction, enabled: Boolean, onClick: () -> Unit) {
    val presentation = action.presentation()
    val destructive = action == MessageAction.DELETE
    val actionColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .width(55.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = presentation.icon,
            contentDescription = presentation.label,
            modifier = Modifier.size(24.dp),
            tint = actionColor,
        )
        Text(
            text = presentation.label,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = actionColor,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageAction.presentation(): MessageActionPresentation = when (this) {
    MessageAction.COPY -> MessageActionPresentation(
        Icons.Outlined.ContentCopy,
        appString(AppString.MESSAGE_ACTION_COPY),
    )
    MessageAction.FORWARD -> MessageActionPresentation(
        Icons.AutoMirrored.Filled.ArrowForward,
        appString(AppString.MESSAGE_ACTION_FORWARD),
    )
    MessageAction.PARTIAL_SELECT -> MessageActionPresentation(
        Icons.Outlined.TextFields,
        appString(AppString.MESSAGE_ACTION_PARTIAL_SELECT),
    )
    MessageAction.DELETE -> MessageActionPresentation(
        Icons.Outlined.DeleteOutline,
        appString(AppString.MESSAGE_ACTION_DELETE),
    )
    MessageAction.MULTI_SELECT -> MessageActionPresentation(
        Icons.Outlined.SelectAll,
        appString(AppString.MESSAGE_ACTION_MULTI_SELECT),
    )
}

@Composable
internal fun MessageModeHeader(
    selectedCount: Int?,
    onClose: () -> Unit,
) {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose, modifier = Modifier.width(72.dp)) {
                Text(appString(if (selectedCount == null) AppString.ACTION_DONE else AppString.ACTION_CANCEL))
            }
            Text(
                text = selectedCount?.let { appString(AppString.SELECTED_MESSAGES, it) }
                    ?: appString(AppString.TEXT_SELECTION_TITLE),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Box(Modifier.width(72.dp))
        }
    }
}

@Composable
internal fun MessageSelectionIndicator(selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = appString(AppString.SELECT_MESSAGE),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
internal fun MultiSelectActionBar(
    selectedCount: Int,
    forwardEnabled: Boolean,
    deleteEnabled: Boolean,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomAction(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                label = appString(AppString.MESSAGE_ACTION_FORWARD),
                enabled = selectedCount > 0 && forwardEnabled,
                onClick = onForward,
            )
            BottomAction(
                icon = Icons.Outlined.DeleteOutline,
                label = appString(AppString.MESSAGE_ACTION_DELETE),
                enabled = selectedCount > 0 && deleteEnabled,
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun BottomAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color)
        Text(
            text = label,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ForwardMessagesSheet(
    messageCount: Int,
    peers: List<Peer>,
    onDismiss: () -> Unit,
    onPeerSelected: (Peer) -> Unit,
) {
    val targets = eligibleForwardTargets(peers)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = appString(AppString.FORWARD_MESSAGES_TITLE, messageCount),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 480.dp)) {
            if (targets.isEmpty()) {
                Text(
                    text = appString(AppString.NO_ONLINE_DEVICES),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(targets, key = Peer::id) { peer ->
                        PeerListItem(
                            peer = peer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPeerSelected(peer) }
                                .padding(horizontal = 16.dp),
                            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                            trailingContentDescription = appString(AppString.MESSAGE_ACTION_FORWARD),
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(appString(AppString.ACTION_CANCEL))
        }
    }
}

@Composable
internal fun DeleteMessagesDialog(
    messageCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(AppString.DELETE_MESSAGES_TITLE)) },
        text = { Text(appString(AppString.DELETE_MESSAGES_MESSAGE, messageCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(appString(AppString.MESSAGE_ACTION_DELETE), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(appString(AppString.ACTION_CANCEL)) }
        },
    )
}

internal fun eligibleForwardTargets(peers: List<Peer>): List<Peer> = peers
    .filter { it.availability == PeerAvailability.ONLINE && it.trustState == TrustState.TRUSTED }
    .sortedBy { it.displayName.lowercase() }

private data class MessageActionPresentation(
    val icon: ImageVector,
    val label: String,
)
