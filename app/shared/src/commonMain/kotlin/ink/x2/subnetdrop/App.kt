package ink.x2.subnetdrop

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import ink.x2.subnetdrop.presentation.ChatSelection
import ink.x2.subnetdrop.presentation.SubnetDropViewModel
import ink.x2.subnetdrop.ui.ChatScreen
import ink.x2.subnetdrop.ui.HomeScreen
import ink.x2.subnetdrop.ui.IncomingFileDialog
import ink.x2.subnetdrop.ui.PairingDialog
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(viewModel: SubnetDropViewModel = koinViewModel()) {
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
            containerColor = Color.White,
        ) { contentPadding ->
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
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
private fun WideContent(ui: AppUiState, viewModel: SubnetDropViewModel, sidebarWidth: Dp) {
    Row(Modifier.fillMaxSize()) {
        HomeScreen(
            state = ui,
            modifier = Modifier.width(sidebarWidth),
            onSectionSelected = viewModel::selectSection,
            onPeerSelected = viewModel::openPeer,
            onRetry = viewModel::retryRuntime,
            onDisplayNameChanged = viewModel::updateDisplayName,
            onSaveDirectoryChanged = viewModel::updateSaveDirectory,
            onIncomingFileConfirmationChanged = viewModel::updateIncomingFileConfirmation,
            onSettingsError = viewModel::reportFilePickerError,
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
private fun CompactContent(ui: AppUiState, viewModel: SubnetDropViewModel) {
    val backStack = remember { mutableStateListOf<Any>(HomeRoute) }
    val latestUi = rememberUpdatedState(ui)
    LaunchedEffect(ui.selection) {
        syncCompactBackStack(backStack, ui.selection?.let(::ChatRoute))
    }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { navigateHome(backStack, viewModel) },
        popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        predictivePopTransitionSpec = { _ -> EnterTransition.None togetherWith ExitTransition.None },
        entryProvider = { route ->
            when (route) {
                HomeRoute -> NavEntry(route) {
                    HomeScreen(
                        state = latestUi.value,
                        modifier = Modifier.fillMaxSize(),
                        onSectionSelected = viewModel::selectSection,
                        onPeerSelected = viewModel::openPeer,
                        onRetry = viewModel::retryRuntime,
                        onDisplayNameChanged = viewModel::updateDisplayName,
                        onSaveDirectoryChanged = viewModel::updateSaveDirectory,
                        onIncomingFileConfirmationChanged = viewModel::updateIncomingFileConfirmation,
                        onSettingsError = viewModel::reportFilePickerError,
                    )
                }
                is ChatRoute -> NavEntry(route) {
                    ChatScreen(
                        selection = route.selection,
                        messages = latestUi.value.messages,
                        modifier = Modifier.fillMaxSize(),
                        showBack = true,
                        onBack = { navigateHome(backStack, viewModel) },
                        onSend = viewModel::send,
                        onRetryMessage = viewModel::retry,
                        transfers = latestUi.value.fileTransfers,
                        onSendFile = viewModel::sendFile,
                        onCancelFile = viewModel::cancelFile,
                        onFilePickerError = viewModel::reportFilePickerError,
                    )
                }
                else -> error("Unknown navigation route: $route")
            }
        },
    )
}

private fun syncCompactBackStack(backStack: SnapshotStateList<Any>, route: ChatRoute?) {
    while (backStack.size > 1) backStack.removeLast()
    route?.let(backStack::add)
}

private fun navigateHome(backStack: SnapshotStateList<Any>, viewModel: SubnetDropViewModel) {
    while (backStack.size > 1) backStack.removeLast()
    viewModel.closeChat()
}

private data object HomeRoute

private data class ChatRoute(val selection: ChatSelection)

@Composable
private fun rememberAppUiState(viewModel: SubnetDropViewModel): AppUiState {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val profile by viewModel.localProfile.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val incomingFileOffers by viewModel.incomingFileOffers.collectAsStateWithLifecycle()
    val fileTransfers by viewModel.fileTransfers.collectAsStateWithLifecycle()
    val fileTransferSettings by viewModel.fileTransferSettings.collectAsStateWithLifecycle()
    return AppUiState(
        peers = peers,
        candidates = candidates,
        runtimeState = runtimeState,
        localDeviceId = profile?.deviceId,
        localDisplayName = profile?.displayName,
        selection = selection,
        messages = messages,
        notice = notice,
        section = section,
        incomingFileOffers = incomingFileOffers,
        fileTransfers = fileTransfers,
        fileTransferSettings = fileTransferSettings,
    )
}

private val WIDE_LAYOUT_MIN_WIDTH = 840.dp
private val MIN_SIDEBAR_WIDTH = 320.dp
private val MAX_SIDEBAR_WIDTH = 440.dp
private const val SIDEBAR_WIDTH_FRACTION = 0.34f
