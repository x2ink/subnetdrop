package ink.x2.subnetdrop.domain.usecase

import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.port.PeerRepository
import kotlinx.coroutines.flow.Flow

class ObservePeersUseCase(
    private val peerRepository: PeerRepository,
) {
    operator fun invoke(): Flow<List<Peer>> = peerRepository.observePeers()
}
