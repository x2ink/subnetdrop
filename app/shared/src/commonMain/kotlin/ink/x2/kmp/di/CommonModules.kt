package ink.x2.kmp.di

import ink.x2.kmp.data.DatabaseFactory
import ink.x2.kmp.data.SqlDelightChatRepository
import ink.x2.kmp.data.SqlDelightDeviceProfileRepository
import ink.x2.kmp.data.SqlDelightPeerRepository
import ink.x2.kmp.data.SqlDelightTrustedIdentityRepository
import ink.x2.kmp.domain.port.ChatRepository
import ink.x2.kmp.domain.port.DeviceProfileRepository
import ink.x2.kmp.domain.port.PeerRepository
import ink.x2.kmp.domain.port.TrustedIdentityRepository
import ink.x2.kmp.domain.usecase.MarkConversationReadUseCase
import ink.x2.kmp.domain.usecase.ObserveConversationsUseCase
import ink.x2.kmp.domain.usecase.ObserveMessagesUseCase
import ink.x2.kmp.domain.usecase.ObservePeersUseCase
import ink.x2.kmp.domain.usecase.SendMessageUseCase
import ink.x2.kmp.network.identity.LocalIdentityService
import ink.x2.kmp.presentation.LanChatViewModel
import ink.x2.kmp.runtime.LanChatRuntime
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
}

private val domainModule = module {
    factory { ObservePeersUseCase(get()) }
    factory { ObserveConversationsUseCase(get()) }
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
        LanChatRuntime(
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
        LanChatViewModel(
            runtime = get(),
            observePeers = get(),
            observeConversations = get(),
            observeMessages = get(),
            sendMessage = get(),
            markConversationRead = get(),
            pairingService = get(),
            fileTransferService = get(),
        )
    }
}

internal const val DEFAULT_DISPLAY_NAME_QUALIFIER = "defaultDisplayName"
