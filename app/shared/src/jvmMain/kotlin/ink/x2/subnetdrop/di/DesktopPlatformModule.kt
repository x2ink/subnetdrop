package ink.x2.subnetdrop.di

import ink.x2.subnetdrop.data.DatabaseDriverFactory
import ink.x2.subnetdrop.data.DesktopDatabaseDriverFactory
import ink.x2.subnetdrop.domain.port.ChatTransport
import ink.x2.subnetdrop.domain.port.FileTransferService
import ink.x2.subnetdrop.domain.port.IdGenerator
import ink.x2.subnetdrop.domain.port.PairingService
import ink.x2.subnetdrop.domain.port.PeerDiscovery
import ink.x2.subnetdrop.domain.port.SecureMessageCodec
import ink.x2.subnetdrop.domain.port.TimestampProvider
import ink.x2.subnetdrop.network.crypto.DesktopSecureKeyValueStore
import ink.x2.subnetdrop.network.crypto.SecureKeyValueStore
import ink.x2.subnetdrop.network.crypto.TinkSecureMessageCodec
import ink.x2.subnetdrop.network.discovery.DesktopPeerDiscovery
import ink.x2.subnetdrop.network.transport.SubnetDropTransport
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.util.UUID

val desktopPlatformModule = module {
    single<DatabaseDriverFactory> {
        DesktopDatabaseDriverFactory(File(desktopDataDirectory(), "subnetdrop.db"))
    }
    single<SecureKeyValueStore> { DesktopSecureKeyValueStore() }
    single<SecureMessageCodec> { TinkSecureMessageCodec(get()) }
    single<TimestampProvider> { TimestampProvider(System::currentTimeMillis) }
    single<IdGenerator> { IdGenerator { UUID.randomUUID().toString() } }
    single<PeerDiscovery> { DesktopPeerDiscovery(get()) }
    single {
        SubnetDropTransport(
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
    single<ChatTransport> { get<SubnetDropTransport>() }
    single<FileTransferService> { get<SubnetDropTransport>() }
    single<PairingService> { get<SubnetDropTransport>() }
    single(named(DEFAULT_DISPLAY_NAME_QUALIFIER)) { defaultDesktopDeviceName() }
}

private fun desktopDataDirectory(): File {
    val userHome = File(System.getProperty("user.home"))
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("mac") -> File(userHome, "Library/Application Support/SubnetDrop")
        osName.contains("win") -> System.getenv("APPDATA")
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.resolve("SubnetDrop")
            ?: File(userHome, "AppData/Roaming/SubnetDrop")
        else -> File(userHome, ".local/share/subnetdrop")
    }
}

private fun defaultDesktopDeviceName(): String {
    val userName = System.getProperty("user.name")?.takeIf(String::isNotBlank)
    return userName?.let { "SubnetDrop · $it" } ?: "SubnetDrop Desktop"
}

private fun desktopReceivedFilesDirectory(): File =
    File(System.getProperty("user.home"), "Downloads/SubnetDrop")
