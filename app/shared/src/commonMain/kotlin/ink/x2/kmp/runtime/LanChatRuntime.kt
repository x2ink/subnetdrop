package ink.x2.kmp.runtime

import ink.x2.kmp.domain.model.Peer
import ink.x2.kmp.domain.model.PeerAvailability
import ink.x2.kmp.domain.model.PublicIdentity
import ink.x2.kmp.domain.model.TrustState
import ink.x2.kmp.domain.port.ChatTransport
import ink.x2.kmp.domain.port.DiscoveryEvent
import ink.x2.kmp.domain.port.PeerDiscovery
import ink.x2.kmp.domain.port.PeerRepository
import ink.x2.kmp.domain.port.TransportEvent
import ink.x2.kmp.domain.port.TrustedIdentityRepository
import ink.x2.kmp.network.identity.LocalIdentityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LanChatRuntime(
    private val localIdentityService: LocalIdentityService,
    private val chatTransport: ChatTransport,
    private val peerDiscovery: PeerDiscovery,
    private val peerRepository: PeerRepository,
    private val trustedIdentityRepository: TrustedIdentityRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow<RuntimeState>(RuntimeState.Stopped)
    private val mutableIdentity = MutableStateFlow<PublicIdentity?>(null)
    private var discoveryJob: Job? = null
    private var transportJob: Job? = null

    val state: StateFlow<RuntimeState> = mutableState.asStateFlow()
    val identity: StateFlow<PublicIdentity?> = mutableIdentity.asStateFlow()

    suspend fun start() {
        lifecycleMutex.withLock {
            if (state.value is RuntimeState.Running || state.value is RuntimeState.Starting) return
            mutableState.value = RuntimeState.Starting
            startServices()
        }
    }

    suspend fun stop() {
        lifecycleMutex.withLock {
            discoveryJob?.cancel()
            transportJob?.cancel()
            peerDiscovery.stop()
            chatTransport.stop()
            peerRepository.markAllOffline()
            mutableState.value = RuntimeState.Stopped
        }
    }

    suspend fun updateDisplayName(displayName: String) {
        lifecycleMutex.withLock {
            val updatedIdentity = localIdentityService.updateDisplayName(displayName)
            mutableIdentity.value = updatedIdentity
            if (state.value !is RuntimeState.Running && state.value !is RuntimeState.Degraded) return
            restartDiscovery(updatedIdentity)
        }
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun startServices() {
        try {
            val identity = localIdentityService.get()
            mutableIdentity.value = identity
            peerRepository.markAllOffline()
            observeEvents()
            chatTransport.start()
            peerDiscovery.start(identity.deviceId, identity.displayName, chatTransport.listenerPort)
            mutableState.value = RuntimeState.Running(identity)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            stopAfterFailure(exception)
        }
    }

    private suspend fun restartDiscovery(identity: PublicIdentity) {
        try {
            peerDiscovery.stop()
            peerDiscovery.start(identity.deviceId, identity.displayName, chatTransport.listenerPort)
            mutableState.value = RuntimeState.Running(identity)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            mutableState.value = RuntimeState.Degraded(exception.message ?: "设备名称发布失败")
        }
    }

    private fun observeEvents() {
        discoveryJob?.cancel()
        transportJob?.cancel()
        discoveryJob = scope.launch {
            peerDiscovery.events.collect(::handleDiscoveryEvent)
        }
        transportJob = scope.launch {
            chatTransport.events.collect(::handleTransportEvent)
        }
    }

    private suspend fun handleDiscoveryEvent(event: DiscoveryEvent) {
        when (event) {
            is DiscoveryEvent.Found -> saveDiscoveredPeer(event.peer)
            is DiscoveryEvent.Lost -> markPeerOffline(event.peerId)
            is DiscoveryEvent.Failure -> mutableState.value = RuntimeState.Degraded(event.reason)
        }
    }

    private suspend fun saveDiscoveredPeer(discovered: Peer) {
        val existing = peerRepository.findPeer(discovered.id)
        val isTrusted = trustedIdentityRepository.find(discovered.id) != null
        peerRepository.upsertPeer(
            discovered.copy(
                trustState = when {
                    existing?.trustState == TrustState.KEY_CHANGED -> TrustState.KEY_CHANGED
                    isTrusted -> TrustState.TRUSTED
                    existing != null -> existing.trustState
                    else -> TrustState.UNPAIRED
                },
            ),
        )
    }

    private suspend fun markPeerOffline(peerId: String) {
        val peer = peerRepository.findPeer(peerId) ?: return
        peerRepository.upsertPeer(peer.copy(availability = PeerAvailability.OFFLINE))
    }

    private fun handleTransportEvent(event: TransportEvent) {
        if (event is TransportEvent.Failure) {
            mutableState.value = RuntimeState.Degraded(event.reason)
        }
    }

    private suspend fun stopAfterFailure(exception: Exception) {
        discoveryJob?.cancel()
        transportJob?.cancel()
        runCatching { peerDiscovery.stop() }
        runCatching { chatTransport.stop() }
        mutableState.value = RuntimeState.Failed(exception.message ?: "局域网服务启动失败")
    }
}

sealed interface RuntimeState {
    data object Stopped : RuntimeState

    data object Starting : RuntimeState

    data class Running(val identity: PublicIdentity) : RuntimeState

    data class Degraded(val reason: String) : RuntimeState

    data class Failed(val reason: String) : RuntimeState
}
