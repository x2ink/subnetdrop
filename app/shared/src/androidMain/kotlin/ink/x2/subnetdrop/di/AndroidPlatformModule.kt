package ink.x2.subnetdrop.di

import android.content.Context
import android.os.Build
import android.os.Environment
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import ink.x2.subnetdrop.data.AndroidDatabaseDriverFactory
import ink.x2.subnetdrop.data.DatabaseDriverFactory
import ink.x2.subnetdrop.domain.port.ChatTransport
import ink.x2.subnetdrop.domain.port.FileTransferService
import ink.x2.subnetdrop.domain.port.IdGenerator
import ink.x2.subnetdrop.domain.port.PairingService
import ink.x2.subnetdrop.domain.port.PeerDiscovery
import ink.x2.subnetdrop.domain.port.PeerReachabilityProbe
import ink.x2.subnetdrop.domain.port.SecureMessageCodec
import ink.x2.subnetdrop.domain.port.TimestampProvider
import ink.x2.subnetdrop.network.crypto.AndroidSecureKeyValueStore
import ink.x2.subnetdrop.network.crypto.SecureKeyValueStore
import ink.x2.subnetdrop.network.crypto.TinkSecureMessageCodec
import ink.x2.subnetdrop.network.discovery.AndroidPeerDiscovery
import ink.x2.subnetdrop.network.transport.SubnetDropTransport
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.UUID

val androidPlatformModule = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences(FILE_SETTINGS_NAME, Context.MODE_PRIVATE),
        )
    }
    single<SecureKeyValueStore> { AndroidSecureKeyValueStore(androidContext()) }
    single<SecureMessageCodec> { TinkSecureMessageCodec(get()) }
    single<TimestampProvider> { TimestampProvider(System::currentTimeMillis) }
    single<IdGenerator> { IdGenerator { UUID.randomUUID().toString() } }
    single<PeerDiscovery> { AndroidPeerDiscovery(androidContext(), get(), get()) }
    single {
        SubnetDropTransport(
            localIdentityService = get(),
            peerRepository = get(),
            trustedIdentityRepository = get(),
            chatRepository = get(),
            secureMessageCodec = get(),
            timestampProvider = get(),
            idGenerator = get(),
            fileTransferSettingsRepository = get(),
        )
    }
    single<ChatTransport> { get<SubnetDropTransport>() }
    single<PeerReachabilityProbe> { get<SubnetDropTransport>() }
    single<FileTransferService> { get<SubnetDropTransport>() }
    single<PairingService> { get<SubnetDropTransport>() }
    single(named(DEFAULT_DISPLAY_NAME_QUALIFIER)) { defaultAndroidDeviceName(androidContext()) }
    single(named(DEFAULT_SAVE_DIRECTORY_QUALIFIER)) { defaultAndroidSaveDirectory(androidContext()) }
}

private fun defaultAndroidSaveDirectory(context: Context): String = requireNotNull(
    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
).resolve("SubnetDrop").path

private fun defaultAndroidDeviceName(context: Context): String {
    val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
    val model = Build.MODEL?.trim().orEmpty()
    return if (model.isEmpty()) appName else "$appName · $model"
}

private const val FILE_SETTINGS_NAME = "file-transfer-settings"
