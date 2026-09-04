# LAN Chat Architecture

This document is the entry point for the project's technical documentation.

## Product boundary

LAN Chat is a serverless, one-to-one messenger for Android, macOS, and Windows. Devices discover each other on the same local network, pair explicitly, exchange authenticated encrypted messages, and keep their own history locally.

- [Product and protocol specification](spec/lan-chat-v1.md)
- [Encrypted file-transfer specification](spec/file-transfer-v1.md)
- [Active implementation plan](tasks/todo.md)

## System overview

```mermaid
flowchart TB
    Android[Android shell] --> UI
    Desktop[macOS / Windows shell] --> UI
    UI[Compose presentation] --> Domain[Domain ports and use cases]
    Data[SQLDelight data layer] --> Domain
    Network[Discovery, transport, crypto] --> Domain
    Network --> Peer[Peer device]
    Data --> Database[(Local SQLite)]
    Network --> Secrets[Platform secret storage]
```

Dependencies point inward. Domain code is pure Kotlin and has no dependency on Compose, Koin, SQLDelight, Ktor, Android APIs, or desktop APIs. Koin is used only at composition roots and presentation boundaries.

## Modules

| Module | Responsibility |
|---|---|
| `:core` | Domain models, ports, and use cases |
| `:data` | SQLDelight schema, local message/history repositories |
| `:network` | mDNS discovery, WebSocket transport, pairing and encryption |
| `:app:shared` | Shared Compose UI and ViewModels |
| `:app:androidApp` | Android entry point, permissions and lifecycle |
| `:app:desktopApp` | macOS/Windows entry point, window and process lifecycle |

The legacy `server` template directory is excluded from Gradle and is not part of the production data path. Clients
never require a central server.

## Main flow

```mermaid
sequenceDiagram
    participant A as Device A
    participant B as Device B
    A->>B: mDNS discovery
    A->>B: WebSocket connect + hello
    A->>B: Pairing public identity
    B-->>A: Pairing public identity
    A->>A: User verifies safety code
    B->>B: User verifies safety code
    A->>B: Signed HPKE ciphertext
    B->>B: Verify, deduplicate, decrypt, persist
    B-->>A: Authenticated delivery ACK
    A->>A: Mark message delivered
    B->>B: Open conversation and mark incoming messages read
    B-->>A: Signed read receipt
    A->>A: Mark matching outgoing messages read
    A->>B: Encrypted file offer
    B-->>A: Accept or reject
    loop Bounded encrypted chunks
        A->>B: HPKE file chunk
        B-->>A: Authenticated chunk ACK
    end
    B->>B: Verify SHA-256 and publish local file
```

## Platform boundary

Platform-dependent services are expressed as domain ports and supplied by Koin modules:

- Android: `NsdManager`, Wi-Fi multicast handling, Android Keystore, app files and process foreground lifecycle.
- Desktop JVM: JmDNS, OS-specific secure storage, app data directory, firewall-aware listener lifecycle.
