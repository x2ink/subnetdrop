package ink.x2.kmp.domain.port

import ink.x2.kmp.domain.model.FileTransfer
import ink.x2.kmp.domain.model.IncomingFileOffer
import ink.x2.kmp.domain.model.LocalFile
import kotlinx.coroutines.flow.StateFlow

interface FileTransferService {
    val incomingOffers: StateFlow<List<IncomingFileOffer>>
    val transfers: StateFlow<List<FileTransfer>>

    suspend fun sendFile(peerId: String, file: LocalFile)

    suspend fun acceptOffer(transferId: String)

    suspend fun rejectOffer(transferId: String)

    suspend fun cancelTransfer(transferId: String)
}
