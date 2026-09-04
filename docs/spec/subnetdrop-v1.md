# SubnetDrop v1 Specification

## Goals

1. Run on Android 11+, macOS, and Windows through Compose Multiplatform.
2. Discover peers connected to the same reachable LAN without a central server.
3. Support one-to-one text conversations with live delivery status.
4. Store conversation history locally on each device.
5. Encrypt message content for the intended peer and authenticate the sender.
6. Transfer one file at a time after explicit receiver acceptance, with encrypted chunks and whole-file verification.
7. Use Clean Architecture and Koin constructor injection.

## Non-goals

- Group chat, Internet relay, NAT traversal, account registration, cloud backup, multi-device history sync, calls, and push notifications.
- Guaranteed reachability on guest Wi-Fi, client-isolated access points, or segmented enterprise VLANs.
- Background delivery on Android in v1 unless the foreground service is explicitly enabled.

## User flows

### Identity setup

On first launch the app creates a stable random device ID, a display name, an HPKE key pair, and a signing key pair. Private material never appears in discovery metadata.

### Discovery

Each foreground device advertises `_subnetdrop._tcp.local.` with its device ID, protocol major version, display name, and listener port. Discovery results expire when mDNS removes the service or a freshness timeout is reached. IP addresses are never treated as stable identity.

### Pairing

An untrusted peer connection exchanges public identity bundles. Both devices derive the same human-verifiable safety code from the canonical ordering of the public bundles. A peer becomes trusted only after local user confirmation. A changed public key for an existing device ID blocks messages and raises a safety warning.

### Messaging

An outgoing message is written locally as `PENDING` before network transmission. The recipient verifies the signature, rejects duplicates, decrypts, persists, and returns a delivery acknowledgement. The sender then marks the local row `DELIVERED`. Failed sends remain retryable.

Incoming messages are stored unread. Opening their conversation marks them read locally and sends an Ed25519-signed read
receipt in bounded batches. The sender accepts the receipt only from the trusted recipient and updates matching outgoing
messages from `DELIVERED` to `READ`. Conversation summaries expose an unread count.

### File transfer

The sender validates and hashes one selected file, then sends encrypted metadata. The receiver must explicitly accept
before any content is uploaded. Accepted content uses ordered, independently encrypted 24 KiB chunks over one WebSocket.
The receiver publishes the file only after byte-count and SHA-256 verification. Detailed limits are defined in
[file-transfer-v1.md](file-transfer-v1.md).

## Domain model

### Peer

| Field | Type | Notes |
|---|---|---|
| `id` | String | Random stable device identifier |
| `displayName` | String | User-editable local alias |
| `host` | String | Ephemeral resolved address |
| `port` | Int | Ephemeral listener port |
| `availability` | Enum | ONLINE or OFFLINE |
| `trustState` | Enum | UNPAIRED, PENDING, TRUSTED, KEY_CHANGED |

### Conversation

| Field | Type | Notes |
|---|---|---|
| `id` | String | Deterministically derived from the two device IDs |
| `peerId` | String | The remote device |
| `updatedAt` | Long | Local epoch milliseconds |

### Message

| Field | Type | Notes |
|---|---|---|
| `id` | String | Globally unique random identifier |
| `conversationId` | String | Owning conversation |
| `senderId` | String | Stable sender device ID |
| `recipientId` | String | Stable recipient device ID |
| `body` | String | Plaintext only inside trusted process/database boundary |
| `createdAt` | Long | Sender timestamp for display, not trust decisions |
| `direction` | Enum | INCOMING or OUTGOING |
| `status` | Enum | PENDING, SENDING, SENT, DELIVERED, READ, FAILED |
| `isRead` | Boolean | Local read state for incoming messages |

## Wire protocol

Every envelope contains `protocolVersion`, `type`, `messageId`, `senderId`, `recipientId`, and a type-specific payload. Parsers reject unknown major versions, oversized frames, invalid identifiers, unexpected recipients, invalid signatures, and malformed ciphertext.

Initial frame types:

- `PAIR_REQUEST`
- `PAIR_RESPONSE`
- `CHAT_MESSAGE`
- `DELIVERY_ACK`
- `READ_RECEIPT`
- `FILE_OFFER`
- `FILE_DECISION`
- `FILE_CHUNK`
- `FILE_CANCEL`
- `ERROR`
- `PING` / `PONG`

Message IDs make receipt idempotent. The database has a unique constraint on message ID. An ACK is safe to send repeatedly.
Pairing identities, routing fields, acknowledgement payloads and read-receipt message IDs are not encrypted. Chat content
and file business payloads are HPKE encrypted; protected frames are signed or return a signed acknowledgement as defined
by their type.

## Encryption

V1 uses Google Tink primitives instead of custom algorithms:

- HPKE: X25519, HKDF-SHA256, AES-256-GCM.
- Signature: Ed25519.
- `contextInfo`: canonical public envelope header.
- Signature input: canonical header plus ciphertext.

HPKE provides confidentiality but not sender authentication, so signatures are mandatory. Safety-code verification binds public keys to the human-approved peer. V1 does not claim post-compromise forward secrecy; an audited Noise or Double Ratchet transport can replace the encryption implementation behind the same domain port in a later protocol version.

## Local storage

SQLDelight is the source of truth. Required tables are `deviceProfileEntity`, `peerEntity`, `trustedIdentityEntity`,
`conversationEntity`, and `messageEntity`. Private keysets are wrapped by platform secure storage; they are not stored as
cleartext database columns. Message bodies are currently plaintext in SQLite and must gain at-rest encryption plus a
migration strategy before the project claims encrypted local history.

## Dependency injection

Koin is initialized once per process, outside Compose:

- Android in `Application.onCreate()` with `androidContext`.
- Desktop before opening the Compose window.

Long-lived database, repository, discovery, identity, and connection-manager instances are `single`. ViewModels use `viewModel`. Runtime conversation identifiers use parameters. Domain classes use ordinary constructor injection and do not import Koin.

## Acceptance criteria

1. Two supported devices on a reachable LAN discover each other within 10 seconds.
2. An unpaired device cannot deliver a chat message.
3. Both devices show the same safety code during pairing.
4. Tampered ciphertext, signature, sender, or recipient fields are rejected and not stored.
5. Sending a text message results in exactly one incoming row and a delivered state on the sender.
6. Opening a conversation clears its unread count and produces a signed read receipt that updates the sender to `READ`.
7. Restarting either application preserves identity, trust, and chat history.
8. A receiver can reject an offered file without receiving file bytes; an accepted file is published only after length
   and SHA-256 verification.
9. Android and desktop unit tests pass, and physical Android/macOS/Windows interoperability is recorded before release.
