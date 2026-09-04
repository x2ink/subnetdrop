package ink.x2.kmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ink.x2.kmp.domain.model.Conversation
import ink.x2.kmp.domain.model.LocalFile
import ink.x2.kmp.domain.model.Message
import ink.x2.kmp.domain.model.MessageDirection
import ink.x2.kmp.domain.model.Peer
import ink.x2.kmp.domain.model.TrustState
import ink.x2.kmp.domain.model.conversationIdFor
import ink.x2.kmp.domain.port.FileTransferService
import ink.x2.kmp.domain.port.PairingCandidate
import ink.x2.kmp.domain.port.PairingService
import ink.x2.kmp.domain.usecase.MarkConversationReadUseCase
import ink.x2.kmp.domain.usecase.ObserveConversationsUseCase
import ink.x2.kmp.domain.usecase.ObserveMessagesUseCase
import ink.x2.kmp.domain.usecase.ObservePeersUseCase
import ink.x2.kmp.domain.usecase.SendMessageUseCase
import ink.x2.kmp.runtime.LanChatRuntime
import ink.x2.kmp.runtime.RuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LanChatViewModel(
    private val runtime: LanChatRuntime,
    observePeers: ObservePeersUseCase,
    observeConversations: ObserveConversationsUseCase,
    private val observeMessages: ObserveMessagesUseCase,
    private val sendMessage: SendMessageUseCase,
    private val markConversationRead: MarkConversationReadUseCase,
    private val pairingService: PairingService,
    private val fileTransferService: FileTransferService,
) : ViewModel() {
    private val mutableSelection = MutableStateFlow<ChatSelection?>(null)
    private val mutableNotice = MutableStateFlow<UiNotice?>(null)
    private val mutableSection = MutableStateFlow(HomeSection.NEARBY)

    val peers = observePeers().toUiState(emptyList())
    val conversations = observeConversations().toUiState(emptyList())
    val candidates: StateFlow<List<PairingCandidate>> = pairingService.candidates
    val runtimeState: StateFlow<RuntimeState> = runtime.state
    val localIdentity = runtime.identity
    val selection = mutableSelection.asStateFlow()
    val notice = mutableNotice.asStateFlow()
    val section = mutableSection.asStateFlow()
    val incomingFileOffers = fileTransferService.incomingOffers
    val fileTransfers = fileTransferService.transfers
    val messages = mutableSelection
        .flatMapLatest { selected ->
            selected?.let { observeMessages(it.conversationId) } ?: flowOf(emptyList())
        }
        .toUiState(emptyList())

    init {
        observeUnreadMessages()
    }

    fun selectSection(section: HomeSection) {
        mutableSection.value = section
    }

    fun openConversation(conversation: Conversation) {
        mutableSelection.value = ChatSelection(
            conversationId = conversation.id,
            peerId = conversation.peerId,
            peerDisplayName = conversation.peerDisplayName,
        )
    }

    fun openPeer(peer: Peer) {
        if (peer.trustState != TrustState.TRUSTED) {
            requestPairing(peer.id)
            return
        }
        val localDeviceId = localIdentity.value?.deviceId ?: return showError("本机身份尚未就绪")
        mutableSelection.value = ChatSelection(
            conversationId = conversationIdFor(localDeviceId, peer.id),
            peerId = peer.id,
            peerDisplayName = peer.displayName,
        )
    }

    fun closeChat() {
        mutableSelection.value = null
    }

    fun requestPairing(peerId: String) = launchAction("发起配对失败") {
        pairingService.requestPairing(peerId)
    }

    fun confirmPairing(candidate: PairingCandidate) = launchAction("确认配对失败") {
        pairingService.confirmPairing(candidate.identity.deviceId)
        openTrustedIdentity(candidate)
        mutableNotice.value = UiNotice("已建立加密信任", isError = false)
    }

    fun dismissPairing(peerId: String) {
        launchAction("取消配对失败") {
            pairingService.dismissPairing(peerId)
        }
    }

    fun send(body: String) {
        val selected = selection.value ?: return showError("请先选择联系人")
        val senderId = localIdentity.value?.deviceId ?: return showError("本机身份尚未就绪")
        launchAction("消息发送失败") {
            sendMessage(selected.conversationId, senderId, selected.peerId, body).getOrThrow()
        }
    }

    fun retry(message: Message) = launchAction("消息重试失败") {
        sendMessage.retry(message).getOrThrow()
    }

    fun updateDisplayName(displayName: String) = launchAction("设备名称更新失败") {
        runtime.updateDisplayName(displayName)
        mutableNotice.value = UiNotice("设备名称已更新", isError = false)
    }

    fun retryRuntime() = launchAction("局域网服务启动失败") {
        runtime.start()
    }

    fun sendFile(file: LocalFile) {
        val selected = selection.value ?: return showError("请先选择联系人")
        launchAction("文件发送失败") {
            fileTransferService.sendFile(selected.peerId, file)
            mutableNotice.value = UiNotice("文件传输完成", isError = false)
        }
    }

    fun acceptFile(transferId: String) = launchAction("接受文件失败") {
        fileTransferService.acceptOffer(transferId)
    }

    fun rejectFile(transferId: String) = launchAction("拒绝文件失败") {
        fileTransferService.rejectOffer(transferId)
    }

    fun cancelFile(transferId: String) = launchAction("取消文件失败") {
        fileTransferService.cancelTransfer(transferId)
    }

    fun reportFilePickerError(message: String) {
        showError(message)
    }

    fun clearNotice() {
        mutableNotice.value = null
    }

    private fun openTrustedIdentity(candidate: PairingCandidate) {
        val localDeviceId = localIdentity.value?.deviceId ?: return
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
                        .onFailure { showError("发送已读回执失败：${it.message ?: "未知错误"}") }
                }
        }
    }

    private fun launchAction(errorPrefix: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showError("$errorPrefix：${exception.message ?: "未知错误"}")
            }
        }
    }

    private fun showError(message: String) {
        mutableNotice.value = UiNotice(message, isError = true)
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
    val message: String,
    val isError: Boolean,
)

enum class HomeSection {
    NEARBY,
    CHATS,
    SETTINGS,
}

private data class ReadRequest(
    val conversationId: String,
    val peerId: String,
    val messageIds: List<String>,
)
