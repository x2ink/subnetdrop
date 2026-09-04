package ink.x2.kmp.di

import android.content.Context
import android.os.Build
import android.os.Environment
import ink.x2.kmp.data.AndroidDatabaseDriverFactory
import ink.x2.kmp.data.DatabaseDriverFactory
import ink.x2.kmp.domain.port.ChatTransport
import ink.x2.kmp.domain.port.FileTransferService
import ink.x2.kmp.domain.port.IdGenerator
import ink.x2.kmp.domain.port.PairingService
import ink.x2.kmp.domain.port.PeerDiscovery
import ink.x2.kmp.domain.port.SecureMessageCodec
import ink.x2.kmp.domain.port.TimestampProvider
import ink.x2.kmp.network.crypto.AndroidSecureKeyValueStore
import ink.x2.kmp.network.crypto.SecureKeyValueStore
import ink.x2.kmp.network.crypto.TinkSecureMessageCodec
import ink.x2.kmp.network.discovery.AndroidPeerDiscovery
import ink.x2.kmp.network.transport.LanChatTransport
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.UUID

val androidPlatformModule = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
    single<SecureKeyValueStore> { AndroidSecureKeyValueStore(androidContext()) }
    single<SecureMessageCodec> { TinkSecureMessageCodec(get()) }
    single<TimestampProvider> { TimestampProvider(System::currentTimeMillis) }
    single<IdGenerator> { IdGenerator { UUID.randomUUID().toString() } }
    single<PeerDiscovery> { AndroidPeerDiscovery(androidContext(), get()) }
    single {
        LanChatTransport(
            localIdentityService = get(),
            peerRepository = get(),
            trustedIdentityRepository = get(),
            chatRepository = get(),
            secureMessageCodec = get(),
            timestampProvider = get(),
            idGenerator = get(),
            receivedFilesDirectory = requireNotNull(
                androidContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            ).resolve("LanChat"),
        )
    }
    single<ChatTransport> { get<LanChatTransport>() }
    single<FileTransferService> { get<LanChatTransport>() }
    single<PairingService> { get<LanChatTransport>() }
    single(named(DEFAULT_DISPLAY_NAME_QUALIFIER)) { defaultAndroidDeviceName(androidContext()) }
}

private fun defaultAndroidDeviceName(context: Context): String {
    val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
    val model = Build.MODEL?.trim().orEmpty()
    return if (model.isEmpty()) appName else "$appName · $model"
}
