# Encrypted file transfer v1

## Goal

Add one-to-one file transfer between trusted LAN Chat peers without a central server. The feature is inspired by
[LocalSend's metadata-first upload session](https://github.com/localsend/protocol), while retaining LAN Chat's existing
device identity and HPKE trust model.

## Product behavior

- A sender selects one local file from an open conversation.
- The receiver sees the file name and size and must explicitly accept or reject the offer.
- Accepted files show live byte progress on both devices.
- Completed incoming files are stored in the platform download directory under a `LanChat` folder.
- Failed, rejected and cancelled transfers remain visible for the current app session with an explicit state.
- File contents are streamed in bounded chunks and are never loaded into memory as one buffer.

## Domain API

```kotlin
interface FileTransferService {
    val incomingOffers: StateFlow<List<IncomingFileOffer>>
    val transfers: StateFlow<List<FileTransfer>>

    suspend fun sendFile(peerId: String, file: LocalFile)
    suspend fun acceptOffer(transferId: String)
    suspend fun rejectOffer(transferId: String)
    suspend fun cancelTransfer(transferId: String)
}
```

## Protocol

The protocol uses the existing WebSocket endpoint and trusted peer identities.

```mermaid
sequenceDiagram
    participant S as Sender
    participant R as Receiver
    S->>S: Stream file through SHA-256
    S->>R: HPKE FILE_OFFER metadata
    R-->>S: Signed delivery ACK
    R->>R: User accepts or rejects
    R->>S: HPKE FILE_DECISION
    S-->>R: Signed delivery ACK
    loop Sequential 24 KiB chunks
        S->>R: HPKE FILE_CHUNK
        R->>R: Validate session, order and byte count
        R-->>S: Signed chunk ACK
    end
    R->>R: Verify total bytes and SHA-256, then rename temporary file
```

Control payloads and chunks are encrypted and authenticated with HPKE plus Ed25519 using frame metadata as associated
data. The SHA-256 digest protects whole-file integrity and allows validation before the temporary file becomes visible.

## Limits and validation

- Trusted peers only.
- One file per transfer session in v1.
- Maximum file size: 10 GiB.
- Chunk plaintext size: 24 KiB.
- Maximum file name length: 255 characters.
- File names are reduced to a leaf name; path separators, blank names and control characters are rejected.
- Chunks must arrive exactly once and in ascending order.
- A transfer must not write more bytes than declared in its offer.
- Rejected, cancelled, timed-out or invalid transfers delete their temporary data.
- Existing destination files are preserved by selecting a collision-free final name.

## Platform behavior

- macOS/Windows desktop: native AWT file picker; received files use `~/Downloads/LanChat`.
- Android: Storage Access Framework picker copies the selected content URI into app cache for streaming; received files
  use the app-specific external Downloads directory. No Android installation is part of this iteration.

## Acceptance criteria

1. A trusted desktop peer can select and offer a file from a conversation.
2. The receiver must accept before any file bytes are sent.
3. Both sides expose progress and terminal state.
4. A successful receiver file has the exact byte count and SHA-256 digest of the source.
5. Tampered, reordered, oversized and untrusted traffic is rejected without publishing a destination file.
6. JVM unit/integration tests and the macOS desktop package pass.
7. The rebuilt application installs and launches on desktop without installing an Android build.
