package ink.x2.subnetdrop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import io.github.vinceglb.filekit.exists
import ink.x2.subnetdrop.domain.model.DeliveryStatus
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.domain.model.LocalFile
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.port.FileTransferService
import ink.x2.subnetdrop.presentation.ChatSelection
import ink.x2.subnetdrop.resources.AppString
import ink.x2.subnetdrop.resources.appString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    selection: ChatSelection?,
    messages: List<Message>,
    modifier: Modifier,
    showBack: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onRetryMessage: (Message) -> Unit,
    storedFileMessages: List<FileTransfer>,
    transfers: List<FileTransfer>,
    maxFileSizeBytes: Long,
    onSendFiles: (List<LocalFile>) -> Unit,
    onCancelFile: (String) -> Unit,
    onFilePickerError: (String) -> Unit,
    peers: List<Peer>,
    onForwardMessages: (List<Message>, Peer) -> Unit,
    onDeleteMessages: (List<Message>) -> Unit,
) {
    if (selection == null) {
        EmptyChat(modifier)
        return
    }
    val launchFilePicker = rememberFilePickerLauncher(maxFileSizeBytes, onSendFiles, onFilePickerError)
    val inputMessages = fileInputMessages()
    val timelineListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var isFileDragActive by remember { mutableStateOf(false) }
    var actionMessageId by remember(selection.conversationId) { mutableStateOf<String?>(null) }
    var partialSelectionMessageId by remember(selection.conversationId) { mutableStateOf<String?>(null) }
    var multiSelectActive by remember(selection.conversationId) { mutableStateOf(false) }
    var selectedMessageIds by remember(selection.conversationId) { mutableStateOf(emptySet<String>()) }
    var forwardQueue by remember(selection.conversationId) { mutableStateOf(emptyList<Message>()) }
    var deleteQueue by remember(selection.conversationId) { mutableStateOf(emptyList<Message>()) }
    val selectedMessages = remember(messages, selectedMessageIds) {
        messages.filter { it.id in selectedMessageIds }
    }
    LaunchedEffect(messages) {
        val availableIds = messages.mapTo(mutableSetOf(), Message::id)
        selectedMessageIds = selectedMessageIds.intersect(availableIds)
        if (actionMessageId !in availableIds) actionMessageId = null
        if (partialSelectionMessageId !in availableIds) partialSelectionMessageId = null
    }
    val openFileFailed = appString(AppString.OPEN_FILE_FAILED)
    val openFile = { transfer: FileTransfer ->
        runCatching {
            FileKit.openFileWithDefaultApplication(PlatformFile(requireNotNull(transfer.localPath)))
        }.onFailure { failure ->
            onFilePickerError(
                inputMessages.withDetail(openFileFailed, failure.message),
            )
        }
        Unit
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .platformFileDropTarget(
                maxFileSizeBytes = maxFileSizeBytes,
                onFilesDropped = onSendFiles,
                onError = { error -> onFilePickerError(inputMessages.forError(error)) },
                onDragActiveChanged = { isFileDragActive = it },
            ),
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            when {
                multiSelectActive -> MessageModeHeader(selectedMessages.size) {
                    multiSelectActive = false
                    selectedMessageIds = emptySet()
                }
                partialSelectionMessageId != null -> MessageModeHeader(selectedCount = null) {
                    partialSelectionMessageId = null
                }
                else -> ChatHeader(selection.peerDisplayName, showBack, onBack)
            }
            ChatTimeline(
                messages = messages,
                storedFileMessages = storedFileMessages,
                transfers = transfers,
                conversationId = selection.conversationId,
                peerId = selection.peerId,
                modifier = Modifier.weight(1f),
                listState = timelineListState,
                onRetryMessage = onRetryMessage,
                onCancelFile = onCancelFile,
                onOpenFile = openFile,
                interactionState = MessageInteractionState(
                    actionMessageId = actionMessageId,
                    partialSelectionMessageId = partialSelectionMessageId,
                    multiSelectActive = multiSelectActive,
                    selectedMessageIds = selectedMessageIds,
                ),
                onActionMenuRequest = { actionMessageId = it.id },
                onActionMenuDismiss = { actionMessageId = null },
                onMessageAction = { message, action ->
                    actionMessageId = null
                    when (action) {
                        MessageAction.COPY -> coroutineScope.launch {
                            clipboard.setClipEntry(plainTextClipEntry(message.body))
                        }
                        MessageAction.FORWARD -> forwardQueue = listOf(message)
                        MessageAction.PARTIAL_SELECT -> partialSelectionMessageId = message.id
                        MessageAction.DELETE -> deleteQueue = listOf(message)
                        MessageAction.MULTI_SELECT -> {
                            multiSelectActive = true
                            selectedMessageIds = setOf(message.id)
                        }
                    }
                },
                onMessageSelectionToggle = { messageId ->
                    selectedMessageIds = selectedMessageIds.toggle(messageId)
                },
            )
            when {
                multiSelectActive -> MultiSelectActionBar(
                    selectedCount = selectedMessages.size,
                    onForward = { forwardQueue = selectedMessages },
                    onDelete = { deleteQueue = selectedMessages },
                )
                partialSelectionMessageId == null -> Composer(
                    onSend = onSend,
                    onAttachFile = launchFilePicker,
                    onInputFocused = {
                        coroutineScope.launch {
                            if (timelineListState.layoutInfo.totalItemsCount > 0) {
                                timelineListState.scrollToItem(0)
                            }
                        }
                    },
                )
            }
        }
        if (isFileDragActive) FileDropOverlay()
    }
    if (forwardQueue.isNotEmpty()) {
        ForwardMessagesDialog(
            messageCount = forwardQueue.size,
            peers = peers,
            onDismiss = { forwardQueue = emptyList() },
            onPeerSelected = { peer ->
                val messagesToForward = forwardQueue
                forwardQueue = emptyList()
                multiSelectActive = false
                selectedMessageIds = emptySet()
                onForwardMessages(messagesToForward, peer)
            },
        )
    }
    if (deleteQueue.isNotEmpty()) {
        DeleteMessagesDialog(
            messageCount = deleteQueue.size,
            onDismiss = { deleteQueue = emptyList() },
            onConfirm = {
                val messagesToDelete = deleteQueue
                deleteQueue = emptyList()
                multiSelectActive = false
                selectedMessageIds = emptySet()
                onDeleteMessages(messagesToDelete)
            },
        )
    }
}

@Composable
private fun FileDropOverlay() {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = appString(AppString.DROP_FILES),
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = appString(
                    AppString.DROP_FILES_LIMIT,
                    FileTransferService.MAX_FILES_PER_BATCH,
                ),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ChatHeader(title: String, showBack: Boolean, onBack: () -> Unit) {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = appString(AppString.CONTENT_BACK),
                    )
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
                        text = appString(AppString.ENCRYPTED_CHAT),
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
private fun ChatTimeline(
    messages: List<Message>,
    storedFileMessages: List<FileTransfer>,
    transfers: List<FileTransfer>,
    conversationId: String,
    peerId: String,
    modifier: Modifier,
    listState: LazyListState,
    onRetryMessage: (Message) -> Unit,
    onCancelFile: (String) -> Unit,
    onOpenFile: (FileTransfer) -> Unit,
    interactionState: MessageInteractionState,
    onActionMenuRequest: (Message) -> Unit,
    onActionMenuDismiss: () -> Unit,
    onMessageAction: (Message, MessageAction) -> Unit,
    onMessageSelectionToggle: (String) -> Unit,
) {
    val timelineItems = remember(messages, storedFileMessages, transfers, conversationId, peerId) {
        buildChatTimeline(messages, storedFileMessages, transfers, conversationId, peerId)
    }
    val displayItems = remember(timelineItems) { timelineItems.asReversed() }
    LaunchedEffect(displayItems.firstOrNull()?.stableKey) {
        if (displayItems.isNotEmpty()) listState.scrollToItem(0)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        items(displayItems, key = ChatTimelineItem::stableKey) { item ->
            when (item) {
                is ChatTimelineItem.TextMessage -> TimelineTextMessage(
                    message = item.message,
                    interactionState = interactionState,
                    onRetryMessage = onRetryMessage,
                    onActionMenuRequest = onActionMenuRequest,
                    onActionMenuDismiss = onActionMenuDismiss,
                    onMessageAction = onMessageAction,
                    onMessageSelectionToggle = onMessageSelectionToggle,
                )
                is ChatTimelineItem.FileMessage -> FileTransferMessage(item.transfer, onCancelFile, onOpenFile)
            }
        }
    }
}

@Composable
private fun TimelineTextMessage(
    message: Message,
    interactionState: MessageInteractionState,
    onRetryMessage: (Message) -> Unit,
    onActionMenuRequest: (Message) -> Unit,
    onActionMenuDismiss: () -> Unit,
    onMessageAction: (Message, MessageAction) -> Unit,
    onMessageSelectionToggle: (String) -> Unit,
) {
    if (interactionState.multiSelectActive) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MessageSelectionIndicator(
                selected = message.id in interactionState.selectedMessageIds,
                onClick = { onMessageSelectionToggle(message.id) },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onMessageSelectionToggle(message.id) },
            ) {
                MessageBubble(
                    message = message,
                    onRetryMessage = onRetryMessage,
                    interactionState = interactionState,
                    onActionMenuRequest = onActionMenuRequest,
                    onActionMenuDismiss = onActionMenuDismiss,
                    onMessageAction = onMessageAction,
                )
            }
        }
    } else {
        MessageBubble(
            message = message,
            onRetryMessage = onRetryMessage,
            interactionState = interactionState,
            onActionMenuRequest = onActionMenuRequest,
            onActionMenuDismiss = onActionMenuDismiss,
            onMessageAction = onMessageAction,
        )
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    onRetryMessage: (Message) -> Unit,
    interactionState: MessageInteractionState,
    onActionMenuRequest: (Message) -> Unit,
    onActionMenuDismiss: () -> Unit,
    onMessageAction: (Message, MessageAction) -> Unit,
) {
    val outgoing = message.direction == MessageDirection.OUTGOING
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = MIN_BUBBLE_WIDTH, max = MAX_BUBBLE_WIDTH)
                        .heightIn(min = MIN_BUBBLE_HEIGHT)
                        .messageInteraction(
                            enabled = interactionState.partialSelectionMessageId == null &&
                                !interactionState.multiSelectActive,
                            longClickLabel = appString(AppString.MESSAGE_ACTIONS),
                            onActionMenuRequest = { onActionMenuRequest(message) },
                        ),
                    shape = MessageBubbleShape(pointingLeft = !outgoing),
                    color = if (outgoing) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(
                            start = if (outgoing) {
                                BUBBLE_HORIZONTAL_PADDING
                            } else {
                                BUBBLE_HORIZONTAL_PADDING + 8.dp
                            },
                            end = if (outgoing) {
                                BUBBLE_HORIZONTAL_PADDING + 8.dp
                            } else {
                                BUBBLE_HORIZONTAL_PADDING
                            },
                            top = BUBBLE_VERTICAL_PADDING,
                            bottom = BUBBLE_VERTICAL_PADDING,
                        ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        MessageBody(message, outgoing, interactionState.partialSelectionMessageId == message.id)
                    }
                }
                if (outgoing) {
                    DeliveryState(
                        message = message,
                        retryEnabled = !interactionState.multiSelectActive,
                        onRetryMessage = onRetryMessage,
                    )
                }
            }
            MessageActionMenu(
                expanded = interactionState.actionMessageId == message.id,
                onDismiss = onActionMenuDismiss,
                onAction = { action -> onMessageAction(message, action) },
            )
        }
    }
}

@Composable
private fun MessageBody(message: Message, outgoing: Boolean, textSelectionEnabled: Boolean) {
    val content: @Composable () -> Unit = {
        Text(
            text = message.body,
            style = MaterialTheme.typography.bodyLarge,
            color = if (outgoing) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
    if (textSelectionEnabled) {
        SelectionContainer { content() }
    } else {
        content()
    }
}

private fun Modifier.messageInteraction(
    enabled: Boolean,
    longClickLabel: String,
    onActionMenuRequest: () -> Unit,
): Modifier = if (enabled) {
    platformSecondaryClick(onActionMenuRequest)
        .combinedClickable(
            onClick = {},
            onLongClickLabel = longClickLabel,
            onLongClick = onActionMenuRequest,
        )
} else {
    this
}

@Composable
private fun DeliveryState(
    message: Message,
    retryEnabled: Boolean,
    onRetryMessage: (Message) -> Unit,
) {
    val failed = message.status == DeliveryStatus.FAILED
    Row(
        modifier = Modifier
            .padding(top = 4.dp, end = 4.dp)
            .then(if (failed && retryEnabled) Modifier.clickable { onRetryMessage(message) } else Modifier),
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
            text = if (failed) appString(AppString.SEND_FAILED_RETRY) else message.status.label(),
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
private fun FileTransferMessage(
    transfer: FileTransfer,
    onCancelFile: (String) -> Unit,
    onOpenFile: (FileTransfer) -> Unit,
) {
    val outgoing = transfer.direction == FileTransferDirection.OUTGOING
    val coroutineScope = rememberCoroutineScope()
    var localFileExists by remember(transfer.id, transfer.localPath, transfer.status) {
        mutableStateOf<Boolean?>(null)
    }
    LaunchedEffect(transfer.id, transfer.localPath, transfer.status) {
        localFileExists = transfer.localPath?.let { doesLocalFileExist(it) } ?: false
    }
    val cancellable = transfer.status == FileTransferStatus.PREPARING ||
        transfer.status == FileTransferStatus.WAITING_FOR_ACCEPTANCE ||
        transfer.status == FileTransferStatus.TRANSFERRING
    val expired = isFileMessageExpired(transfer, localFileExists)
    val canOpen = transfer.localPath != null &&
        (outgoing || transfer.status == FileTransferStatus.COMPLETED) &&
        transfer.status != FileTransferStatus.REJECTED && transfer.status != FileTransferStatus.CANCELLED
    val openFile = {
        coroutineScope.launch {
            val exists = transfer.localPath?.let { doesLocalFileExist(it) } ?: false
            localFileExists = exists
            if (exists) onOpenFile(transfer)
        }
        Unit
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = MAX_FILE_MESSAGE_WIDTH)
                .then(if (canOpen) Modifier.clickable(onClick = openFile) else Modifier),
            shape = MessageBubbleShape(pointingLeft = !outgoing),
            color = if (outgoing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Row(
                modifier = Modifier.padding(
                    start = if (outgoing) {
                        BUBBLE_HORIZONTAL_PADDING
                    } else {
                        BUBBLE_HORIZONTAL_PADDING + 8.dp
                    },
                    end = if (outgoing) {
                        BUBBLE_HORIZONTAL_PADDING + 8.dp
                    } else {
                        BUBBLE_HORIZONTAL_PADDING
                    },
                    top = BUBBLE_VERTICAL_PADDING,
                    bottom = BUBBLE_VERTICAL_PADDING,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(
                    Modifier
                        .padding(start = 12.dp)
                        .widthIn(max = MAX_FILE_CONTENT_WIDTH)
                        .width(IntrinsicSize.Max),
                ) {
                    Text(transfer.fileName, maxLines = 2, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = transfer.summary(expired),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (expired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (cancellable) {
                        LinearProgressIndicator(
                            progress = { transfer.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        )
                    }
                    transfer.error?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (cancellable) {
                    IconButton(onClick = { onCancelFile(transfer.id) }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = appString(AppString.CANCEL_FILE_TRANSFER),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    onSend: (String) -> Unit,
    onAttachFile: () -> Unit,
    onInputFocused: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val send = {
        text.trim().takeIf(String::isNotEmpty)?.let(onSend)
        text = ""
    }
    Surface(color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = onAttachFile, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = appString(AppString.SEND_FILE),
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_MESSAGE_LENGTH) text = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 132.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused) onInputFocused()
                    },
                placeholder = { Text(appString(AppString.MESSAGE_PLACEHOLDER)) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
            )
            FilledIconButton(
                onClick = send,
                modifier = Modifier.size(52.dp),
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = appString(AppString.SEND_MESSAGE),
                )
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
                    text = appString(AppString.SELECT_PAIRED_DEVICE),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = appString(AppString.START_SECURE_CHAT),
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

@Composable
private fun DeliveryStatus.label(): String = when (this) {
    DeliveryStatus.PENDING -> appString(AppString.DELIVERY_PENDING)
    DeliveryStatus.SENDING -> appString(AppString.DELIVERY_SENDING)
    DeliveryStatus.SENT -> appString(AppString.DELIVERY_SENT)
    DeliveryStatus.DELIVERED -> appString(AppString.DELIVERY_UNREAD)
    DeliveryStatus.READ -> appString(AppString.DELIVERY_READ)
    DeliveryStatus.FAILED -> appString(AppString.DELIVERY_FAILED)
}

@Composable
private fun FileTransferStatus.label(): String = when (this) {
    FileTransferStatus.PREPARING -> appString(AppString.FILE_PREPARING)
    FileTransferStatus.WAITING_FOR_ACCEPTANCE -> appString(AppString.FILE_WAITING_FOR_ACCEPTANCE)
    FileTransferStatus.TRANSFERRING -> appString(AppString.FILE_TRANSFERRING)
    FileTransferStatus.COMPLETED -> appString(AppString.FILE_COMPLETED)
    FileTransferStatus.REJECTED -> appString(AppString.FILE_REJECTED)
    FileTransferStatus.CANCELLED -> appString(AppString.FILE_CANCELLED)
    FileTransferStatus.FAILED -> appString(AppString.FILE_FAILED)
}

@Composable
private fun FileTransfer.summary(expired: Boolean): String = if (expired) {
    appString(AppString.FILE_EXPIRED_SUMMARY, formatFileSize(size))
} else {
    when (status) {
        FileTransferStatus.PREPARING,
        FileTransferStatus.WAITING_FOR_ACCEPTANCE,
        FileTransferStatus.TRANSFERRING,
        -> appString(
            AppString.FILE_PROGRESS_SUMMARY,
            status.label(),
            formatFileSize(transferredBytes),
            formatFileSize(size),
        )
        FileTransferStatus.COMPLETED,
        FileTransferStatus.REJECTED,
        FileTransferStatus.CANCELLED,
        FileTransferStatus.FAILED,
        -> appString(AppString.FILE_TERMINAL_SUMMARY, status.label(), formatFileSize(size))
    }
}

internal fun isFileMessageExpired(transfer: FileTransfer, localFileExists: Boolean?): Boolean =
    transfer.status == FileTransferStatus.COMPLETED && localFileExists == false

private suspend fun doesLocalFileExist(path: String): Boolean = withContext(Dispatchers.IO) {
    try {
        PlatformFile(path).exists()
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        false
    }
}

internal sealed interface ChatTimelineItem {
    val createdAt: Long
    val stableKey: String

    data class TextMessage(val message: Message) : ChatTimelineItem {
        override val createdAt: Long = message.createdAt
        override val stableKey: String = "message:${message.id}"
    }

    data class FileMessage(val transfer: FileTransfer) : ChatTimelineItem {
        override val createdAt: Long = transfer.createdAt
        override val stableKey: String = "file:${transfer.id}"
    }
}

private data class MessageInteractionState(
    val actionMessageId: String?,
    val partialSelectionMessageId: String?,
    val multiSelectActive: Boolean,
    val selectedMessageIds: Set<String>,
)

internal fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

internal fun buildChatTimeline(
    messages: List<Message>,
    storedFileMessages: List<FileTransfer>,
    transfers: List<FileTransfer>,
    conversationId: String,
    peerId: String,
): List<ChatTimelineItem> = buildList {
    messages
        .filter { it.conversationId == conversationId }
        .forEach { add(ChatTimelineItem.TextMessage(it)) }
    val liveTransfers = transfers.filter { it.conversationId == conversationId && it.peerId == peerId }
    val liveTransferIds = liveTransfers.mapTo(mutableSetOf(), FileTransfer::id)
    storedFileMessages
        .filter { it.conversationId == conversationId && it.peerId == peerId && it.id !in liveTransferIds }
        .forEach { add(ChatTimelineItem.FileMessage(it)) }
    liveTransfers.forEach { add(ChatTimelineItem.FileMessage(it)) }
}.sortedWith(compareBy<ChatTimelineItem>(ChatTimelineItem::createdAt).thenBy(ChatTimelineItem::stableKey))

class MessageBubbleShape(
    private val pointingLeft: Boolean,
    private val cornerRadius: Dp = 8.dp,
    private val tailWidth: Dp = 8.dp,
    private val tailHeight: Dp = 16.dp,
    private val tailTop: Dp = 16.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {

        return with(density) {
            val radius = cornerRadius.toPx()
            val tailW = tailWidth.toPx()
            val tailH = tailHeight.toPx()
            val tailY = tailTop.toPx()
            val path = Path()
            if (pointingLeft) {
                path.moveTo(tailW + radius, 0f)
                path.lineTo(size.width - radius, 0f)
                path.quadraticBezierTo(
                    size.width,
                    0f,
                    size.width,
                    radius
                )
                path.lineTo(
                    size.width,
                    size.height - radius
                )
                path.quadraticBezierTo(
                    size.width,
                    size.height,
                    size.width - radius,
                    size.height
                )
                path.lineTo(
                    tailW + radius,
                    size.height
                )
                path.quadraticBezierTo(
                    tailW,
                    size.height,
                    tailW,
                    size.height - radius
                )
                path.lineTo(
                    tailW,
                    tailY + tailH
                )
                path.lineTo(
                    0f,
                    tailY + tailH / 2f
                )
                path.lineTo(
                    tailW,
                    tailY
                )
                path.lineTo(
                    tailW,
                    radius
                )
                path.quadraticBezierTo(
                    tailW,
                    0f,
                    tailW + radius,
                    0f
                )
            } else {
                path.moveTo(radius, 0f)
                path.lineTo(
                    size.width - tailW - radius,
                    0f
                )
                path.quadraticBezierTo(
                    size.width - tailW,
                    0f,
                    size.width - tailW,
                    radius
                )
                path.lineTo(
                    size.width - tailW,
                    tailY
                )
                path.lineTo(
                    size.width,
                    tailY + tailH / 2f
                )
                path.lineTo(
                    size.width - tailW,
                    tailY + tailH
                )
                path.lineTo(
                    size.width - tailW,
                    size.height - radius
                )
                path.quadraticBezierTo(
                    size.width - tailW,
                    size.height,
                    size.width - tailW - radius,
                    size.height
                )
                path.lineTo(
                    radius,
                    size.height
                )
                path.quadraticBezierTo(
                    0f,
                    size.height,
                    0f,
                    size.height - radius
                )
                path.lineTo(
                    0f,
                    radius
                )
                path.quadraticBezierTo(
                    0f,
                    0f,
                    radius,
                    0f
                )
            }
            path.close()
            Outline.Generic(path)
        }
    }
}




private val MAX_BUBBLE_WIDTH = 560.dp
private val MAX_FILE_MESSAGE_WIDTH = 440.dp
private val MAX_FILE_CONTENT_WIDTH = 320.dp
private val MIN_BUBBLE_WIDTH = 64.dp
private val MIN_BUBBLE_HEIGHT = 48.dp
private val BUBBLE_HORIZONTAL_PADDING = 14.dp
private val BUBBLE_VERTICAL_PADDING = 10.dp
private val STRETCHABLE_BUBBLE_SHAPE = RoundedCornerShape(8.dp)
private const val MAX_MESSAGE_LENGTH = 8_192
