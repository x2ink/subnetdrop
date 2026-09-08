package ink.x2.subnetdrop.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ink.x2.subnetdrop.domain.model.LocalFile
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.MessageDirection
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.domain.model.conversationIdFor
import ink.x2.subnetdrop.domain.port.FileTransferService
import ink.x2.subnetdrop.domain.port.FileTransferSettingsRepository
import ink.x2.subnetdrop.domain.port.PairingCandidate
import ink.x2.subnetdrop.domain.port.PairingService
import ink.x2.subnetdrop.domain.usecase.MarkConversationReadUseCase
import ink.x2.subnetdrop.domain.usecase.ObserveFileMessagesUseCase
import ink.x2.subnetdrop.domain.usecase.ObserveMessagesUseCase
import ink.x2.subnetdrop.domain.usecase.ObservePeersUseCase
import ink.x2.subnetdrop.domain.usecase.SendMessageUseCase
import ink.x2.subnetdrop.resources.AppString
import ink.x2.subnetdrop.resources.LocalizedText
import ink.x2.subnetdrop.runtime.SubnetDropRuntime
import ink.x2.subnetdrop.runtime.RuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SubnetDropViewModel(
    private val runtime: SubnetDropRuntime,
    observePeers: ObservePeersUseCase,
    private val observeMessages: ObserveMessagesUseCase,
    private val observeFileMessages: ObserveFileMessagesUseCase,
    private val sendMessage: SendMessageUseCase,
    private val markConversationRead: MarkConversationReadUseCase,
    private val pairingService: PairingService,
    private val fileTransferService: FileTransferService,
    private val fileTransferSettingsRepository: FileTransferSettingsRepository,
) : ViewModel() {
    private val mutableSelection = MutableStateFlow<ChatSelection?>(null)
    private val mutableNotice = MutableStateFlow<UiNotice?>(null)
    private val mutableSection = MutableStateFlow(HomeSection.NEARBY)

    val peers = observePeers().toUiState(emptyList())
    val candidates: StateFlow<List<PairingCandidate>> = pairingService.candidates
    val runtimeState: StateFlow<RuntimeState> = runtime.state
    val localProfile = runtime.profile
    val selection = mutableSelection.asStateFlow()
    val notice = mutableNotice.asStateFlow()
    val section = mutableSection.asStateFlow()
    val incomingFileOffers = fileTransferService.incomingOffers
    val fileTransfers = fileTransferService.transfers
    val fileTransferSettings = fileTransferSettingsRepository.settings
    val messages = mutableSelection
        .flatMapLatest { selected ->
            selected?.let { observeMessages(it.conversationId) } ?: emptyFlow()
        }
        .toUiState(emptyList())
    val storedFileMessages = mutableSelection
        .flatMapLatest { selected ->
            selected?.let { observeFileMessages(it.conversationId) } ?: emptyFlow()
        }
        .toUiState(emptyList())

    init {
        observeUnreadMessages()
    }

    fun selectSection(section: HomeSection) {
        mutableSection.value = section
    }

    fun openPeer(peer: Peer) {
        if (peer.trustState != TrustState.TRUSTED) {
            requestPairing(peer.id)
            return
        }
        val localDeviceId = localProfile.value?.deviceId ?: return showError(AppString.LOCAL_PROFILE_NOT_READY)
        mutableSelection.value = ChatSelection(
            conversationId = conversationIdFor(localDeviceId, peer.id),
            peerId = peer.id,
            peerDisplayName = peer.displayName,
        )
    }

    fun closeChat() {
        mutableSelection.value = null
    }

    fun requestPairing(peerId: String) = launchAction(AppString.PAIR_REQUEST_FAILED) {
        pairingService.requestPairing(peerId)
    }

    fun confirmPairing(candidate: PairingCandidate) = launchAction(AppString.PAIR_CONFIRM_FAILED) {
        pairingService.confirmPairing(candidate.identity.deviceId)
        openTrustedIdentity(candidate)
        showMessage(AppString.TRUST_ESTABLISHED)
    }

    fun dismissPairing(peerId: String) {
        launchAction(AppString.PAIR_CANCEL_FAILED) {
            pairingService.dismissPairing(peerId)
        }
    }

    fun send(body: String) {
        val selected = selection.value ?: return showError(AppString.SELECT_CONTACT_FIRST)
        val senderId = localProfile.value?.deviceId ?: return showError(AppString.LOCAL_PROFILE_NOT_READY)
        launchAction(AppString.MESSAGE_SEND_FAILED) {
            sendMessage(selected.conversationId, senderId, selected.peerId, body).getOrThrow()
        }
    }

    fun retry(message: Message) = launchAction(AppString.MESSAGE_RETRY_FAILED) {
        sendMessage.retry(message).getOrThrow()
    }

    fun updateDisplayName(displayName: String) = launchAction(AppString.DISPLAY_NAME_UPDATE_FAILED) {
        runtime.updateDisplayName(displayName)
        showMessage(AppString.DISPLAY_NAME_UPDATED)
    }

    fun retryRuntime() = launchAction(AppString.RUNTIME_START_FAILED) {
        runtime.start()
    }

    fun refreshPeers() = launchAction(AppString.PEERS_REFRESH_FAILED) {
        runtime.refreshDiscovery()
        showMessage(AppString.PEERS_REFRESH_STARTED)
    }

    fun deletePeer(peerId: String, deleteHistory: Boolean) = launchAction(AppString.DELETE_DEVICE_FAILED) {
        pairingService.dismissPairing(peerId)
        fileTransferService.clearPeerTransfers(peerId)
        runtime.deletePeer(peerId, deleteHistory)
        if (selection.value?.peerId == peerId) closeChat()
        showMessage(AppString.DEVICE_DELETED)
    }

    fun sendFiles(files: List<LocalFile>) {
        if (files.isEmpty()) return
        val selected = selection.value ?: return showError(AppString.SELECT_CONTACT_FIRST)
        launchAction(AppString.FILE_SEND_FAILED) {
            showMessage(AppString.FILES_STARTED, files.size)
            fileTransferService.sendFiles(selected.peerId, files)
            showMessage(AppString.FILES_FINISHED, files.size)
        }
    }

    fun acceptFile(transferId: String) = launchAction(AppString.FILE_ACCEPT_FAILED) {
        fileTransferService.acceptOffer(transferId)
    }

    fun rejectFile(transferId: String) = launchAction(AppString.FILE_REJECT_FAILED) {
        fileTransferService.rejectOffer(transferId)
    }

    fun cancelFile(transferId: String) = launchAction(AppString.FILE_CANCEL_FAILED) {
        fileTransferService.cancelTransfer(transferId)
    }

    fun updateSaveDirectory(path: String) = launchAction(AppString.SAVE_DIRECTORY_UPDATE_FAILED) {
        fileTransferSettingsRepository.updateSaveDirectory(path)
        showMessage(AppString.SAVE_DIRECTORY_UPDATED)
    }

    fun updateIncomingFileConfirmation(required: Boolean) = launchAction(AppString.RECEIVE_SETTING_UPDATE_FAILED) {
        fileTransferSettingsRepository.updateRequireIncomingConfirmation(required)
    }

    fun updateMaxFileSize(maxFileSizeBytes: Long) = launchAction(AppString.MAX_FILE_SIZE_UPDATE_FAILED) {
        fileTransferSettingsRepository.updateMaxFileSizeBytes(maxFileSizeBytes)
        showMessage(AppString.MAX_FILE_SIZE_UPDATED)
    }

    fun reportFilePickerError(message: String) {
        showError(message)
    }

    fun clearNotice() {
        mutableNotice.value = null
    }

    private fun openTrustedIdentity(candidate: PairingCandidate) {
        val localDeviceId = localProfile.value?.deviceId ?: return
        mutableSelection.value = ChatSelection(
            conversationId = conversationIdFor(localDeviceId, candidate.identity.deviceId),
            peerId = candidate.identity.deviceId,
            peerDisplayName = candidate.identity.displayName,
        )
    }

    private fun observeUnreadMessages() {
        viewModelScope.launch {
            combine(mutableSelection, messages) { selected, currentMessages ->
                selected?.let { selection ->
                    ReadRequest(
                        conversationId = selection.conversationId,
                        peerId = selection.peerId,
                        messageIds = currentMessages
                            .filter { message ->
                                message.conversationId == selection.conversationId &&
                                    message.direction == MessageDirection.INCOMING &&
                                    !message.isRead
                            }
                            .map(Message::id),
                    )
                }
            }
                .distinctUntilChanged()
                .collect { request ->
                    if (request == null || request.messageIds.isEmpty()) return@collect
                    markConversationRead(request.conversationId, request.peerId)
                        .onFailure { showError(AppString.READ_RECEIPT_FAILED, it.message) }
                }
        }
    }

    private fun launchAction(errorMessage: AppString, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showError(errorMessage, exception.message)
            }
        }
    }

    private fun showMessage(message: AppString, vararg formatArgs: Any) {
        mutableNotice.value = UiNotice(
            text = LocalizedText.Resource(message, formatArgs.toList()),
            isError = false,
        )
    }

    private fun showError(message: AppString, detail: String? = null) {
        mutableNotice.value = UiNotice(
            text = LocalizedText.Resource(message, detail = detail),
            isError = true,
        )
    }

    private fun showError(message: String) {
        mutableNotice.value = UiNotice(LocalizedText.Raw(message), isError = true)
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.toUiState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class ChatSelection(
    val conversationId: String,
    val peerId: String,
    val peerDisplayName: String,
)

data class UiNotice(
    val text: LocalizedText,
    val isError: Boolean,
)

enum class HomeSection {
    NEARBY,
    SETTINGS,
}

private data class ReadRequest(
    val conversationId: String,
    val peerId: String,
    val messageIds: List<String>,
)
