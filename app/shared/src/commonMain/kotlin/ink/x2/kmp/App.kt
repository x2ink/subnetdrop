package ink.x2.kmp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ink.x2.kmp.presentation.LanChatViewModel
import ink.x2.kmp.ui.ChatScreen
import ink.x2.kmp.ui.HomeScreen
import ink.x2.kmp.ui.IncomingFileDialog
import ink.x2.kmp.ui.PairingDialog

@Composable
fun App(viewModel: LanChatViewModel = koinViewModel()) {
    val ui = rememberAppUiState(viewModel)
    val snackbarHostState = remember { SnackbarHostState() }
    ui.notice?.let { notice ->
        LaunchedEffect(notice) {
            snackbarHostState.showSnackbar(notice.message)
            viewModel.clearNotice()
        }
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) { contentPadding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding)) {
                if (maxWidth >= WIDE_LAYOUT_MIN_WIDTH) {
                    val sidebarWidth = (maxWidth * SIDEBAR_WIDTH_FRACTION).coerceIn(
                        MIN_SIDEBAR_WIDTH,
                        MAX_SIDEBAR_WIDTH,
                    )
                    WideContent(ui, viewModel, sidebarWidth)
                } else {
                    CompactContent(ui, viewModel)
                }
                val candidate = ui.candidates.firstOrNull()
                val fileOffer = ui.incomingFileOffers.firstOrNull()
                candidate?.let {
                    PairingDialog(
                        candidate = it,
                        onConfirm = { viewModel.confirmPairing(it) },
                        onDismiss = { viewModel.dismissPairing(it.identity.deviceId) },
                    )
                } ?: fileOffer?.let {
                    IncomingFileDialog(
                        offer = it,
                        onAccept = { viewModel.acceptFile(it.transferId) },
                        onReject = { viewModel.rejectFile(it.transferId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WideContent(ui: AppUiState, viewModel: LanChatViewModel, sidebarWidth: Dp) {
    LaunchedEffect(ui.selection, ui.conversations) {
        if (ui.selection == null) {
            ui.conversations.firstOrNull()?.let(viewModel::openConversation)
        }
    }
    Row(Modifier.fillMaxSize()) {
        HomeScreen(
            state = ui,
            modifier = Modifier.width(sidebarWidth),
            onSectionSelected = viewModel::selectSection,
            onPeerSelected = viewModel::openPeer,
            onConversationSelected = viewModel::openConversation,
            onRetry = viewModel::retryRuntime,
            onDisplayNameChanged = viewModel::updateDisplayName,
        )
        VerticalDivider(Modifier.width(1.dp))
        ChatScreen(
            selection = ui.selection,
            messages = ui.messages,
            modifier = Modifier.weight(1f),
            showBack = false,
            onBack = viewModel::closeChat,
            onSend = viewModel::send,
            onRetryMessage = viewModel::retry,
            transfers = ui.fileTransfers,
            onSendFile = viewModel::sendFile,
            onCancelFile = viewModel::cancelFile,
            onFilePickerError = viewModel::reportFilePickerError,
        )
    }
}

@Composable
private fun CompactContent(ui: AppUiState, viewModel: LanChatViewModel) {
    if (ui.selection == null) {
        HomeScreen(
            state = ui,
            modifier = Modifier.fillMaxSize(),
            onSectionSelected = viewModel::selectSection,
            onPeerSelected = viewModel::openPeer,
            onConversationSelected = viewModel::openConversation,
            onRetry = viewModel::retryRuntime,
            onDisplayNameChanged = viewModel::updateDisplayName,
        )
    } else {
        ChatScreen(
            selection = ui.selection,
            messages = ui.messages,
            modifier = Modifier.fillMaxSize(),
            showBack = true,
            onBack = viewModel::closeChat,
            onSend = viewModel::send,
            onRetryMessage = viewModel::retry,
            transfers = ui.fileTransfers,
            onSendFile = viewModel::sendFile,
            onCancelFile = viewModel::cancelFile,
            onFilePickerError = viewModel::reportFilePickerError,
        )
    }
}

@Composable
private fun rememberAppUiState(viewModel: LanChatViewModel): AppUiState {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val identity by viewModel.localIdentity.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val incomingFileOffers by viewModel.incomingFileOffers.collectAsStateWithLifecycle()
    val fileTransfers by viewModel.fileTransfers.collectAsStateWithLifecycle()
    return AppUiState(
        peers = peers,
        conversations = conversations,
        candidates = candidates,
        runtimeState = runtimeState,
        localDeviceId = identity?.deviceId,
        localDisplayName = identity?.displayName,
        selection = selection,
        messages = messages,
        notice = notice,
        section = section,
        incomingFileOffers = incomingFileOffers,
        fileTransfers = fileTransfers,
    )
}

private val WIDE_LAYOUT_MIN_WIDTH = 840.dp
private val MIN_SIDEBAR_WIDTH = 320.dp
private val MAX_SIDEBAR_WIDTH = 440.dp
private const val SIDEBAR_WIDTH_FRACTION = 0.34f
