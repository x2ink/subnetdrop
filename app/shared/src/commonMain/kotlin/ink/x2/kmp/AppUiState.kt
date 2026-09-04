package ink.x2.kmp

import ink.x2.kmp.domain.model.Conversation
import ink.x2.kmp.domain.model.FileTransfer
import ink.x2.kmp.domain.model.IncomingFileOffer
import ink.x2.kmp.domain.model.Message
import ink.x2.kmp.domain.model.Peer
import ink.x2.kmp.domain.port.PairingCandidate
import ink.x2.kmp.presentation.ChatSelection
import ink.x2.kmp.presentation.HomeSection
import ink.x2.kmp.presentation.UiNotice
import ink.x2.kmp.runtime.RuntimeState

data class AppUiState(
    val peers: List<Peer>,
    val conversations: List<Conversation>,
    val candidates: List<PairingCandidate>,
    val runtimeState: RuntimeState,
    val localDeviceId: String?,
    val localDisplayName: String?,
    val selection: ChatSelection?,
    val messages: List<Message>,
    val notice: UiNotice?,
    val section: HomeSection,
    val incomingFileOffers: List<IncomingFileOffer>,
    val fileTransfers: List<FileTransfer>,
)
