package ink.x2.subnetdrop.network.transport

import ink.x2.subnetdrop.domain.model.FileTransfer
import ink.x2.subnetdrop.domain.model.FileTransferDirection
import ink.x2.subnetdrop.domain.model.FileTransferStatus
import ink.x2.subnetdrop.domain.model.IncomingFileOffer
import ink.x2.subnetdrop.domain.model.LocalFile
import ink.x2.subnetdrop.domain.model.Message
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.PublicIdentity
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.domain.model.conversationIdFor
import ink.x2.subnetdrop.domain.port.ChatRepository
import ink.x2.subnetdrop.domain.port.ChatTransport
import ink.x2.subnetdrop.domain.port.FileTransferService
import ink.x2.subnetdrop.domain.port.IdGenerator
import ink.x2.subnetdrop.domain.port.PairingCandidate
import ink.x2.subnetdrop.domain.port.PairingService
import ink.x2.subnetdrop.domain.port.PeerReachabilityProbe
import ink.x2.subnetdrop.domain.port.PeerRepository
import ink.x2.subnetdrop.domain.port.SecureMessageCodec
import ink.x2.subnetdrop.domain.port.TimestampProvider
import ink.x2.subnetdrop.domain.port.TransportEvent
import ink.x2.subnetdrop.domain.port.TrustedIdentityRepository
import ink.x2.subnetdrop.network.identity.LocalIdentityService
import ink.x2.subnetdrop.network.protocol.DeliveryAckPayload
import ink.x2.subnetdrop.network.protocol.ErrorPayload
import ink.x2.subnetdrop.network.protocol.FileCancelPayload
import ink.x2.subnetdrop.network.protocol.FileDecisionPayload
import ink.x2.subnetdrop.network.protocol.FileOfferPayload
import ink.x2.subnetdrop.network.protocol.FileStreamCompletePayload
import ink.x2.subnetdrop.network.protocol.FileStreamStartPayload
import ink.x2.subnetdrop.network.protocol.FrameType
import ink.x2.subnetdrop.network.protocol.PublicIdentityPayload
import ink.x2.subnetdrop.network.protocol.ReadReceiptPayload
import ink.x2.subnetdrop.network.protocol.TransportFrame
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCio
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCio
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Base64

class SubnetDropTransport(
    private val localIdentityService: LocalIdentityService,
    private val peerRepository: PeerRepository,
    private val trustedIdentityRepository: TrustedIdentityRepository,
    private val chatRepository: ChatRepository,
    private val secureMessageCodec: SecureMessageCodec,
    private val timestampProvider: TimestampProvider,
    private val idGenerator: IdGenerator,
    private val receivedFilesDirectory: File,
    override val listenerPort: Int = DEFAULT_PORT,
) : ChatTransport, PairingService, FileTransferService, PeerReachabilityProbe {
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = EVENT_BUFFER_SIZE)
    private val mutableCandidates = MutableStateFlow<List<PairingCandidate>>(emptyList())
    private val candidateMutex = Mutex()
    private val lifecycleMutex = Mutex()
    private val transferMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false }
    private val client by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(ClientCio) {
            install(ClientWebSockets)
        }
    }
    private var server: EmbeddedServer<*, *>? = null
    private val pendingDecisions = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val incomingSessions = mutableMapOf<String, IncomingSession>()
    private val cancelledTransfers = mutableSetOf<String>()
    private val mutableIncomingOffers = MutableStateFlow<List<IncomingFileOffer>>(emptyList())
    private val mutableTransfers = MutableStateFlow<List<FileTransfer>>(emptyList())

    override val events: Flow<TransportEvent> = mutableEvents.asSharedFlow()
    override val candidates: StateFlow<List<PairingCandidate>> = mutableCandidates.asStateFlow()
    override val incomingOffers: StateFlow<List<IncomingFileOffer>> = mutableIncomingOffers.asStateFlow()
    override val transfers: StateFlow<List<FileTransfer>> = mutableTransfers.asStateFlow()

    override suspend fun start() {
        lifecycleMutex.withLock {
            if (server != null) return
            server = createServer().also { it.start(wait = false) }
        }
    }

    override suspend fun stop() {
        lifecycleMutex.withLock {
            server?.stop(SHUTDOWN_GRACE_MS, SHUTDOWN_TIMEOUT_MS)
            server = null
        }
        cleanupTransferSessions()
    }

    override suspend fun isReachable(peer: Peer): Boolean {
        return try {
            val localDeviceId = localIdentityService.getProfile().deviceId
            var response: TransportFrame? = null
            withTimeout(REACHABILITY_TIMEOUT_MS) {
                client.webSocket(host = peer.host, port = peer.port, path = CHAT_PATH) {
                    response = exchangeFrame(
                        TransportFrame(
                            type = FrameType.PING,
                            senderId = localDeviceId,
                            recipientId = peer.id,
                            payload = "",
                        ),
                    )
                }
            }
            response?.let { pong ->
                pong.type == FrameType.PONG &&
                    pong.senderId == peer.id &&
                    pong.recipientId == localDeviceId
            } == true
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun send(peerId: String, message: Message) {
        val peer = requireNotNull(peerRepository.findPeer(peerId)) { "Peer was not discovered" }
        require(peer.trustState == TrustState.TRUSTED) { "Peer identity must be re-verified" }
        val localIdentity = localIdentityService.get()
        require(message.senderId == localIdentity.deviceId) { "Message sender is not the local identity" }
        val recipientIdentity = requireNotNull(trustedIdentityRepository.find(peerId)) {
            "Peer is not trusted"
        }
        val encryptedEnvelope = secureMessageCodec.encrypt(message, recipientIdentity)
        val response = exchange(
            peerId,
            TransportFrame(
                type = FrameType.CHAT_MESSAGE,
                senderId = localIdentity.deviceId,
                recipientId = peerId,
                payload = encryptedEnvelope,
            ),
        )
        verifyDeliveryAck(response, message.id, recipientIdentity, localIdentity.deviceId)
        mutableEvents.tryEmit(TransportEvent.MessageDelivered(message.id))
    }

    override suspend fun sendReadReceipt(peerId: String, messageIds: List<String>) {
        require(messageIds.isNotEmpty()) { "Read receipt must contain at least one message" }
        require(messageIds.size <= MAX_READ_RECEIPT_MESSAGE_COUNT) { "Read receipt contains too many messages" }
        require(messageIds.distinct().size == messageIds.size) { "Read receipt contains duplicate messages" }
        messageIds.forEach { validateIdentifier(it, "message ID") }
        val peer = requireNotNull(peerRepository.findPeer(peerId)) { "Peer was not discovered" }
        require(peer.trustState == TrustState.TRUSTED) { "Peer identity must be re-verified" }
        val localIdentity = localIdentityService.get()
        val recipientIdentity = requireNotNull(trustedIdentityRepository.find(peerId)) { "Peer is not trusted" }
        val signingData = readReceiptSigningData(localIdentity.deviceId, peerId, messageIds)
        val response = exchange(
            peerId,
            TransportFrame(
                type = FrameType.READ_RECEIPT,
                senderId = localIdentity.deviceId,
                recipientId = peerId,
                payload = json.encodeToString(ReadReceiptPayload(messageIds)),
                signature = Base64.getEncoder().encodeToString(secureMessageCodec.sign(signingData)),
            ),
        )
        verifyDeliveryAck(response, messageIds.last(), recipientIdentity, localIdentity.deviceId)
    }

    override suspend fun sendFile(peerId: String, file: LocalFile) {
        val source = File(file.path).absoluteFile
        require(source.isFile) { "Selected file does not exist" }
        validateFileName(file.name)
        require(source.length() == file.size) { "Selected file changed before transfer" }
        require(file.size in 0..MAX_FILE_SIZE_BYTES) { "File exceeds the allowed size" }
        val transferId = idGenerator.generate().also { validateIdentifier(it, "transfer ID") }
        val transfer = FileTransfer(
            id = transferId,
            peerId = peerId,
            fileName = file.name,
            size = file.size,
            direction = FileTransferDirection.OUTGOING,
            status = FileTransferStatus.PREPARING,
            localPath = source.path,
        )
        setTransfer(transfer)
        val decision = CompletableDeferred<Boolean>()
        transferMutex.withLock { pendingDecisions[transferId] = decision }
        try {
            updateTransfer(transferId) { it.copy(status = FileTransferStatus.WAITING_FOR_ACCEPTANCE) }
            sendSignedFileRequest(
                peerId = peerId,
                type = FrameType.FILE_OFFER,
                acknowledgementId = transferId,
                payload = json.encodeToString(
                    FileOfferPayload(
                        transferId = transferId,
                        fileName = file.name,
                        size = file.size,
                        contentType = file.contentType,
                    ),
                ),
            )
            val accepted = withTimeout(FILE_OFFER_TIMEOUT_MS) { decision.await() }
            if (isTransferCancelled(transferId)) {
                updateTransfer(transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
                return
            }
            if (!accepted) {
                updateTransfer(transferId) { it.copy(status = FileTransferStatus.REJECTED) }
                return
            }
            updateTransfer(transferId) { it.copy(status = FileTransferStatus.TRANSFERRING) }
            uploadFile(peerId, transferId, source)
            updateTransfer(transferId) {
                it.copy(status = FileTransferStatus.COMPLETED, transferredBytes = it.size)
            }
        } catch (exception: CancellationException) {
            updateTransfer(transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
            throw exception
        } catch (_: TransferCancelledException) {
            updateTransfer(transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
        } catch (exception: Exception) {
            updateTransfer(transferId) {
                it.copy(status = FileTransferStatus.FAILED, error = exception.message ?: "Transfer failed")
            }
            throw exception
        } finally {
            transferMutex.withLock { pendingDecisions.remove(transferId) }
        }
    }

    override suspend fun acceptOffer(transferId: String) {
        val offer = removeIncomingOffer(transferId)
        val session = createIncomingSession(offer)
        transferMutex.withLock { incomingSessions[transferId] = session }
        setTransfer(
            FileTransfer(
                id = offer.transferId,
                peerId = offer.peerId,
                fileName = offer.fileName,
                size = offer.size,
                direction = FileTransferDirection.INCOMING,
                status = FileTransferStatus.TRANSFERRING,
                localPath = requireNotNull(session.finalFile).path,
            ),
        )
        try {
            sendFileDecision(offer, accepted = true)
        } catch (exception: Exception) {
            discardIncomingSession(transferId)
            updateTransfer(transferId) {
                it.copy(status = FileTransferStatus.FAILED, error = exception.message ?: "Acceptance failed")
            }
            throw exception
        }
    }

    override suspend fun rejectOffer(transferId: String) {
        val offer = removeIncomingOffer(transferId)
        discardIncomingSession(transferId)
        updateTransfer(transferId) { it.copy(status = FileTransferStatus.REJECTED) }
        sendFileDecision(offer, accepted = false)
    }

    override suspend fun cancelTransfer(transferId: String) {
        val transfer = findTransfer(transferId)
        transferMutex.withLock {
            cancelledTransfers += transferId
            pendingDecisions[transferId]?.complete(false)
        }
        removeOfferIfPresent(transferId)
        discardIncomingSession(transferId)
        updateTransfer(transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
        sendSignedFileRequest(
            peerId = transfer.peerId,
            type = FrameType.FILE_CANCEL,
            acknowledgementId = transferId,
            payload = json.encodeToString(FileCancelPayload(transferId)),
        )
    }

    override suspend fun requestPairing(peerId: String) {
        val localIdentity = localIdentityService.get()
        val response = exchange(
            peerId,
            TransportFrame(
                type = FrameType.PAIR_REQUEST,
                senderId = localIdentity.deviceId,
                recipientId = peerId,
                payload = json.encodeToString(localIdentity.toPayload()),
            ),
        )
        require(response.type == FrameType.PAIR_RESPONSE) { "Peer rejected pairing" }
        require(response.senderId == peerId && response.recipientId == localIdentity.deviceId) {
            "Pairing response identity mismatch"
        }
        addCandidate(response.decodeIdentity(), localIdentity)
    }

    override suspend fun confirmPairing(peerId: String) {
        val candidate = candidates.value.firstOrNull { it.identity.deviceId == peerId }
            ?: error("No pending pairing for peer")
        trustedIdentityRepository.save(candidate.identity, timestampProvider.nowMillis())
        dismissPairing(peerId)
    }

    override suspend fun dismissPairing(peerId: String) {
        candidateMutex.withLock {
            mutableCandidates.value = mutableCandidates.value.filterNot { it.identity.deviceId == peerId }
        }
    }

    private fun createServer(): EmbeddedServer<*, *> = embeddedServer(
        factory = ServerCio,
        host = LISTEN_HOST,
        port = listenerPort,
    ) {
        install(ServerWebSockets) {
            maxFrameSize = MAX_FRAME_SIZE_BYTES
        }
        routing {
            webSocket(CHAT_PATH) {
                handleIncomingSession()
            }
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleIncomingSession() {
        var upload: IncomingUpload? = null
        try {
            for (rawFrame in incoming) {
                try {
                    when (rawFrame) {
                        is Frame.Text -> {
                            val text = rawFrame.readText()
                            require(text.length <= MAX_FRAME_TEXT_LENGTH) { "Frame exceeds the allowed size" }
                            val frame = json.decodeFromString<TransportFrame>(text)
                            if (frame.type == FrameType.FILE_STREAM_START) {
                                require(upload == null) { "An upload is already active on this connection" }
                                upload = beginIncomingUpload(frame)
                                sendEncoded(
                                    createDeliveryAck(
                                        streamStartAckId(upload.transferId),
                                        upload.localIdentity,
                                        upload.senderIdentity.deviceId,
                                    ),
                                )
                            } else if (frame.type == FrameType.FILE_STREAM_COMPLETE) {
                                val activeUpload = requireNotNull(upload) { "File stream was not authenticated" }
                                completeIncomingUpload(frame, activeUpload)
                                sendEncoded(
                                    createDeliveryAck(
                                        activeUpload.transferId,
                                        activeUpload.localIdentity,
                                        activeUpload.senderIdentity.deviceId,
                                    ),
                                )
                                upload = null
                            } else {
                                val response = handleIncomingFrame(frame)
                                response?.let { sendEncoded(it) }
                            }
                        }
                        is Frame.Binary -> {
                            val activeUpload = requireNotNull(upload) { "File stream was not authenticated" }
                            appendIncomingBytes(
                                peerId = activeUpload.senderIdentity.deviceId,
                                transferId = activeUpload.transferId,
                                bytes = rawFrame.readBytes(),
                            )
                        }
                        else -> Unit
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    upload?.let { failIncomingUpload(it.transferId, exception) }
                    upload = null
                    sendEncoded(errorFrame("INVALID_REQUEST", exception.message ?: "Invalid request"))
                }
            }
        } finally {
            upload?.let { failIncomingUpload(it.transferId, IllegalStateException("File connection closed")) }
        }
    }

    private suspend fun handleIncomingFrame(frame: TransportFrame): TransportFrame? {
        validateFrame(frame)
        if (frame.type == FrameType.PING) return handlePing(frame)
        val localIdentity = localIdentityService.get()
        require(frame.recipientId == localIdentity.deviceId) { "Frame is addressed to another device" }
        return when (frame.type) {
            FrameType.PAIR_REQUEST -> handlePairRequest(frame, localIdentity)
            FrameType.CHAT_MESSAGE -> handleChatMessage(frame, localIdentity)
            FrameType.READ_RECEIPT -> handleReadReceipt(frame, localIdentity)
            FrameType.FILE_OFFER -> handleFileOffer(frame, localIdentity)
            FrameType.FILE_DECISION -> handleFileDecision(frame, localIdentity)
            FrameType.FILE_CANCEL -> handleFileCancel(frame, localIdentity)
            FrameType.PAIR_RESPONSE,
            FrameType.DELIVERY_ACK,
            FrameType.FILE_STREAM_START,
            FrameType.FILE_STREAM_COMPLETE,
            FrameType.ERROR,
            FrameType.PING,
            FrameType.PONG,
            -> errorFrame("UNEXPECTED_FRAME", "Unexpected frame type")
        }
    }

    private suspend fun handlePing(frame: TransportFrame): TransportFrame {
        val profile = localIdentityService.getProfile()
        require(frame.recipientId == profile.deviceId) { "Frame is addressed to another device" }
        return TransportFrame(
            type = FrameType.PONG,
            senderId = profile.deviceId,
            recipientId = frame.senderId,
            payload = "",
        )
    }

    private suspend fun handlePairRequest(
        frame: TransportFrame,
        localIdentity: PublicIdentity,
    ): TransportFrame {
        val remoteIdentity = frame.decodeIdentity()
        require(remoteIdentity.deviceId == frame.senderId) { "Pairing identity does not match sender" }
        addCandidate(remoteIdentity, localIdentity)
        return TransportFrame(
            type = FrameType.PAIR_RESPONSE,
            senderId = localIdentity.deviceId,
            recipientId = remoteIdentity.deviceId,
            payload = json.encodeToString(localIdentity.toPayload()),
        )
    }

    private suspend fun handleChatMessage(
        frame: TransportFrame,
        localIdentity: PublicIdentity,
    ): TransportFrame {
        val senderIdentity = requireNotNull(trustedIdentityRepository.find(frame.senderId)) {
            "Sender is not trusted"
        }
        val message = secureMessageCodec.decrypt(frame.payload, senderIdentity, localIdentity.deviceId)
        require(message.conversationId == conversationIdFor(message.senderId, message.recipientId)) {
            "Conversation identity is invalid"
        }
        if (!chatRepository.containsMessage(message.id)) {
            chatRepository.saveMessage(message)
            mutableEvents.tryEmit(TransportEvent.MessageReceived(message))
        }
        return createDeliveryAck(message.id, localIdentity, senderIdentity.deviceId)
    }

    private suspend fun handleReadReceipt(
        frame: TransportFrame,
        localIdentity: PublicIdentity,
    ): TransportFrame {
        val senderIdentity = requireNotNull(trustedIdentityRepository.find(frame.senderId)) {
            "Sender is not trusted"
        }
        val receipt = json.decodeFromString<ReadReceiptPayload>(frame.payload)
        require(receipt.messageIds.isNotEmpty()) { "Read receipt must contain at least one message" }
        require(receipt.messageIds.size <= MAX_READ_RECEIPT_MESSAGE_COUNT) {
            "Read receipt contains too many messages"
        }
        require(receipt.messageIds.distinct().size == receipt.messageIds.size) {
            "Read receipt contains duplicate messages"
        }
        receipt.messageIds.forEach { validateIdentifier(it, "message ID") }
        val signature = Base64.getDecoder().decode(requireNotNull(frame.signature))
        secureMessageCodec.verify(
            readReceiptSigningData(frame.senderId, frame.recipientId, receipt.messageIds),
            signature,
            senderIdentity,
        )
        chatRepository.markOutgoingMessagesRead(frame.senderId, receipt.messageIds)
        return createDeliveryAck(receipt.messageIds.last(), localIdentity, senderIdentity.deviceId)
    }

    private suspend fun handleFileOffer(
        frame: TransportFrame,
        localIdentity: PublicIdentity,
    ): TransportFrame {
        val senderIdentity = trustedSender(frame.senderId)
        val offer = decodeSignedFilePayload<FileOfferPayload>(frame, senderIdentity)
        validateIdentifier(offer.transferId, "transfer ID")
        validateFileName(offer.fileName)
        require(offer.size in 0..MAX_FILE_SIZE_BYTES) { "File exceeds the allowed size" }
        val peer = requireNotNull(peerRepository.findPeer(frame.senderId)) { "Sender was not discovered" }
        val incomingOffer = IncomingFileOffer(
            transferId = offer.transferId,
            peerId = frame.senderId,
            peerDisplayName = peer.displayName,
            fileName = offer.fileName,
            size = offer.size,
        )
        transferMutex.withLock {
            val duplicate = mutableIncomingOffers.value.any { it.transferId == offer.transferId } ||
                mutableTransfers.value.any { it.id == offer.transferId }
            require(!duplicate) { "Transfer already exists" }
            mutableIncomingOffers.value = mutableIncomingOffers.value + incomingOffer
            mutableTransfers.value = mutableTransfers.value + FileTransfer(
                id = offer.transferId,
                peerId = frame.senderId,
                fileName = offer.fileName,
                size = offer.size,
                direction = FileTransferDirection.INCOMING,
                status = FileTransferStatus.WAITING_FOR_ACCEPTANCE,
            )
            incomingSessions[offer.transferId] = IncomingSession.pending(offer, frame.senderId)
        }
        return createDeliveryAck(offer.transferId, localIdentity, senderIdentity.deviceId)
    }

    private suspend fun handleFileDecision(
        frame: TransportFrame,
        localIdentity: PublicIdentity,
    ): TransportFrame {
        val senderIdentity = trustedSender(frame.senderId)
        val decision = decodeSignedFilePayload<FileDecisionPayload>(frame, senderIdentity)
        validateIdentifier(decision.transferId, "transfer ID")
        val pending = transferMutex.withLock { pendingDecisions[decision.transferId] }
            ?: error("Transfer offer is no longer pending")
        require(findTransfer(decision.transferId).peerId == frame.senderId) { "Transfer peer mismatch" }
        require(pending.complete(decision.accepted)) { "Transfer decision was already received" }
        return createDeliveryAck(decision.transferId, localIdentity, senderIdentity.deviceId)
    }

    private suspend fun beginIncomingUpload(frame: TransportFrame): IncomingUpload {
        validateFrame(frame)
        val localIdentity = localIdentityService.get()
        require(frame.recipientId == localIdentity.deviceId) { "Frame is addressed to another device" }
        val senderIdentity = trustedSender(frame.senderId)
        val start = decodeSignedFilePayload<FileStreamStartPayload>(frame, senderIdentity)
        validateIdentifier(start.transferId, "transfer ID")
        val transfer = findTransfer(start.transferId)
        require(transfer.peerId == frame.senderId) { "Transfer peer mismatch" }
        require(transfer.status == FileTransferStatus.TRANSFERRING) { "Transfer was not accepted" }
        return IncomingUpload(start.transferId, localIdentity, senderIdentity)
    }

    private suspend fun completeIncomingUpload(frame: TransportFrame, upload: IncomingUpload) {
        validateFrame(frame)
        require(frame.senderId == upload.senderIdentity.deviceId) { "Transfer sender changed" }
        require(frame.recipientId == upload.localIdentity.deviceId) { "Frame is addressed to another device" }
        val completion = decodeSignedFilePayload<FileStreamCompletePayload>(frame, upload.senderIdentity)
        require(completion.transferId == upload.transferId) { "Transfer completion does not match stream" }
        require(SHA256_REGEX.matches(completion.sha256)) { "Invalid file checksum" }
        completeIncomingTransfer(completion.transferId, completion.sha256)
    }

    private suspend fun handleFileCancel(
        frame: TransportFrame,
        localIdentity: PublicIdentity,
    ): TransportFrame {
        val senderIdentity = trustedSender(frame.senderId)
        val cancellation = decodeSignedFilePayload<FileCancelPayload>(frame, senderIdentity)
        validateIdentifier(cancellation.transferId, "transfer ID")
        val transfer = findTransfer(cancellation.transferId)
        require(transfer.peerId == frame.senderId) { "Transfer peer mismatch" }
        transferMutex.withLock {
            cancelledTransfers += cancellation.transferId
            pendingDecisions[cancellation.transferId]?.complete(false)
        }
        removeOfferIfPresent(cancellation.transferId)
        discardIncomingSession(cancellation.transferId)
        updateTransfer(cancellation.transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
        return createDeliveryAck(cancellation.transferId, localIdentity, senderIdentity.deviceId)
    }

    private suspend fun uploadFile(peerId: String, transferId: String, source: File) {
        val peer = requireNotNull(peerRepository.findPeer(peerId)) { "Peer was not discovered" }
        require(peer.availability == PeerAvailability.ONLINE) { "Peer is offline" }
        val localIdentity = localIdentityService.get()
        val recipientIdentity = requireNotNull(trustedIdentityRepository.find(peerId)) { "Peer is not trusted" }
        val totalBytes = source.length()
        var transferredBytes = 0L
        client.webSocket(host = peer.host, port = peer.port, path = CHAT_PATH) {
            val startResponse = exchangeFrame(
                createSignedFileFrame(
                    type = FrameType.FILE_STREAM_START,
                    sender = localIdentity,
                    recipient = recipientIdentity,
                    payload = json.encodeToString(FileStreamStartPayload(transferId)),
                ),
            )
            verifyDeliveryAck(
                startResponse,
                streamStartAckId(transferId),
                recipientIdentity,
                localIdentity.deviceId,
            )
            val digest = withContext(Dispatchers.IO) {
                val messageDigest = MessageDigest.getInstance("SHA-256")
                source.inputStream().buffered(FILE_CHUNK_SIZE_BYTES).use { input ->
                    while (transferredBytes < totalBytes) {
                        if (isTransferCancelled(transferId)) throw TransferCancelledException()
                        val expectedBytes = minOf(
                            FILE_CHUNK_SIZE_BYTES.toLong(),
                            totalBytes - transferredBytes,
                        ).toInt()
                        val bytes = input.readExactChunk(expectedBytes)
                        send(Frame.Binary(fin = true, data = bytes))
                        messageDigest.update(bytes)
                        transferredBytes += bytes.size
                        updateTransfer(transferId) { it.copy(transferredBytes = transferredBytes) }
                    }
                    if (isTransferCancelled(transferId)) throw TransferCancelledException()
                    require(source.length() == totalBytes) { "Selected file changed during transfer" }
                }
                messageDigest.digest().toHex()
            }
            val completionResponse = exchangeFrame(
                createSignedFileFrame(
                    type = FrameType.FILE_STREAM_COMPLETE,
                    sender = localIdentity,
                    recipient = recipientIdentity,
                    payload = json.encodeToString(FileStreamCompletePayload(transferId, digest)),
                ),
            )
            verifyDeliveryAck(
                completionResponse,
                transferId,
                recipientIdentity,
                localIdentity.deviceId,
            )
        }
    }

    private suspend fun sendFileDecision(offer: IncomingFileOffer, accepted: Boolean) {
        sendSignedFileRequest(
            peerId = offer.peerId,
            type = FrameType.FILE_DECISION,
            acknowledgementId = offer.transferId,
            payload = json.encodeToString(FileDecisionPayload(offer.transferId, accepted)),
        )
    }

    private suspend fun sendSignedFileRequest(
        peerId: String,
        type: FrameType,
        acknowledgementId: String,
        payload: String,
    ) {
        val localIdentity = localIdentityService.get()
        val recipientIdentity = requireNotNull(trustedIdentityRepository.find(peerId)) { "Peer is not trusted" }
        val response = exchange(
            peerId,
            createSignedFileFrame(
                type = type,
                sender = localIdentity,
                recipient = recipientIdentity,
                payload = payload,
            ),
        )
        verifyDeliveryAck(response, acknowledgementId, recipientIdentity, localIdentity.deviceId)
    }

    private suspend fun createSignedFileFrame(
        type: FrameType,
        sender: PublicIdentity,
        recipient: PublicIdentity,
        payload: String,
    ): TransportFrame {
        val signingData = filePayloadSigningData(type, sender.deviceId, recipient.deviceId, payload)
        return TransportFrame(
            type = type,
            senderId = sender.deviceId,
            recipientId = recipient.deviceId,
            payload = payload,
            signature = Base64.getEncoder().encodeToString(secureMessageCodec.sign(signingData)),
        )
    }

    private inline fun <reified T> decodeSignedFilePayload(
        frame: TransportFrame,
        senderIdentity: PublicIdentity,
    ): T {
        val signature = Base64.getDecoder().decode(requireNotNull(frame.signature))
        secureMessageCodec.verify(
            filePayloadSigningData(frame.type, frame.senderId, frame.recipientId, frame.payload),
            signature,
            senderIdentity,
        )
        return json.decodeFromString(frame.payload)
    }

    private suspend fun trustedSender(peerId: String): PublicIdentity =
        requireNotNull(trustedIdentityRepository.find(peerId)) { "Sender is not trusted" }

    private suspend fun appendIncomingBytes(
        peerId: String,
        transferId: String,
        bytes: ByteArray,
    ) {
        transferMutex.withLock {
            require(bytes.isNotEmpty() && bytes.size <= FILE_CHUNK_SIZE_BYTES) { "File chunk has invalid size" }
            val session = incomingSessions[transferId] ?: error("Transfer session was not accepted")
            require(session.peerId == peerId) { "Transfer peer mismatch" }
            require(session.tempFile != null) { "Transfer session was not accepted" }
            require(transferId !in cancelledTransfers) { "Transfer was cancelled" }
            val remainingBytes = session.size - session.receivedBytes
            require(remainingBytes > 0) { "File contains more bytes than offered" }
            require(bytes.size.toLong() <= remainingBytes) { "File contains more bytes than offered" }
            val reachesEnd = bytes.size.toLong() == remainingBytes
            if (!reachesEnd) require(bytes.size == FILE_CHUNK_SIZE_BYTES) { "Non-final file chunk has invalid size" }
            requireNotNull(session.outputStream) { "Transfer output stream is not open" }.write(bytes)
            session.digest.update(bytes)
            val updated = session.copy(
                receivedBytes = session.receivedBytes + bytes.size,
            )
            incomingSessions[transferId] = updated
            mutableTransfers.value = mutableTransfers.value.map { transfer ->
                if (transfer.id == transferId) {
                    transfer.copy(transferredBytes = updated.receivedBytes)
                } else {
                    transfer
                }
            }
        }
    }

    private suspend fun failIncomingUpload(transferId: String, exception: Exception) {
        discardIncomingSession(transferId)
        updateTransfer(transferId) { transfer ->
            if (transfer.status == FileTransferStatus.TRANSFERRING) {
                transfer.copy(status = FileTransferStatus.FAILED, error = exception.message ?: "File upload failed")
            } else {
                transfer
            }
        }
    }

    private suspend fun completeIncomingTransfer(transferId: String, expectedSha256: String) {
        val session = transferMutex.withLock {
            requireNotNull(incomingSessions.remove(transferId)) { "Transfer session does not exist" }
        }
        val tempFile = requireNotNull(session.tempFile)
        val finalFile = requireNotNull(session.finalFile)
        try {
            requireNotNull(session.outputStream) { "Transfer output stream is not open" }.run {
                flush()
                close()
            }
            require(tempFile.length() == session.size) { "Received file size does not match offer" }
            require(session.digest.digest().toHex() == expectedSha256) { "Received file checksum does not match sender" }
            require(!finalFile.exists()) { "Destination file appeared during transfer" }
            require(tempFile.renameTo(finalFile)) { "Unable to publish received file" }
            updateTransfer(transferId) {
                it.copy(
                    status = FileTransferStatus.COMPLETED,
                    transferredBytes = it.size,
                    localPath = finalFile.path,
                )
            }
        } catch (exception: Exception) {
            tempFile.delete()
            updateTransfer(transferId) {
                it.copy(status = FileTransferStatus.FAILED, error = exception.message ?: "File validation failed")
            }
            throw exception
        }
    }

    private suspend fun createIncomingSession(offer: IncomingFileOffer): IncomingSession {
        require(receivedFilesDirectory.mkdirs() || receivedFilesDirectory.isDirectory) {
            "Unable to create received-files directory"
        }
        return transferMutex.withLock {
            val pending = requireNotNull(incomingSessions[offer.transferId]) { "Transfer offer does not exist" }
            val destination = uniqueDestinationFile(offer.fileName)
            val tempFile = File(receivedFilesDirectory, ".${offer.transferId}.part")
            require(!tempFile.exists() && tempFile.createNewFile()) { "Unable to create temporary file" }
            try {
                pending.copy(
                    tempFile = tempFile,
                    finalFile = destination,
                    outputStream = BufferedOutputStream(FileOutputStream(tempFile), FILE_CHUNK_SIZE_BYTES),
                )
            } catch (exception: Exception) {
                tempFile.delete()
                throw exception
            }
        }
    }

    private fun uniqueDestinationFile(fileName: String): File {
        val direct = File(receivedFilesDirectory, fileName)
        val reservedPaths = incomingSessions.values.mapNotNull { it.finalFile?.absolutePath }.toSet()
        if (!direct.exists() && direct.absolutePath !in reservedPaths) return direct
        val extensionIndex = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        val base = fileName.substring(0, extensionIndex)
        val extension = fileName.substring(extensionIndex)
        var suffix = 1
        while (true) {
            val candidate = File(receivedFilesDirectory, "$base ($suffix)$extension")
            if (!candidate.exists() && candidate.absolutePath !in reservedPaths) return candidate
            suffix += 1
        }
    }

    private suspend fun removeIncomingOffer(transferId: String): IncomingFileOffer = transferMutex.withLock {
        val offer = mutableIncomingOffers.value.firstOrNull { it.transferId == transferId }
            ?: error("Incoming file offer does not exist")
        mutableIncomingOffers.value = mutableIncomingOffers.value.filterNot { it.transferId == transferId }
        offer
    }

    private suspend fun removeOfferIfPresent(transferId: String) {
        transferMutex.withLock {
            mutableIncomingOffers.value = mutableIncomingOffers.value.filterNot { it.transferId == transferId }
        }
    }

    private suspend fun discardIncomingSession(transferId: String) {
        val session = transferMutex.withLock { incomingSessions.remove(transferId) }
        session?.outputStream?.close()
        session?.tempFile?.let { temporaryFile ->
            require(!temporaryFile.exists() || temporaryFile.delete()) { "Unable to delete temporary file" }
        }
    }

    private suspend fun cleanupTransferSessions() {
        val sessions = transferMutex.withLock {
            val activeSessions = incomingSessions.values.toList()
            cancelledTransfers += mutableTransfers.value
                .filter { it.status.isActive() }
                .map(FileTransfer::id)
            pendingDecisions.values.forEach { it.complete(false) }
            incomingSessions.clear()
            mutableIncomingOffers.value = emptyList()
            mutableTransfers.value = mutableTransfers.value.map { transfer ->
                if (transfer.status.isActive()) transfer.copy(status = FileTransferStatus.CANCELLED) else transfer
            }
            activeSessions
        }
        sessions.forEach { session ->
            session.outputStream?.close()
            session.tempFile?.let { temporaryFile ->
                require(!temporaryFile.exists() || temporaryFile.delete()) { "Unable to delete temporary file" }
            }
        }
    }

    private suspend fun setTransfer(transfer: FileTransfer) {
        transferMutex.withLock {
            mutableTransfers.value = mutableTransfers.value.filterNot { it.id == transfer.id } + transfer
        }
    }

    private suspend fun updateTransfer(transferId: String, transform: (FileTransfer) -> FileTransfer) {
        transferMutex.withLock {
            var found = false
            mutableTransfers.value = mutableTransfers.value.map { transfer ->
                if (transfer.id == transferId) {
                    found = true
                    transform(transfer)
                } else {
                    transfer
                }
            }
            require(found) { "Transfer does not exist" }
        }
    }

    private suspend fun findTransfer(transferId: String): FileTransfer = transferMutex.withLock {
        mutableTransfers.value.firstOrNull { it.id == transferId } ?: error("Transfer does not exist")
    }

    private suspend fun isTransferCancelled(transferId: String): Boolean =
        transferMutex.withLock { transferId in cancelledTransfers }

    private fun validateFileName(fileName: String) {
        require(fileName.isNotBlank() && fileName.length <= MAX_FILE_NAME_LENGTH) { "Invalid file name" }
        require('/' !in fileName && '\\' !in fileName && fileName != "." && fileName != "..") {
            "File name must not contain a path"
        }
        require(fileName.none { it.code < MIN_PRINTABLE_CHARACTER_CODE }) { "File name contains control characters" }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private fun java.io.InputStream.readExactChunk(expectedBytes: Int): ByteArray {
        if (expectedBytes == 0) return ByteArray(0)
        val result = ByteArray(expectedBytes)
        var offset = 0
        while (offset < expectedBytes) {
            val count = read(result, offset, expectedBytes - offset)
            require(count >= 0) { "Selected file changed during transfer" }
            offset += count
        }
        return result
    }

    private fun filePayloadSigningData(
        type: FrameType,
        senderId: String,
        recipientId: String,
        payload: String,
    ): ByteArray = "FILE|$PROTOCOL_VERSION|${type.name}|$senderId|$recipientId|$payload".encodeToByteArray()

    private fun streamStartAckId(transferId: String): String = "$transferId:start"

    private fun FileTransferStatus.isActive(): Boolean = when (this) {
        FileTransferStatus.PREPARING,
        FileTransferStatus.WAITING_FOR_ACCEPTANCE,
        FileTransferStatus.TRANSFERRING,
        -> true
        FileTransferStatus.COMPLETED,
        FileTransferStatus.REJECTED,
        FileTransferStatus.CANCELLED,
        FileTransferStatus.FAILED,
        -> false
    }

    private suspend fun createDeliveryAck(
        messageId: String,
        localIdentity: PublicIdentity,
        recipientId: String,
    ): TransportFrame {
        val signingData = ackSigningData(localIdentity.deviceId, recipientId, messageId)
        return TransportFrame(
            type = FrameType.DELIVERY_ACK,
            senderId = localIdentity.deviceId,
            recipientId = recipientId,
            payload = json.encodeToString(DeliveryAckPayload(messageId)),
            signature = Base64.getEncoder().encodeToString(secureMessageCodec.sign(signingData)),
        )
    }

    private fun verifyDeliveryAck(
        frame: TransportFrame,
        expectedMessageId: String,
        senderIdentity: PublicIdentity,
        localDeviceId: String,
    ) {
        require(frame.type == FrameType.DELIVERY_ACK) { "Peer did not acknowledge the message" }
        require(frame.senderId == senderIdentity.deviceId && frame.recipientId == localDeviceId) {
            "Delivery acknowledgement identity mismatch"
        }
        val acknowledgement = json.decodeFromString<DeliveryAckPayload>(frame.payload)
        require(acknowledgement.messageId == expectedMessageId) { "Acknowledged message does not match" }
        val signature = Base64.getDecoder().decode(requireNotNull(frame.signature))
        secureMessageCodec.verify(
            ackSigningData(frame.senderId, frame.recipientId, acknowledgement.messageId),
            signature,
            senderIdentity,
        )
    }

    private suspend fun exchange(peerId: String, request: TransportFrame): TransportFrame {
        val peer = requireNotNull(peerRepository.findPeer(peerId)) { "Peer was not discovered" }
        require(peer.availability == PeerAvailability.ONLINE) { "Peer is offline" }
        var response: TransportFrame? = null
        client.webSocket(host = peer.host, port = peer.port, path = CHAT_PATH) {
            response = exchangeFrame(request)
        }
        return requireNotNull(response) { "Peer closed without a response" }
    }

    private suspend fun WebSocketSession.exchangeFrame(request: TransportFrame): TransportFrame =
        withTimeout(EXCHANGE_TIMEOUT_MS) {
            send(Frame.Text(json.encodeToString(request)))
            receiveTransportFrame()
        }

    private suspend fun WebSocketSession.receiveTransportFrame(): TransportFrame =
        withTimeout(EXCHANGE_TIMEOUT_MS) {
            val response = incoming.receive() as? Frame.Text ?: error("Peer returned a non-text frame")
            json.decodeFromString<TransportFrame>(response.readText()).also(::throwIfError)
        }

    private suspend fun addCandidate(remote: PublicIdentity, local: PublicIdentity) {
        candidateMutex.withLock {
            markChangedIdentity(remote)
            val candidate = PairingCandidate(
                identity = remote,
                safetyCode = secureMessageCodec.calculateSafetyCode(local, remote),
            )
            mutableCandidates.value = mutableCandidates.value
                .filterNot { it.identity.deviceId == remote.deviceId } + candidate
        }
    }

    private suspend fun markChangedIdentity(remote: PublicIdentity) {
        val trusted = trustedIdentityRepository.find(remote.deviceId) ?: return
        val unchanged = trusted.encryptionPublicKey.contentEquals(remote.encryptionPublicKey) &&
            trusted.signingPublicKey.contentEquals(remote.signingPublicKey)
        if (unchanged) return
        val peer = peerRepository.findPeer(remote.deviceId) ?: return
        peerRepository.upsertPeer(peer.copy(trustState = TrustState.KEY_CHANGED))
    }

    private fun throwIfError(frame: TransportFrame) {
        if (frame.type != FrameType.ERROR) return
        val error = json.decodeFromString<ErrorPayload>(frame.payload)
        throw IllegalStateException("${error.code}: ${error.message}")
    }

    private fun TransportFrame.decodeIdentity(): PublicIdentity {
        val value = json.decodeFromString<PublicIdentityPayload>(payload)
        return PublicIdentity(
            deviceId = value.deviceId,
            displayName = value.displayName,
            encryptionPublicKey = Base64.getDecoder().decode(value.encryptionPublicKey),
            signingPublicKey = Base64.getDecoder().decode(value.signingPublicKey),
        )
    }

    private fun PublicIdentity.toPayload(): PublicIdentityPayload = PublicIdentityPayload(
        deviceId = deviceId,
        displayName = displayName,
        encryptionPublicKey = Base64.getEncoder().encodeToString(encryptionPublicKey),
        signingPublicKey = Base64.getEncoder().encodeToString(signingPublicKey),
    )

    private fun validateFrame(frame: TransportFrame) {
        require(frame.protocolVersion == PROTOCOL_VERSION) { "Unsupported protocol version" }
        require(ID_REGEX.matches(frame.senderId)) { "Invalid sender ID" }
        require(ID_REGEX.matches(frame.recipientId)) { "Invalid recipient ID" }
    }

    private fun validateIdentifier(value: String, field: String) {
        require(ID_REGEX.matches(value)) { "Invalid $field" }
    }

    private fun errorFrame(code: String, message: String): TransportFrame = TransportFrame(
        type = FrameType.ERROR,
        senderId = "unknown",
        recipientId = "unknown",
        payload = json.encodeToString(ErrorPayload(code, message)),
    )

    private suspend fun DefaultWebSocketServerSession.sendEncoded(frame: TransportFrame) {
        send(Frame.Text(json.encodeToString(frame)))
    }

    private fun ackSigningData(senderId: String, recipientId: String, messageId: String): ByteArray =
        "ACK|$PROTOCOL_VERSION|$senderId|$recipientId|$messageId".encodeToByteArray()

    private fun readReceiptSigningData(
        senderId: String,
        recipientId: String,
        messageIds: List<String>,
    ): ByteArray = "READ|$PROTOCOL_VERSION|$senderId|$recipientId|${messageIds.joinToString(",")}".encodeToByteArray()

    private data class IncomingSession(
        val transferId: String,
        val peerId: String,
        val fileName: String,
        val size: Long,
        val digest: MessageDigest,
        val tempFile: File? = null,
        val finalFile: File? = null,
        val outputStream: BufferedOutputStream? = null,
        val receivedBytes: Long = 0,
    ) {
        companion object {
            fun pending(offer: FileOfferPayload, peerId: String): IncomingSession = IncomingSession(
                transferId = offer.transferId,
                peerId = peerId,
                fileName = offer.fileName,
                size = offer.size,
                digest = MessageDigest.getInstance("SHA-256"),
            )
        }
    }

    private data class IncomingUpload(
        val transferId: String,
        val localIdentity: PublicIdentity,
        val senderIdentity: PublicIdentity,
    )

    private class TransferCancelledException : Exception()

    private companion object {
        const val DEFAULT_PORT = 45_892
        const val LISTEN_HOST = "0.0.0.0"
        const val CHAT_PATH = "/chat"
        const val PROTOCOL_VERSION = 1
        const val MAX_FRAME_SIZE_BYTES = 1L * 1_024L * 1_024L
        const val MAX_FRAME_TEXT_LENGTH = 64 * 1_024
        const val MAX_READ_RECEIPT_MESSAGE_COUNT = 128
        const val FILE_CHUNK_SIZE_BYTES = 512 * 1_024
        const val MAX_FILE_NAME_LENGTH = 255
        const val MIN_PRINTABLE_CHARACTER_CODE = 32
        const val MAX_FILE_SIZE_BYTES = 10L * 1_024L * 1_024L * 1_024L
        const val EVENT_BUFFER_SIZE = 64
        const val EXCHANGE_TIMEOUT_MS = 10_000L
        const val REACHABILITY_TIMEOUT_MS = 1_500L
        const val FILE_OFFER_TIMEOUT_MS = 5 * 60 * 1_000L
        const val SHUTDOWN_GRACE_MS = 500L
        const val SHUTDOWN_TIMEOUT_MS = 2_000L
        val ID_REGEX = Regex("^[A-Za-z0-9._:-]{1,128}$")
        val SHA256_REGEX = Regex("^[a-f0-9]{64}$")
    }
}
