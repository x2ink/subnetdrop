# LAN Chat implementation

## Active plan

### Interactive Git commit hooks

- [x] Diagnose repository and local hook state without overwriting existing hooks.
- [x] Generate shared `.githooks/` and activate interactive conventional commits locally.
- [x] Configure a project-appropriate pre-push check and verify all installed hooks.

### Stretchable chat bubble background

- [x] Replace size-sensitive bubble geometry with fixed-DP stretchable corner shapes for both directions.
- [x] Give message bubbles a shared minimum width, minimum height and content insets.
- [x] Compile, package and visually verify the desktop UI without running Android tasks.

### Repository development guidance

- [x] Add a root `AGENTS.md` with project-specific architecture, security, UI, DI, build and verification rules.
- [x] Refresh `README.md` to match the implemented chat, read-receipt and encrypted file-transfer capabilities.
- [x] Check documentation links, Markdown formatting and the final diff without running Android tasks.

### Responsive UI refinement

- [x] Make home, settings, and chat content remain scrollable at narrow widths and short window heights.
- [x] Replace text-only chat actions with accessible Material icons and refine bubbles/composer/empty state.
- [x] Add a root `BUILD_GUILD.md` covering supported build, run, test, lint, and packaging commands.
- [x] Move nearby/chat/settings navigation to the bottom on compact and desktop layouts.
- [x] Add unread conversation badges and authenticated read receipts with read/unread bubble states.
- [x] Verify tests, desktop packaging, and resized desktop rendering without installing Android builds.

### Encrypted file transfer

- [x] Review LocalSend's metadata-first upload protocol and define the LAN Chat-specific security boundary.
- [x] Add domain transfer models, service state and generic encrypted payload support.
- [x] Implement offer, accept/reject, cancellation, bounded chunk upload and SHA-256 verification.
- [x] Add desktop and Android file selection/storage adapters while installing only the desktop package.
- [x] Add file-transfer dialogs, progress cards and composer attachment action.
- [x] Cover encryption, rejection and end-to-end file transfer with JVM tests.
- [x] Build, install and visually verify the macOS desktop package; record evidence.

- [x] Capture architecture, protocol, security boundaries, and acceptance criteria.
- [x] Replace unused template targets with the Android/Desktop product target set.
- [x] Add Koin, serialization, SQLDelight, Ktor, discovery, and cryptography dependencies.
- [x] Implement domain models, ports, and use cases with unit tests.
- [x] Implement SQLDelight storage and repository adapters with persistence tests.
- [x] Implement persistent device identity, safety-code pairing, and signed HPKE envelopes.
- [x] Implement Android NSD and desktop JmDNS discovery.
- [x] Implement bidirectional WebSocket listener/client, ACK, deduplication, retry, and reconnect.
- [x] Implement Koin common/platform modules and process-level startup.
- [x] Replace template UI with nearby peers, pairing, conversations, chat, and settings screens.
- [x] Add Android permissions and foreground lifecycle behavior.
- [x] Build Android APK and macOS DMG distribution.
- [x] Test physical Android/macOS discovery, pairing, encrypted messaging, ACK, and persistence.
- [ ] Build Windows MSI on Windows and test physical Android/macOS/Windows interoperability.
- [x] Record verification evidence under `docs/tasks/verification/`.

## Review

The code-complete MVP is implemented and locally verified. Android/macOS physical interoperability passed on the same
Wi-Fi. A Windows build and physical Windows interoperability remain release validation work because Compose native
packages must be built on their target operating system.

The responsive UI follow-up moved the primary navigation to the bottom, kept lists usable at short window heights,
and replaced text actions with accessible Material icons. Conversations now expose unread counts; opening one marks its
incoming messages read and sends a signed, batched read receipt so outgoing bubbles advance from `未读` to `已读`.
Desktop JVM tests and DMG packaging passed. The packaged runtime module list was corrected after launch verification,
and the rebuilt application was installed and launched on macOS without building or installing an Android artifact.

Encrypted file transfer now follows a metadata-first acceptance flow inspired by LocalSend. Trusted peers exchange HPKE
encrypted offers and 24 KiB chunks, authenticate every frame, verify final byte count and SHA-256, and only then publish
the received file. The Compose UI provides a native file picker, incoming confirmation dialog, progress cards and
cancellation. Accept, reject, encryption binding and multi-chunk transfer tests pass; the rebuilt macOS application is
installed and running. No Android build or installation was performed.

Repository guidance is now explicit in the root `AGENTS.md`: dependency direction, Koin boundaries, Kotlin/coroutine
rules, protocol invariants, safe file handling, SQLDelight migrations, responsive Compose behavior, domestic mirrors and
desktop-first verification are all covered. `README.md` now reflects the implemented messaging, read receipt and file
transfer behavior, documents storage/security limitations, and links to the architecture, specifications, build guide and
verification evidence. Link targets and both staged and unstaged diff whitespace checks passed; no build was required for
documentation-only changes and no Android task was run.

Chat bubbles now use one fixed 14 dp `RoundedCornerShape` with a shared 64 x 48 dp minimum size and fixed content insets.
This provides Compose's equivalent of a nine-patch background: corners remain fixed while only the center expands for
longer content. Desktop visual verification compared a one-character incoming message, a short incoming message and an
outgoing message with delivery state. All kept the same corner geometry. JVM regression and DMG packaging passed in 35
seconds, the desktop application was updated and launched, and no Android task was run.

Git hooks are now shared under `.githooks/` and activated in `.git/hooks/` through the repository-owned
`scripts/setup-git-hooks.sh`. Running `git commit` interactively selects the type, scope, subject and task ID; remote review
`review footer` generation remains automatic. Invalid free-form `git commit -m` input is rejected while already conforming
messages pass. The project pre-push hook runs the core, data, network and shared JVM tests plus desktop compilation. Hook
copies, executable modes, shell syntax and interactive/non-interactive message paths were verified without creating a
commit or running an Android task.
