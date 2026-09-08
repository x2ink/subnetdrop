# High-speed file transfer v1

## Goal

Add one-to-one file transfer between trusted SubnetDrop peers without a central server. The feature is inspired by
[LocalSend's metadata-first upload session](https://github.com/localsend/protocol), while retaining SubnetDrop's existing
device identity and trust model.

## Product behavior

- A sender selects up to 50 local files from an open conversation. On desktop, dropping regular files anywhere inside
  the open chat page provides the same input. Every file is an independent transfer and message.
- At most three outgoing transfers run concurrently across all batches; queued items remain visible as `PREPARING`.
- Incoming offers are accepted automatically by default. The receiver can enable per-file confirmation in Settings;
  confirmation mode exposes accept and reject actions before any content bytes are sent.
- Each transfer appears in the conversation timeline as a directional file-message card, interleaved with text by its
  creation time instead of being rendered in a separate transfer panel.
- Accepted files show live byte progress on both devices.
- The receiver can choose a persistent save directory in Settings. The initial value is the platform download directory
  under a `SubnetDrop` folder.
- The per-file size limit is persisted per device. It defaults to 10 GiB and can be configured from 1 to 1024 GiB;
  sender and receiver enforce their own limits independently.
- A completed incoming file, or the sender's existing source file, can be opened with the operating system's default
  application from its file-message card.
- Android stores incoming files in the public `Download/SubnetDrop` MediaStore collection by default. An entry remains
  pending and invisible to other applications until length and SHA-256 validation succeeds; cancellation or failure
  deletes it. A user-selected SAF directory remains supported and takes precedence over the default.
- Completed, failed, rejected and cancelled transfers are persisted as conversation history with an explicit state.
- A completed file message stores its local source/destination path. If that path is missing, the card displays `已失效`
  and does not invoke the operating-system opener.
- File contents are streamed in bounded chunks and are never loaded into memory as one buffer.
- Active progress is process-local. Terminal file-message cards are restored from SQLDelight after restart; successfully
  received files remain on disk independently of their database metadata.

## Domain API

```kotlin
interface FileTransferService {
    val incomingOffers: StateFlow<List<IncomingFileOffer>>
    val transfers: StateFlow<List<FileTransfer>>

    suspend fun sendFile(peerId: String, file: LocalFile)
    suspend fun sendFiles(peerId: String, files: List<LocalFile>)
    suspend fun acceptOffer(transferId: String)
    suspend fun rejectOffer(transferId: String)
    suspend fun cancelTransfer(transferId: String)
}

interface FileTransferSettingsRepository {
    val settings: StateFlow<FileTransferSettings>
    suspend fun updateSaveDirectory(path: String)
    suspend fun updateRequireIncomingConfirmation(required: Boolean)
    suspend fun updateMaxFileSizeBytes(maxFileSizeBytes: Long)
}
```

## Protocol

The protocol uses the existing Ktor WebSocket endpoint and trusted peer identities. Control requests use short-lived
request/response sessions. After acceptance, all chunks for one file reuse a single upload session.

```mermaid
sequenceDiagram
    participant S as Sender
    participant R as Receiver
    S->>R: Signed FILE_OFFER metadata
    R-->>S: Signed delivery ACK
    alt Confirmation is disabled (default)
        R->>R: Prepare destination automatically
    else Confirmation is enabled
        R->>R: User accepts or rejects
    end
    R->>S: Signed FILE_DECISION
    S-->>R: Signed delivery ACK
    S->>R: Open upload WebSocket
    S->>R: Signed FILE_STREAM_START
    loop Sequential 512 KiB chunks on the same connection
        S->>R: Plain binary frame
        S->>S: Update SHA-256
        R->>R: Validate byte count and update SHA-256
        opt At each bounded progress window
            S->>R: Signed FILE_STREAM_PROGRESS checkpoint
            R-->>S: Signed FILE_STREAM_PROGRESS with confirmed bytes
            S->>S: Publish receiver-confirmed progress
        end
    end
    S->>R: Signed FILE_STREAM_COMPLETE with SHA-256
    R->>R: Verify total bytes and SHA-256, then rename temporary file
    R-->>S: Signed delivery ACK
```

File control payloads are authenticated with Ed25519. File bytes are intentionally not encrypted: they are sent as raw
binary WebSocket frames without Base64 conversion or per-chunk acknowledgement. Both devices calculate SHA-256 while
streaming, and the signed completion frame binds the sender's final digest to the authenticated transfer. This detects
modification but does not hide the file from an observer on the same network.

The sender must not expose bytes merely queued in its local WebSocket channel as delivered progress. After each 4 MiB
window, it sends a signed progress checkpoint on the ordered upload connection. The receiver acknowledges only when all
preceding chunks have been validated and written to the temporary-file sink. Both cards then publish that confirmed byte
count. This bounds drift and memory without adding a round trip for every 512 KiB chunk. Client outgoing and server incoming
WebSocket queues are bounded so TCP backpressure reaches the source reader instead of buffering an entire large file.

## Limits and validation

- Trusted peers only.
- One file per transfer session, up to 50 files per picker batch and three active outgoing sessions per process.
- Configurable per-device file size: 1–1024 GiB, default 10 GiB. The 1024 GiB ceiling is also the protocol hard limit.
- Binary chunk size: 512 KiB.
- Progress acknowledgement window: 4 MiB, with a final checkpoint before completion when needed.
- Maximum file name length: 255 characters.
- File names are reduced to a leaf name; path separators, blank names and control characters are rejected.
- Chunks must arrive exactly once and in ascending order.
- A transfer must not write more bytes than declared in its offer.
- Rejected, cancelled, timed-out or invalid transfers delete their temporary data.
- Existing destination files are preserved by selecting a collision-free final name.
- Automatic acceptance means a trusted peer can consume receiver bandwidth and disk space. Users who do not want that
  policy must enable per-file confirmation.

## Platform behavior

- Android, macOS and Windows use FileKit's Compose Multiplatform launchers and platform-native file/directory dialogs.
- macOS and Windows additionally accept the operating system's file-list drag payload on an open chat page. Dragged
  files use the same batch-count, metadata and configured size validation as picker selections; directories and other
  payload types are rejected instead of being interpreted as files.
- Android provider-backed selections are size-checked before FileKit copies them into app cache for the JVM transport.
- Android retains access to a selected Storage Access Framework directory. Desktop stores the selected path directly.
- Desktop initially uses `~/Downloads/SubnetDrop`. Android uses MediaStore to publish completed files in the public
  `Download/SubnetDrop` collection, so other applications can open them without access to the app-private directory.
- A receiver-side file card is openable only after final length and SHA-256 validation publishes the completed file.
- Files that are still being received cannot be opened from the receiver-side message card.

## Acceptance criteria

1. A trusted desktop peer can select and offer a file from a conversation.
2. With confirmation disabled, a valid offer from a trusted peer is accepted automatically; with confirmation enabled,
   the receiver can reject it without receiving file bytes.
3. Both sides expose progress and terminal state.
4. A successful receiver file has the exact byte count and SHA-256 digest of the source.
5. Tampered, reordered, oversized and untrusted traffic is rejected without publishing a destination file.
6. JVM unit/integration tests cover accepted multi-chunk transfer, rejection and tamper/order validation.
7. Android, macOS and Windows file selection, destination handling and cross-platform transfer are verified on their
   target systems before release.
8. The confirmation preference and save directory survive application restart.
9. Terminal file messages and local paths survive application restart; duplicate live/history IDs render once.
10. Missing completed files display `已失效` and cannot invoke the operating-system opener.
11. A multi-file batch runs up to three independent transfers concurrently; one ordinary failure does not cancel siblings.
12. Sender and receiver expose per-file byte progress and converge to 100% only after final validation and ACK.
13. File-size settings survive restart; the sender blocks files over its local limit and the receiver rejects offers over
    its own limit.
14. Dropping one or more regular files into an open desktop chat displays a drop affordance and sends them through the
    same batch transfer path as picker selections; directories, non-file payloads and oversized batches fail explicitly.
