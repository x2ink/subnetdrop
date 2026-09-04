package ink.x2.subnetdrop.domain.port

import ink.x2.subnetdrop.domain.model.PublicIdentity
import kotlinx.coroutines.flow.StateFlow

data class PairingCandidate(
    val identity: PublicIdentity,
    val safetyCode: String,
)

interface PairingService {
    val candidates: StateFlow<List<PairingCandidate>>

    suspend fun requestPairing(peerId: String)

    suspend fun confirmPairing(peerId: String)

    suspend fun dismissPairing(peerId: String)
}
