package ink.x2.subnetdrop.di

import ink.x2.subnetdrop.data.DatabaseFactory
import ink.x2.subnetdrop.data.MultiplatformFileTransferSettingsRepository
import ink.x2.subnetdrop.data.SqlDelightChatRepository
import ink.x2.subnetdrop.data.SqlDelightDeviceProfileRepository
import ink.x2.subnetdrop.data.SqlDelightPeerRepository
import ink.x2.subnetdrop.data.SqlDelightTrustedIdentityRepository
import ink.x2.subnetdrop.domain.port.ChatRepository
import ink.x2.subnetdrop.domain.port.DeviceProfileRepository
import ink.x2.subnetdrop.domain.port.FileTransferSettingsRepository
import ink.x2.subnetdrop.domain.port.PeerRepository
import ink.x2.subnetdrop.domain.port.TrustedIdentityRepository
import ink.x2.subnetdrop.domain.usecase.MarkConversationReadUseCase
import ink.x2.subnetdrop.domain.usecase.ObserveMessagesUseCase
import ink.x2.subnetdrop.domain.usecase.ObservePeersUseCase
import ink.x2.subnetdrop.domain.usecase.SendMessageUseCase
import ink.x2.subnetdrop.network.identity.LocalIdentityService
import ink.x2.subnetdrop.presentation.SubnetDropViewModel
import ink.x2.subnetdrop.runtime.SubnetDropRuntime
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel

fun commonModules(): List<Module> = listOf(dataModule, domainModule, presentationModule)

private val dataModule = module {
    single { DatabaseFactory(get()).create() }
    single<PeerRepository> { SqlDelightPeerRepository(get()) }
    single<ChatRepository> { SqlDelightChatRepository(get()) }
    single<DeviceProfileRepository> { SqlDelightDeviceProfileRepository(get()) }
    single<TrustedIdentityRepository> { SqlDelightTrustedIdentityRepository(get()) }
    single<FileTransferSettingsRepository> {
        MultiplatformFileTransferSettingsRepository(
            storage = get(),
            defaultSaveDirectory = get(named(DEFAULT_SAVE_DIRECTORY_QUALIFIER)),
        )
    }
}

private val domainModule = module {
    factory { ObservePeersUseCase(get()) }
    factory { ObserveMessagesUseCase(get()) }
    factory { SendMessageUseCase(get(), get(), get(), get()) }
    factory { MarkConversationReadUseCase(get(), get()) }
    single {
        LocalIdentityService(
            deviceProfileRepository = get(),
            secureMessageCodec = get(),
            idGenerator = get(),
            defaultDisplayName = get(named(DEFAULT_DISPLAY_NAME_QUALIFIER)),
        )
    }
    single {
        SubnetDropRuntime(
            localIdentityService = get(),
            chatTransport = get(),
            peerDiscovery = get(),
            peerRepository = get(),
            trustedIdentityRepository = get(),
        )
    }
}

private val presentationModule = module {
    viewModel {
        SubnetDropViewModel(
            runtime = get(),
            observePeers = get(),
            observeMessages = get(),
            sendMessage = get(),
            markConversationRead = get(),
            pairingService = get(),
            fileTransferService = get(),
            fileTransferSettingsRepository = get(),
        )
    }
}

internal const val DEFAULT_DISPLAY_NAME_QUALIFIER = "defaultDisplayName"
internal const val DEFAULT_SAVE_DIRECTORY_QUALIFIER = "defaultSaveDirectory"
