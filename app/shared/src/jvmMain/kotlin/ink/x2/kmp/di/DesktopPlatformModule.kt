package ink.x2.kmp.di

import ink.x2.kmp.data.DatabaseDriverFactory
import ink.x2.kmp.data.DesktopDatabaseDriverFactory
import ink.x2.kmp.domain.port.ChatTransport
import ink.x2.kmp.domain.port.FileTransferService
import ink.x2.kmp.domain.port.IdGenerator
import ink.x2.kmp.domain.port.PairingService
import ink.x2.kmp.domain.port.PeerDiscovery
import ink.x2.kmp.domain.port.SecureMessageCodec
import ink.x2.kmp.domain.port.TimestampProvider
import ink.x2.kmp.network.crypto.DesktopSecureKeyValueStore
import ink.x2.kmp.network.crypto.SecureKeyValueStore
import ink.x2.kmp.network.crypto.TinkSecureMessageCodec
import ink.x2.kmp.network.discovery.DesktopPeerDiscovery
import ink.x2.kmp.network.transport.LanChatTransport
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.util.UUID

val desktopPlatformModule = module {
    single<DatabaseDriverFactory> {
        DesktopDatabaseDriverFactory(File(desktopDataDirectory(), "lan-chat.db"))
    }
    single<SecureKeyValueStore> { DesktopSecureKeyValueStore() }
    single<SecureMessageCodec> { TinkSecureMessageCodec(get()) }
    single<TimestampProvider> { TimestampProvider(System::currentTimeMillis) }
    single<IdGenerator> { IdGenerator { UUID.randomUUID().toString() } }
    single<PeerDiscovery> { DesktopPeerDiscovery(get()) }
    single {
        LanChatTransport(
            localIdentityService = get(),
            peerRepository = get(),
            trustedIdentityRepository = get(),
            chatRepository = get(),
            secureMessageCodec = get(),
            timestampProvider = get(),
            idGenerator = get(),
            receivedFilesDirectory = desktopReceivedFilesDirectory(),
        )
    }
    single<ChatTransport> { get<LanChatTransport>() }
    single<FileTransferService> { get<LanChatTransport>() }
    single<PairingService> { get<LanChatTransport>() }
    single(named(DEFAULT_DISPLAY_NAME_QUALIFIER)) { defaultDesktopDeviceName() }
}

private fun desktopDataDirectory(): File {
    val userHome = File(System.getProperty("user.home"))
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("mac") -> File(userHome, "Library/Application Support/LanChat")
        osName.contains("win") -> System.getenv("APPDATA")
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.resolve("LanChat")
            ?: File(userHome, "AppData/Roaming/LanChat")
        else -> File(userHome, ".local/share/lan-chat")
    }
}

private fun defaultDesktopDeviceName(): String {
    val userName = System.getProperty("user.name")?.takeIf(String::isNotBlank)
    return userName?.let { "LanChat · $it" } ?: "LanChat Desktop"
}

private fun desktopReceivedFilesDirectory(): File =
    File(System.getProperty("user.home"), "Downloads/LanChat")
