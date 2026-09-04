package ink.x2.kmp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.x2.kmp.domain.model.DeliveryStatus
import ink.x2.kmp.domain.model.FileTransfer
import ink.x2.kmp.domain.model.FileTransferStatus
import ink.x2.kmp.domain.model.LocalFile
import ink.x2.kmp.domain.model.Message
import ink.x2.kmp.domain.model.MessageDirection
import ink.x2.kmp.presentation.ChatSelection

@Composable
fun ChatScreen(
    selection: ChatSelection?,
    messages: List<Message>,
    modifier: Modifier,
    showBack: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onRetryMessage: (Message) -> Unit,
    transfers: List<FileTransfer>,
    onSendFile: (LocalFile) -> Unit,
    onCancelFile: (String) -> Unit,
    onFilePickerError: (String) -> Unit,
) {
    if (selection == null) {
        EmptyChat(modifier)
        return
    }
    val launchFilePicker = rememberFilePickerLauncher(onSendFile, onFilePickerError)
    val peerTransfers = transfers.filter { it.peerId == selection.peerId }.takeLast(MAX_VISIBLE_TRANSFERS)
    Column(modifier.fillMaxSize()) {
        ChatHeader(selection.peerDisplayName, showBack, onBack)
        MessageList(messages, Modifier.weight(1f), onRetryMessage)
        if (peerTransfers.isNotEmpty()) {
            FileTransferPanel(peerTransfers, onCancelFile)
        }
        Composer(onSend, launchFilePicker)
    }
}

@Composable
private fun ChatHeader(title: String, showBack: Boolean, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            PeerAvatar(title)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "端到端加密",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerAvatar(title: String) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
    modifier: Modifier,
    onRetryMessage: (Message) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = Message::id) { message ->
            MessageBubble(message, onRetryMessage)
        }
    }
}

@Composable
private fun MessageBubble(message: Message, onRetryMessage: (Message) -> Unit) {
    val outgoing = message.direction == MessageDirection.OUTGOING
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = MIN_BUBBLE_WIDTH, max = MAX_BUBBLE_WIDTH)
                .heightIn(min = MIN_BUBBLE_HEIGHT),
            shape = STRETCHABLE_BUBBLE_SHAPE,
            color = if (outgoing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Column(Modifier.padding(horizontal = BUBBLE_HORIZONTAL_PADDING, vertical = BUBBLE_VERTICAL_PADDING)) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (outgoing) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (outgoing) {
                    DeliveryState(message, onRetryMessage)
                }
            }
        }
    }
}

@Composable
private fun DeliveryState(message: Message, onRetryMessage: (Message) -> Unit) {
    val failed = message.status == DeliveryStatus.FAILED
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .then(if (failed) Modifier.clickable { onRetryMessage(message) } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = message.status.icon(),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = when {
                failed -> MaterialTheme.colorScheme.error
                message.status == DeliveryStatus.READ -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = if (failed) "发送失败，点击重试" else message.status.label(),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                failed -> MaterialTheme.colorScheme.error
                message.status == DeliveryStatus.READ -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun FileTransferPanel(
    transfers: List<FileTransfer>,
    onCancelFile: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = MAX_TRANSFER_PANEL_HEIGHT),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(transfers, key = FileTransfer::id) { transfer ->
            FileTransferCard(transfer, onCancelFile)
        }
    }
}

@Composable
private fun FileTransferCard(transfer: FileTransfer, onCancelFile: (String) -> Unit) {
    val cancellable = transfer.status == FileTransferStatus.PREPARING ||
        transfer.status == FileTransferStatus.WAITING_FOR_ACCEPTANCE ||
        transfer.status == FileTransferStatus.TRANSFERRING
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(transfer.fileName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${transfer.status.label()} · ${formatFileSize(transfer.transferredBytes)} / " +
                        formatFileSize(transfer.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cancellable) {
                    LinearProgressIndicator(
                        progress = { transfer.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                transfer.error?.let { error ->
                    Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (cancellable) {
                IconButton(onClick = { onCancelFile(transfer.id) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消文件传输")
                }
            }
        }
    }
}

@Composable
private fun Composer(onSend: (String) -> Unit, onAttachFile: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val send = {
        text.trim().takeIf(String::isNotEmpty)?.let(onSend)
        text = ""
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = onAttachFile, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Outlined.AttachFile, contentDescription = "发送文件")
            }
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_MESSAGE_LENGTH) text = it },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 132.dp),
                placeholder = { Text("输入消息…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
            )
            FilledIconButton(
                onClick = send,
                modifier = Modifier.size(52.dp),
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送消息")
            }
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "选择一个已配对设备",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "开始安全的一对一聊天",
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DeliveryStatus.icon(): ImageVector = when (this) {
    DeliveryStatus.PENDING, DeliveryStatus.SENDING -> Icons.Outlined.Refresh
    DeliveryStatus.SENT -> Icons.Outlined.Done
    DeliveryStatus.DELIVERED, DeliveryStatus.READ -> Icons.Outlined.DoneAll
    DeliveryStatus.FAILED -> Icons.Outlined.ErrorOutline
}

private fun DeliveryStatus.label(): String = when (this) {
    DeliveryStatus.PENDING -> "等待发送"
    DeliveryStatus.SENDING -> "发送中"
    DeliveryStatus.SENT -> "已发送"
    DeliveryStatus.DELIVERED -> "未读"
    DeliveryStatus.READ -> "已读"
    DeliveryStatus.FAILED -> "发送失败"
}

private fun FileTransferStatus.label(): String = when (this) {
    FileTransferStatus.PREPARING -> "正在校验"
    FileTransferStatus.WAITING_FOR_ACCEPTANCE -> "等待对方接收"
    FileTransferStatus.TRANSFERRING -> "传输中"
    FileTransferStatus.COMPLETED -> "已完成"
    FileTransferStatus.REJECTED -> "已拒绝"
    FileTransferStatus.CANCELLED -> "已取消"
    FileTransferStatus.FAILED -> "传输失败"
}

private val MAX_BUBBLE_WIDTH = 560.dp
private val MIN_BUBBLE_WIDTH = 64.dp
private val MIN_BUBBLE_HEIGHT = 48.dp
private val BUBBLE_HORIZONTAL_PADDING = 14.dp
private val BUBBLE_VERTICAL_PADDING = 10.dp
private val STRETCHABLE_BUBBLE_SHAPE = RoundedCornerShape(14.dp)
private val MAX_TRANSFER_PANEL_HEIGHT = 180.dp
private const val MAX_MESSAGE_LENGTH = 8_192
private const val MAX_VISIBLE_TRANSFERS = 3
