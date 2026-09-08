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
import ink.x2.subnetdrop.domain.port.FileTransferSettingsRepository
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
import ink.x2.subnetdrop.network.protocol.FileStreamProgressPayload
import ink.x2.subnetdrop.network.protocol.FileStreamStartPayload
import ink.x2.subnetdrop.network.protocol.FrameType
import ink.x2.subnetdrop.network.protocol.PublicIdentityPayload
import ink.x2.subnetdrop.network.protocol.ReadReceiptPayload
import ink.x2.subnetdrop.network.protocol.TransportFrame
import ink.x2.subnetdrop.network.storage.IncomingFileStore
import ink.x2.subnetdrop.network.storage.IncomingFileTarget
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.engine.cio.CIO as ClientCio
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutgoingContent
import io.ktor.http.path
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCio
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class SubnetDropTransport(
    private val localIdentityService: LocalIdentityService,
    private val peerRepository: PeerRepository,
    private val trustedIdentityRepository: TrustedIdentityRepository,
    private val chatRepository: ChatRepository,
    private val secureMessageCodec: SecureMessageCodec,
    private val timestampProvider: TimestampProvider,
    private val idGenerator: IdGenerator,
    private val fileTransferSettingsRepository: FileTransferSettingsRepository,
    private val incomingFileStore: IncomingFileStore,
    override val listenerPort: Int = DEFAULT_PORT,
) : ChatTransport, PairingService, FileTransferService, PeerReachabilityProbe {
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = EVENT_BUFFER_SIZE)
    private val mutableCandidates = MutableStateFlow<List<PairingCandidate>>(emptyList())
    private val candidateMutex = Mutex()
    private val lifecycleMutex = Mutex()
    private val transferMutex = Mutex()
    private val outgoingTransferSlots = Semaphore(FileTransferService.MAX_PARALLEL_OUTGOING_TRANSFERS)
    private val json = Json { ignoreUnknownKeys = false }
    private val secureRandom = SecureRandom()
    private val client by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(ClientCio) {
            engine {
                // Large uploads use receiver progress as their stall timeout instead of CIO's 15-second total timeout.
                requestTimeout = 0
            }
            install(ClientWebSockets) {
                maxFrameSize = MAX_FRAME_SIZE_BYTES
                channels {
                    incoming = bounded(CONTROL_FRAME_QUEUE_CAPACITY)
                    outgoing = bounded(CONTROL_FRAME_QUEUE_CAPACITY)
                }
            }
        }
    }
    private var server: EmbeddedServer<*, *>? = null
    private val pendingDecisions = mutableMapOf<String, CompletableDeferred<FileDecisionPayload>>()
    private val incomingSessions = mutableMapOf<String, IncomingSession>()
    private val incomingUploadControls = mutableMapOf<String, IncomingUploadControl>()
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
        validateFileSize(file.size)
        val conversationId = conversationIdFor(localIdentityService.getProfile().deviceId, peerId)
        val transferId = idGenerator.generate().also { validateIdentifier(it, "transfer ID") }
        val transfer = FileTransfer(
            id = transferId,
            conversationId = conversationId,
            peerId = peerId,
            fileName = file.name,
            size = file.size,
            createdAt = timestampProvider.nowMillis(),
            contentType = file.contentType,
            direction = FileTransferDirection.OUTGOING,
            status = FileTransferStatus.PREPARING,
            localPath = source.path,
        )
        setTransfer(transfer)
        try {
            outgoingTransferSlots.withPermit {
                val decision = CompletableDeferred<FileDecisionPayload>()
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
                    val result = withTimeout(FILE_OFFER_TIMEOUT_MS) { decision.await() }
                    if (isTransferCancelled(transferId)) {
                        updateTransfer(transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
                        return
                    }
                    if (!result.accepted) {
                        updateTransfer(transferId) { it.copy(status = FileTransferStatus.REJECTED) }
                        return
                    }
                    val uploadToken = requireNotNull(result.uploadToken) {
                        "Accepted file decision did not include an upload token"
                    }
                    validateUploadToken(uploadToken)
                    updateTransfer(transferId) { it.copy(status = FileTransferStatus.TRANSFERRING) }
                    uploadFile(peerId, transferId, uploadToken, source)
                    updateTransfer(transferId) {
                        it.copy(status = FileTransferStatus.COMPLETED, transferredBytes = it.size)
                    }
                } finally {
                    transferMutex.withLock { pendingDecisions.remove(transferId) }
                }
            }
        } catch (exception: CancellationException) {
            updateTransferIfActive(transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
            throw exception
        } catch (_: TransferCancelledException) {
            updateTransferIfActive(transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
        } catch (exception: Exception) {
            updateTransferIfActive(transferId) {
                it.copy(status = FileTransferStatus.FAILED, error = exception.message ?: "Transfer failed")
            }
            throw exception
        }
    }

    override suspend fun sendFiles(peerId: String, files: List<LocalFile>) {
        require(files.isNotEmpty()) { "At least one file is required" }
        require(files.size <= FileTransferService.MAX_FILES_PER_BATCH) { "Too many files in one batch" }
        val failures = supervisorScope {
            files.map { file ->
                async {
                    try {
                        sendFile(peerId, file)
                        null
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        exception
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (failures.isNotEmpty()) {
            throw FileBatchTransferException(files.size, failures.size, failures.first())
        }
    }

    override suspend fun acceptOffer(transferId: String) {
        val offer = removeIncomingOffer(transferId)
        try {
            val uploadToken = prepareIncomingTransfer(offer)
            sendFileDecision(offer, accepted = true, uploadToken = uploadToken)
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
        sendFileDecision(offer, accepted = false, uploadToken = null)
    }

    override suspend fun cancelTransfer(transferId: String) {
        val transfer = findTransfer(transferId)
        transferMutex.withLock {
            cancelledTransfers += transferId
            pendingDecisions[transferId]?.complete(FileDecisionPayload(transferId, false, null))
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

    override suspend fun clearPeerTransfers(peerId: String) {
        val sessions = transferMutex.withLock {
            val transferIds = mutableTransfers.value
                .filter { it.peerId == peerId }
                .mapTo(mutableSetOf(), FileTransfer::id)
            cancelledTransfers += transferIds
            transferIds.forEach { transferId ->
                pendingDecisions.remove(transferId)?.complete(FileDecisionPayload(transferId, false, null))
                incomingUploadControls.remove(transferId)
            }
            val peerSessions = incomingSessions.values.filter { it.peerId == peerId }
            peerSessions.forEach { incomingSessions.remove(it.transferId) }
            mutableIncomingOffers.value = mutableIncomingOffers.value.filterNot { it.peerId == peerId }
            mutableTransfers.value = mutableTransfers.value.filterNot { it.peerId == peerId }
            peerSessions
        }
        sessions.forEach { session ->
            session.ioMutex.withLock { session.target?.discard() }
        }
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
            channels {
                incoming = bounded(CONTROL_FRAME_QUEUE_CAPACITY)
                outgoing = bounded(CONTROL_FRAME_QUEUE_CAPACITY)
            }
        }
        routing {
            webSocket(CHAT_PATH) {
                handleIncomingSession()
            }
            put(FILE_UPLOAD_PATH) {
                handleHttpFileUpload(call)
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
                                val startedUpload = beginIncomingUpload(frame)
                                registerIncomingUploadControl(startedUpload, this)
                                upload = startedUpload
                                sendEncoded(
                                    createDeliveryAck(
                                        streamStartAckId(startedUpload.transferId),
                                        startedUpload.localIdentity,
                                        startedUpload.senderIdentity.deviceId,
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
                                unregisterIncomingUploadControl(activeUpload.transferId, this)
                                upload = null
                            } else {
                                val response = handleIncomingFrame(frame)
                                response?.let { sendEncoded(it) }
                            }
                        }
                        is Frame.Binary -> error("File bytes must use the HTTP upload endpoint")
                        else -> Unit
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    upload?.let {
                        failIncomingUpload(it.transferId, exception)
                        unregisterIncomingUploadControl(it.transferId, this)
                    }
                    upload = null
                    sendEncoded(errorFrame("INVALID_REQUEST", exception.message ?: "Invalid request"))
                }
            }
        } finally {
            upload?.let {
                failIncomingUpload(it.transferId, IllegalStateException("File control connection closed"))
                unregisterIncomingUploadControl(it.transferId, this)
            }
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
            FrameType.FILE_STREAM_PROGRESS,
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
        validateFileSize(offer.size)
        val peer = requireNotNull(peerRepository.findPeer(frame.senderId)) { "Sender was not discovered" }
        val incomingOffer = IncomingFileOffer(
            transferId = offer.transferId,
            peerId = frame.senderId,
            peerDisplayName = peer.displayName,
            fileName = offer.fileName,
            size = offer.size,
            contentType = offer.contentType,
        )
        val requiresConfirmation = fileTransferSettingsRepository.settings.value.requireIncomingConfirmation
        transferMutex.withLock {
            val duplicate = mutableIncomingOffers.value.any { it.transferId == offer.transferId } ||
                mutableTransfers.value.any { it.id == offer.transferId }
            require(!duplicate) { "Transfer already exists" }
            if (requiresConfirmation) {
                mutableIncomingOffers.value = mutableIncomingOffers.value + incomingOffer
            }
            mutableTransfers.value = mutableTransfers.value + FileTransfer(
                id = offer.transferId,
                conversationId = conversationIdFor(localIdentity.deviceId, frame.senderId),
                peerId = frame.senderId,
                fileName = offer.fileName,
                size = offer.size,
                createdAt = timestampProvider.nowMillis(),
                contentType = offer.contentType,
                direction = FileTransferDirection.INCOMING,
                status = if (requiresConfirmation) {
                    FileTransferStatus.WAITING_FOR_ACCEPTANCE
                } else {
                    FileTransferStatus.PREPARING
                },
            )
            incomingSessions[offer.transferId] = IncomingSession.pending(offer, frame.senderId)
        }
        if (!requiresConfirmation) {
            try {
                val uploadToken = prepareIncomingTransfer(incomingOffer)
                sendFileDecision(incomingOffer, accepted = true, uploadToken = uploadToken)
            } catch (exception: Exception) {
                discardIncomingSession(offer.transferId)
                updateTransfer(offer.transferId) {
                    it.copy(status = FileTransferStatus.FAILED, error = exception.message ?: "Acceptance failed")
                }
                throw exception
            }
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
        if (decision.accepted) validateUploadToken(requireNotNull(decision.uploadToken))
        if (!decision.accepted) require(decision.uploadToken == null) { "Rejected transfer included an upload token" }
        val pending = transferMutex.withLock { pendingDecisions[decision.transferId] }
            ?: error("Transfer offer is no longer pending")
        require(findTransfer(decision.transferId).peerId == frame.senderId) { "Transfer peer mismatch" }
        require(pending.complete(decision)) { "Transfer decision was already received" }
        return createDeliveryAck(decision.transferId, localIdentity, senderIdentity.deviceId)
    }

    private suspend fun beginIncomingUpload(frame: TransportFrame): IncomingUpload {
        validateFrame(frame)
        val localIdentity = localIdentityService.get()
        require(frame.recipientId == localIdentity.deviceId) { "Frame is addressed to another device" }
        val senderIdentity = trustedSender(frame.senderId)
        val start = decodeSignedFilePayload<FileStreamStartPayload>(frame, senderIdentity)
        validateIdentifier(start.transferId, "transfer ID")
        validateUploadToken(start.uploadToken)
        val transfer = findTransfer(start.transferId)
        require(transfer.peerId == frame.senderId) { "Transfer peer mismatch" }
        require(transfer.status == FileTransferStatus.TRANSFERRING) { "Transfer was not accepted" }
        transferMutex.withLock {
            val session = requireNotNull(incomingSessions[start.transferId]) { "Transfer session does not exist" }
            require(session.uploadToken == start.uploadToken) { "Upload token does not match transfer" }
            require(timestampProvider.nowMillis() <= requireNotNull(session.uploadTokenExpiresAt)) {
                "Upload token has expired"
            }
            require(start.transferId !in incomingUploadControls) { "Upload control connection already exists" }
        }
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

    private suspend fun registerIncomingUploadControl(
        upload: IncomingUpload,
        controlSession: DefaultWebSocketServerSession,
    ) {
        transferMutex.withLock {
            require(upload.transferId !in incomingUploadControls) { "Upload control connection already exists" }
            incomingUploadControls[upload.transferId] = IncomingUploadControl(upload, controlSession)
        }
    }

    private suspend fun unregisterIncomingUploadControl(
        transferId: String,
        controlSession: DefaultWebSocketServerSession,
    ) {
        transferMutex.withLock {
            if (incomingUploadControls[transferId]?.session === controlSession) {
                incomingUploadControls.remove(transferId)
            }
        }
    }

    private suspend fun handleHttpFileUpload(call: ApplicationCall) {
        val request = try {
            authenticateHttpUpload(call)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpUploadException) {
            call.respondText(exception.message.orEmpty(), status = exception.status)
            return
        } catch (exception: Exception) {
            call.respondText("Invalid upload request", status = HttpStatusCode.Forbidden)
            return
        }
        try {
            receiveHttpFileBody(call, request)
            call.respondText("", status = HttpStatusCode.Accepted)
        } catch (exception: CancellationException) {
            failIncomingUpload(request.session.transferId, exception)
            throw exception
        } catch (exception: Exception) {
            failIncomingUpload(request.session.transferId, exception)
            call.respondText(
                exception.message ?: "File upload failed",
                status = HttpStatusCode.UnprocessableEntity,
            )
        }
    }

    private suspend fun authenticateHttpUpload(call: ApplicationCall): AuthenticatedHttpUpload {
        val transferId = requireHttpHeader(call, HEADER_TRANSFER_ID)
        val senderId = requireHttpHeader(call, HEADER_SENDER_ID)
        val signatureValue = requireHttpHeader(call, HEADER_UPLOAD_SIGNATURE)
        validateIdentifier(transferId, "transfer ID")
        validateIdentifier(senderId, "sender ID")
        val authorization = requireHttpHeader(call, HttpHeaders.Authorization)
        requireHttp(authorization.startsWith(BEARER_PREFIX), "Invalid upload authorization")
        val uploadToken = authorization.removePrefix(BEARER_PREFIX)
        validateUploadToken(uploadToken)
        val contentLength = requireHttpHeader(call, HttpHeaders.ContentLength).toLongOrNull()
            ?: throw HttpUploadException(HttpStatusCode.BadRequest, "Invalid content length")
        val localIdentity = localIdentityService.get()
        val senderIdentity = trustedSender(senderId)
        val signature = runCatching { Base64.getDecoder().decode(signatureValue) }
            .getOrElse { throw HttpUploadException(HttpStatusCode.Forbidden, "Invalid upload signature") }
        runCatching {
            secureMessageCodec.verify(
                httpUploadSigningData(
                    senderId = senderId,
                    recipientId = localIdentity.deviceId,
                    transferId = transferId,
                    uploadToken = uploadToken,
                    contentLength = contentLength,
                ),
                signature,
                senderIdentity,
            )
        }.getOrElse { throw HttpUploadException(HttpStatusCode.Forbidden, "Invalid upload signature") }
        return transferMutex.withLock {
            val current = incomingSessions[transferId]
                ?: throw HttpUploadException(HttpStatusCode.NotFound, "Transfer session does not exist")
            val control = incomingUploadControls[transferId]
                ?: throw HttpUploadException(HttpStatusCode.Conflict, "Upload control connection is not active")
            requireHttp(current.peerId == senderId, "Transfer peer mismatch")
            requireHttp(control.upload.senderIdentity.deviceId == senderId, "Transfer control identity mismatch")
            requireHttp(current.uploadToken == uploadToken, "Upload token does not match transfer")
            requireHttp(
                timestampProvider.nowMillis() <= requireNotNull(current.uploadTokenExpiresAt),
                "Upload token has expired",
            )
            requireHttp(contentLength == current.size, "Content length does not match offer")
            requireHttp(!current.uploadStarted, "Upload token was already consumed")
            requireHttp(current.target != null, "Transfer destination is not prepared")
            requireHttp(transferId !in cancelledTransfers, "Transfer was cancelled")
            val claimed = current.copy(uploadStarted = true)
            incomingSessions[transferId] = claimed
            AuthenticatedHttpUpload(claimed, control)
        }
    }

    private suspend fun receiveHttpFileBody(
        call: ApplicationCall,
        request: AuthenticatedHttpUpload,
    ) {
        val target = requireNotNull(request.session.target)
        val digest = MessageDigest.getInstance("SHA-256")
        var receivedBytes = 0L
        var reportedBytes = 0L
        request.session.ioMutex.withLock {
            withContext(Dispatchers.IO) {
                val body = call.receiveChannel()
                val buffer = ByteArray(FILE_IO_BUFFER_SIZE_BYTES)
                while (true) {
                    val count = body.readAvailable(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    require(receivedBytes + count <= request.session.size) { "File exceeds offered size" }
                    target.outputSink.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    receivedBytes += count
                    if (receivedBytes - reportedBytes >= FILE_PROGRESS_WINDOW_BYTES) {
                        target.outputSink.emit()
                        publishIncomingProgress(request, receivedBytes)
                        reportedBytes = receivedBytes
                    }
                }
                require(receivedBytes == request.session.size) {
                    "Received byte count $receivedBytes does not match offered size ${request.session.size}"
                }
                target.outputSink.emit()
            }
            if (receivedBytes > reportedBytes) {
                publishIncomingProgress(request, receivedBytes)
            }
            val receivedSha256 = digest.digest().toHex()
            transferMutex.withLock {
                val current = requireNotNull(incomingSessions[request.session.transferId]) {
                    "Transfer session was cancelled"
                }
                require(current.ioMutex === request.session.ioMutex) { "Transfer session changed" }
                incomingSessions[request.session.transferId] = current.copy(
                    receivedBytes = receivedBytes,
                    receivedSha256 = receivedSha256,
                )
            }
        }
    }

    private suspend fun publishIncomingProgress(
        request: AuthenticatedHttpUpload,
        receivedBytes: Long,
    ) {
        transferMutex.withLock {
            val current = requireNotNull(incomingSessions[request.session.transferId]) {
                "Transfer session was cancelled"
            }
            require(current.ioMutex === request.session.ioMutex) { "Transfer session changed" }
            require(receivedBytes in current.receivedBytes..current.size) { "Invalid received byte count" }
            incomingSessions[request.session.transferId] = current.copy(receivedBytes = receivedBytes)
            mutableTransfers.value = mutableTransfers.value.map { transfer ->
                if (transfer.id == request.session.transferId) {
                    transfer.copy(transferredBytes = receivedBytes)
                } else {
                    transfer
                }
            }
        }
        request.control.session.sendEncoded(
            createSignedFileFrame(
                type = FrameType.FILE_STREAM_PROGRESS,
                sender = request.control.upload.localIdentity,
                recipient = request.control.upload.senderIdentity,
                payload = json.encodeToString(
                    FileStreamProgressPayload(request.session.transferId, receivedBytes),
                ),
            ),
        )
    }

    private fun requireHttpHeader(call: ApplicationCall, name: String): String =
        call.request.headers[name] ?: throw HttpUploadException(HttpStatusCode.BadRequest, "Missing $name header")

    private fun requireHttp(condition: Boolean, message: String) {
        if (!condition) throw HttpUploadException(HttpStatusCode.Forbidden, message)
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
            pendingDecisions[cancellation.transferId]?.complete(
                FileDecisionPayload(cancellation.transferId, false, null),
            )
        }
        removeOfferIfPresent(cancellation.transferId)
        discardIncomingSession(cancellation.transferId)
        updateTransfer(cancellation.transferId) { it.copy(status = FileTransferStatus.CANCELLED) }
        return createDeliveryAck(cancellation.transferId, localIdentity, senderIdentity.deviceId)
    }

    private suspend fun uploadFile(
        peerId: String,
        transferId: String,
        uploadToken: String,
        source: File,
    ) {
        val peer = requireNotNull(peerRepository.findPeer(peerId)) { "Peer was not discovered" }
        require(peer.availability == PeerAvailability.ONLINE) { "Peer is offline" }
        val localIdentity = localIdentityService.get()
        val recipientIdentity = requireNotNull(trustedIdentityRepository.find(peerId)) { "Peer is not trusted" }
        val totalBytes = source.length()
        var confirmedBytes = 0L
        client.webSocket(host = peer.host, port = peer.port, path = CHAT_PATH) {
            val startResponse = exchangeFrame(
                createSignedFileFrame(
                    type = FrameType.FILE_STREAM_START,
                    sender = localIdentity,
                    recipient = recipientIdentity,
                    payload = json.encodeToString(FileStreamStartPayload(transferId, uploadToken)),
                ),
            )
            verifyDeliveryAck(
                startResponse,
                streamStartAckId(transferId),
                recipientIdentity,
                localIdentity.deviceId,
            )
            val httpUpload = async {
                uploadFileOverHttp(
                    peer = peer,
                    transferId = transferId,
                    uploadToken = uploadToken,
                    source = source,
                    localIdentity = localIdentity,
                    recipientIdentity = recipientIdentity,
                )
            }
            while (confirmedBytes < totalBytes) {
                val rawFrame = withTimeout(FILE_PROGRESS_TIMEOUT_MS) { incoming.receive() }
                val frame = rawFrame as? Frame.Text ?: error("Peer returned a non-text file progress frame")
                val progress = json.decodeFromString<TransportFrame>(frame.readText()).also(::throwIfError)
                val receivedBytes = verifyFileProgress(
                    frame = progress,
                    transferId = transferId,
                    previousBytes = confirmedBytes,
                    totalBytes = totalBytes,
                    senderIdentity = recipientIdentity,
                    localDeviceId = localIdentity.deviceId,
                )
                confirmedBytes = receivedBytes
                updateTransfer(transferId) { transfer -> transfer.copy(transferredBytes = receivedBytes) }
            }
            val digest = withTimeout(EXCHANGE_TIMEOUT_MS) { httpUpload.await() }
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

    private suspend fun uploadFileOverHttp(
        peer: Peer,
        transferId: String,
        uploadToken: String,
        source: File,
        localIdentity: PublicIdentity,
        recipientIdentity: PublicIdentity,
    ): String {
        val totalBytes = source.length()
        val digest = MessageDigest.getInstance("SHA-256")
        var streamedBytes = 0L
        val requestSignature = Base64.getEncoder().encodeToString(
            secureMessageCodec.sign(
                httpUploadSigningData(
                    senderId = localIdentity.deviceId,
                    recipientId = recipientIdentity.deviceId,
                    transferId = transferId,
                    uploadToken = uploadToken,
                    contentLength = totalBytes,
                ),
            ),
        )
        val response = client.put {
            url {
                protocol = URLProtocol.HTTP
                host = peer.host
                port = peer.port
                path(FILE_UPLOAD_PATH)
            }
            headers {
                append(HttpHeaders.Authorization, "$BEARER_PREFIX$uploadToken")
                append(HEADER_TRANSFER_ID, transferId)
                append(HEADER_SENDER_ID, localIdentity.deviceId)
                append(HEADER_UPLOAD_SIGNATURE, requestSignature)
            }
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType: ContentType = ContentType.Application.OctetStream
                override val contentLength: Long = totalBytes

                override suspend fun writeTo(channel: ByteWriteChannel) {
                    withContext(Dispatchers.IO) {
                        val buffer = ByteArray(FILE_IO_BUFFER_SIZE_BYTES)
                        source.inputStream().use { input ->
                            while (true) {
                                if (isTransferCancelled(transferId)) throw TransferCancelledException()
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                require(streamedBytes + count <= totalBytes) { "Selected file grew during transfer" }
                                channel.writeFully(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                streamedBytes += count
                            }
                        }
                        channel.flush()
                    }
                }
            })
        }
        val responseText = response.bodyAsText()
        require(response.status == HttpStatusCode.Accepted) {
            "HTTP ${response.status.value}: ${responseText.ifBlank { "File upload failed" }}"
        }
        require(streamedBytes == totalBytes && source.length() == totalBytes) {
            "Selected file changed during transfer"
        }
        return digest.digest().toHex()
    }

    private fun verifyFileProgress(
        frame: TransportFrame,
        transferId: String,
        previousBytes: Long,
        totalBytes: Long,
        senderIdentity: PublicIdentity,
        localDeviceId: String,
    ): Long {
        validateFrame(frame)
        require(frame.type == FrameType.FILE_STREAM_PROGRESS) { "Peer did not confirm file progress" }
        require(frame.senderId == senderIdentity.deviceId && frame.recipientId == localDeviceId) {
            "File progress identity mismatch"
        }
        val progress = decodeSignedFilePayload<FileStreamProgressPayload>(frame, senderIdentity)
        require(progress.transferId == transferId) { "File progress transfer does not match" }
        require(progress.receivedBytes in (previousBytes + 1)..totalBytes) {
            "Peer confirmed unexpected file progress"
        }
        return progress.receivedBytes
    }

    private suspend fun sendFileDecision(
        offer: IncomingFileOffer,
        accepted: Boolean,
        uploadToken: String?,
    ) {
        sendSignedFileRequest(
            peerId = offer.peerId,
            type = FrameType.FILE_DECISION,
            acknowledgementId = offer.transferId,
            payload = json.encodeToString(FileDecisionPayload(offer.transferId, accepted, uploadToken)),
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

    private fun validateFileSize(fileSizeBytes: Long) {
        val configuredLimit = fileTransferSettingsRepository.settings.value.maxFileSizeBytes
        require(fileSizeBytes in 0..configuredLimit) {
            "File exceeds this device's configured maximum size"
        }
    }

    private suspend fun failIncomingUpload(transferId: String, exception: Exception) {
        discardIncomingSession(transferId)
        updateTransferIfActive(transferId) { transfer ->
            transfer.copy(status = FileTransferStatus.FAILED, error = exception.message ?: "File upload failed")
        }
    }

    private suspend fun completeIncomingTransfer(transferId: String, expectedSha256: String) {
        val session = transferMutex.withLock {
            requireNotNull(incomingSessions.remove(transferId)) { "Transfer session does not exist" }
        }
        val target = requireNotNull(session.target)
        try {
            session.ioMutex.withLock {
                withContext(Dispatchers.IO) {
                    target.outputSink.run {
                        flush()
                        close()
                    }
                    require(session.receivedBytes == session.size) {
                        "Received byte count ${session.receivedBytes} does not match offered size ${session.size}"
                    }
                    target.persistedSizeOrNull()?.let { persistedSize ->
                        require(persistedSize == session.size) {
                            "Stored file size $persistedSize does not match offered size ${session.size}"
                        }
                    }
                    require(session.receivedSha256 == expectedSha256) {
                        "Received file checksum does not match sender"
                    }
                    target.publish()
                }
            }
            updateTransfer(transferId) {
                it.copy(
                    status = FileTransferStatus.COMPLETED,
                    transferredBytes = it.size,
                    localPath = target.finalPath,
                )
            }
        } catch (exception: Exception) {
            target.discard()
            updateTransfer(transferId) {
                it.copy(status = FileTransferStatus.FAILED, error = exception.message ?: "File validation failed")
            }
            throw exception
        }
    }

    private suspend fun prepareIncomingTransfer(offer: IncomingFileOffer): String {
        val session = createIncomingSession(offer)
        updateTransfer(offer.transferId) {
            it.copy(
                status = FileTransferStatus.TRANSFERRING,
                localPath = requireNotNull(session.target).temporaryPath,
            )
        }
        return requireNotNull(session.uploadToken)
    }

    private suspend fun createIncomingSession(offer: IncomingFileOffer): IncomingSession {
        val (pending, reservedFinalPaths) = transferMutex.withLock {
            val current = requireNotNull(incomingSessions[offer.transferId]) { "Transfer offer does not exist" }
            current to incomingSessions.values.mapNotNull { it.target?.finalPath }.toSet()
        }
        val target = incomingFileStore.create(
            saveDirectory = fileTransferSettingsRepository.settings.value.saveDirectory,
            transferId = offer.transferId,
            fileName = offer.fileName,
            contentType = pending.contentType,
            reservedFinalPaths = reservedFinalPaths,
        )
        return try {
            transferMutex.withLock {
                val current = requireNotNull(incomingSessions[offer.transferId]) { "Transfer offer no longer exists" }
                require(current.target == null) { "Transfer session was already accepted" }
                current.copy(
                    target = target,
                    uploadToken = newUploadToken(),
                    uploadTokenExpiresAt = timestampProvider.nowMillis() + UPLOAD_TOKEN_TTL_MS,
                ).also { incomingSessions[offer.transferId] = it }
            }
        } catch (exception: Exception) {
            target.discard()
            throw exception
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
        val session = transferMutex.withLock {
            incomingUploadControls.remove(transferId)
            incomingSessions.remove(transferId)
        }
        session?.ioMutex?.withLock {
            session.target?.discard()
        }
    }

    private suspend fun cleanupTransferSessions() {
        val sessions = transferMutex.withLock {
            val activeSessions = incomingSessions.values.toList()
            cancelledTransfers += mutableTransfers.value
                .filter { it.status.isActive() }
                .map(FileTransfer::id)
            pendingDecisions.forEach { (transferId, decision) ->
                decision.complete(FileDecisionPayload(transferId, false, null))
            }
            incomingSessions.clear()
            incomingUploadControls.clear()
            mutableIncomingOffers.value = emptyList()
            mutableTransfers.value = mutableTransfers.value.map { transfer ->
                if (transfer.status.isActive()) transfer.copy(status = FileTransferStatus.CANCELLED) else transfer
            }
            activeSessions
        }
        sessions.forEach { session ->
            session.ioMutex.withLock {
                session.target?.discard()
            }
        }
    }

    private suspend fun setTransfer(transfer: FileTransfer) {
        transferMutex.withLock {
            mutableTransfers.value = mutableTransfers.value.filterNot { it.id == transfer.id } + transfer
        }
    }

    private suspend fun updateTransfer(transferId: String, transform: (FileTransfer) -> FileTransfer) {
        val updatedTransfer = transferMutex.withLock {
            var found = false
            var updated: FileTransfer? = null
            mutableTransfers.value = mutableTransfers.value.map { transfer ->
                if (transfer.id == transferId) {
                    found = true
                    transform(transfer).also { updated = it }
                } else {
                    transfer
                }
            }
            if (!found) {
                check(transferId in cancelledTransfers) { "Transfer does not exist" }
                null
            } else {
                requireNotNull(updated)
            }
        }
        if (updatedTransfer != null && !updatedTransfer.status.isActive()) {
            chatRepository.saveFileMessage(updatedTransfer)
        }
    }

    private suspend fun updateTransferIfActive(
        transferId: String,
        transform: (FileTransfer) -> FileTransfer,
    ) {
        val updatedTransfer = transferMutex.withLock {
            var updated: FileTransfer? = null
            mutableTransfers.value = mutableTransfers.value.map { transfer ->
                if (transfer.id == transferId && transfer.status.isActive()) {
                    transform(transfer).also { updated = it }
                } else {
                    transfer
                }
            }
            updated
        }
        if (updatedTransfer != null && !updatedTransfer.status.isActive()) {
            chatRepository.saveFileMessage(updatedTransfer)
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

    private fun filePayloadSigningData(
        type: FrameType,
        senderId: String,
        recipientId: String,
        payload: String,
    ): ByteArray = "FILE|$PROTOCOL_VERSION|${type.name}|$senderId|$recipientId|$payload".encodeToByteArray()

    private fun httpUploadSigningData(
        senderId: String,
        recipientId: String,
        transferId: String,
        uploadToken: String,
        contentLength: Long,
    ): ByteArray =
        "HTTP_UPLOAD|$PROTOCOL_VERSION|$senderId|$recipientId|$transferId|$uploadToken|$contentLength"
            .encodeToByteArray()

    private fun validateUploadToken(uploadToken: String) {
        require(UPLOAD_TOKEN_REGEX.matches(uploadToken)) { "Invalid upload token" }
    }

    private fun newUploadToken(): String = ByteArray(UPLOAD_TOKEN_SIZE_BYTES)
        .also(secureRandom::nextBytes)
        .let(Base64.getUrlEncoder().withoutPadding()::encodeToString)

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

    private suspend fun WebSocketSession.exchangeFrame(
        request: TransportFrame,
        timeoutMillis: Long = EXCHANGE_TIMEOUT_MS,
    ): TransportFrame =
        withTimeout(timeoutMillis) {
            send(Frame.Text(json.encodeToString(request)))
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
        val contentType: String?,
        val size: Long,
        val target: IncomingFileTarget? = null,
        val uploadToken: String? = null,
        val uploadTokenExpiresAt: Long? = null,
        val uploadStarted: Boolean = false,
        val receivedBytes: Long = 0,
        val receivedSha256: String? = null,
        val ioMutex: Mutex = Mutex(),
    ) {
        companion object {
            fun pending(offer: FileOfferPayload, peerId: String): IncomingSession = IncomingSession(
                transferId = offer.transferId,
                peerId = peerId,
                fileName = offer.fileName,
                contentType = offer.contentType,
                size = offer.size,
            )
        }
    }

    private data class IncomingUpload(
        val transferId: String,
        val localIdentity: PublicIdentity,
        val senderIdentity: PublicIdentity,
    )

    private data class IncomingUploadControl(
        val upload: IncomingUpload,
        val session: DefaultWebSocketServerSession,
    )

    private data class AuthenticatedHttpUpload(
        val session: IncomingSession,
        val control: IncomingUploadControl,
    )

    private class HttpUploadException(
        val status: HttpStatusCode,
        message: String,
    ) : IllegalArgumentException(message)

    private class TransferCancelledException : Exception()

    private class FileBatchTransferException(
        totalCount: Int,
        failedCount: Int,
        cause: Exception,
    ) : Exception("$failedCount of $totalCount file transfers failed", cause)

    private companion object {
        const val DEFAULT_PORT = 45_892
        const val LISTEN_HOST = "0.0.0.0"
        const val CHAT_PATH = "/chat"
        const val FILE_UPLOAD_PATH = "/api/files/upload"
        const val PROTOCOL_VERSION = 2
        const val MAX_FRAME_SIZE_BYTES = 1L * 1_024L * 1_024L
        const val MAX_FRAME_TEXT_LENGTH = 64 * 1_024
        const val MAX_READ_RECEIPT_MESSAGE_COUNT = 128
        const val FILE_IO_BUFFER_SIZE_BYTES = 512 * 1_024
        const val FILE_PROGRESS_WINDOW_BYTES = 4L * 1_024L * 1_024L
        const val CONTROL_FRAME_QUEUE_CAPACITY = 16
        const val UPLOAD_TOKEN_SIZE_BYTES = 32
        const val UPLOAD_TOKEN_TTL_MS = 5 * 60 * 1_000L
        const val BEARER_PREFIX = "Bearer "
        const val HEADER_TRANSFER_ID = "X-SubnetDrop-Transfer-Id"
        const val HEADER_SENDER_ID = "X-SubnetDrop-Sender-Id"
        const val HEADER_UPLOAD_SIGNATURE = "X-SubnetDrop-Signature"
        const val MAX_FILE_NAME_LENGTH = 255
        const val MIN_PRINTABLE_CHARACTER_CODE = 32
        const val EVENT_BUFFER_SIZE = 64
        const val EXCHANGE_TIMEOUT_MS = 10_000L
        const val FILE_PROGRESS_TIMEOUT_MS = 60_000L
        const val REACHABILITY_TIMEOUT_MS = 1_500L
        const val FILE_OFFER_TIMEOUT_MS = 5 * 60 * 1_000L
        const val SHUTDOWN_GRACE_MS = 500L
        const val SHUTDOWN_TIMEOUT_MS = 2_000L
        val ID_REGEX = Regex("^[A-Za-z0-9._:-]{1,128}$")
        val SHA256_REGEX = Regex("^[a-f0-9]{64}$")
        val UPLOAD_TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
