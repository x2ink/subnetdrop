package ink.x2.subnetdrop

import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.IncomingFileOffer
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.port.PairingCandidate
import ink.x2.subnetdrop.presentation.ChatSelection
import ink.x2.subnetdrop.presentation.HomeSection
import ink.x2.subnetdrop.presentation.UiNotice
import ink.x2.subnetdrop.runtime.RuntimeState

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
