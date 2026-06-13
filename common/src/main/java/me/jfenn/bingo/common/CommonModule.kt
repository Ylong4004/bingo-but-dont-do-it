package me.jfenn.bingo.common

import me.jfenn.bingo.api.BingoApiImpl
import me.jfenn.bingo.api.IBingoApi
import me.jfenn.bingo.common.autorestart.ResetCommand
import me.jfenn.bingo.common.autorestart.ResetOnLeaveController
import me.jfenn.bingo.common.autorestart.ResetService
import me.jfenn.bingo.common.bossbar.BossBarController
import me.jfenn.bingo.common.bossbar.BossBarService
import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.card.TagExpansionService
import me.jfenn.bingo.common.card.filter.ObjectiveFilterCommand
import me.jfenn.bingo.common.card.filter.ObjectiveFilterService
import me.jfenn.bingo.common.card.objective.*
import me.jfenn.bingo.common.card.tag.*
import me.jfenn.bingo.common.chat.ChatCommand
import me.jfenn.bingo.common.chat.ChatMessageController
import me.jfenn.bingo.common.chat.ChatMessageService
import me.jfenn.bingo.common.chat.CoordsCommand
import me.jfenn.bingo.common.commands.*
import me.jfenn.bingo.common.config.*
import me.jfenn.bingo.common.controller.*
import me.jfenn.bingo.common.data.*
import me.jfenn.bingo.common.datapack.LobbyWorldService
import me.jfenn.bingo.common.datapack.ServerDatapackManager
import me.jfenn.bingo.common.datapack.ServerProps
import me.jfenn.bingo.common.event.EventBus
import me.jfenn.bingo.common.event.InteractionEntityEvents
import me.jfenn.bingo.common.event.ScopedEventBus
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.game.*
import me.jfenn.bingo.common.infobook.InfoBookCommand
import me.jfenn.bingo.common.infobook.InfoBookController
import me.jfenn.bingo.common.infobook.InfoBookService
import me.jfenn.bingo.common.lobby.BingoLobbyCommand
import me.jfenn.bingo.common.lobby.LobbyModeController
import me.jfenn.bingo.common.lobby.LobbyModeService
import me.jfenn.bingo.common.map.*
import me.jfenn.bingo.common.menu.MenuController
import me.jfenn.bingo.common.menu.tooltips.TooltipController
import me.jfenn.bingo.common.menu.tooltips.TooltipState
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.ready.ReadyCommand
import me.jfenn.bingo.common.ready.ReadyController
import me.jfenn.bingo.common.ready.ReadyService
import me.jfenn.bingo.common.ready.ReadyTimerState
import me.jfenn.bingo.common.scope.BingoPluginHolder
import me.jfenn.bingo.common.scope.BingoScope
import me.jfenn.bingo.common.scope.ScopeManager
import me.jfenn.bingo.common.scoreboard.ScoreboardController
import me.jfenn.bingo.common.scoreboard.ScoreboardDataController
import me.jfenn.bingo.common.scoreboard.ScoreboardService
import me.jfenn.bingo.common.scoring.GameMessageController
import me.jfenn.bingo.common.scoring.GameMessageService
import me.jfenn.bingo.common.scoring.ScoreUpdateService
import me.jfenn.bingo.common.scoring.ScoredItemCheck
import me.jfenn.bingo.common.spawn.*
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.PersistentStateManager
import me.jfenn.bingo.common.state.PersistentStates
import me.jfenn.bingo.common.stats.*
import me.jfenn.bingo.common.team.*
import me.jfenn.bingo.common.text.MessageService
import me.jfenn.bingo.common.text.PlaceholderService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.timer.*
import me.jfenn.bingo.common.utils.Build
import me.jfenn.bingo.integrations.chunky.ChunkyController
import me.jfenn.bingo.integrations.chunky.DummyChunky
import me.jfenn.bingo.integrations.chunky.IChunkyApiFactory
import me.jfenn.bingo.integrations.permissions.FallbackPermissionsApi
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.integrations.permissions.IPermissionsApiFactory
import me.jfenn.bingo.integrations.placeholders.DummyPlaceholders
import me.jfenn.bingo.integrations.placeholders.ITextPlaceholdersApi
import me.jfenn.bingo.integrations.placeholders.ITextPlaceholdersApiFactory
import me.jfenn.bingo.integrations.placeholders.PlaceholdersIntegration
import me.jfenn.bingo.integrations.vanish.DummyVanish
import me.jfenn.bingo.integrations.vanish.IVanishApi
import me.jfenn.bingo.integrations.vanish.IVanishApiFactory
import me.jfenn.bingo.integrations.voice.DummyVoiceApi
import me.jfenn.bingo.integrations.voice.IVoiceApi
import me.jfenn.bingo.integrations.voice.IVoiceApiFactory
import me.jfenn.bingo.integrations.voice.VoiceGroupController
import me.jfenn.bingo.mixinhandler.PlayerAdvancementTrackerMixinHelper
import me.jfenn.bingo.mixinhandler.PlayerEntityMixinHandler
import me.jfenn.bingo.mixinhandler.PlayerManagerMixinHelper
import me.jfenn.bingo.platform.IPersistentStateManager
import me.jfenn.bingo.platform.config.IConfigManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.scope.IScopeManager
import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.Koin
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.jvm.optionals.getOrNull

val baseModule = module {
    single { getKoin() }
    single { LoggerFactory.getLogger(MOD_ID) }
    singleOf(::MigrationHandler)

    singleOf(::ConfigManager) bind IConfigManager::class
    singleOf(::ConfigService)
    single { get<ConfigService>().config }
    single { get<ConfigService>().config.client }
}

val commonModule = module {
    includes(baseModule)

    singleOf(::MapColors)
    singleOf(::TextProvider)
    singleOf(::ServerProps)

    singleOf(::LobbyWorldService)
    singleOf(::ServerDatapackManager)
    singleOf(::EventBus) bind IEventBus::class
    singleOf(::ScopeManager) bind IScopeManager::class
    singleOf(::ServerPacketEvents)

    singleOf(::ReadyTimerState)

    singleOf(::BingoCommand)
    singleOf(::BingoCardCommand)
    singleOf(::BingoDebugCommand)
    singleOf(::BingoLobbyCommand)
    singleOf(::BingoOptionsCommands)
    singleOf(::BingoPrefsCommand)
    singleOf(::ResetCommand)
    singleOf(::GameCommands)
    singleOf(::ChatCommand)
    singleOf(::CoordsCommand)
    singleOf(::JoinCommand)
    singleOf(::ShuffleTeamsCommand)
    singleOf(::SpectatorCommand)
    singleOf(::ReadyCommand)
    singleOf(::StatsCommand)
    singleOf(::ObjectiveFilterCommand)
    singleOf(::InfoBookCommand)
    singleOf(::DataCommands)

    singleOf(::ConnectionFactory)
    single { get<ConnectionFactory>().create() }
    singleOf(::StatsService)
    singleOf(::PlayerSettingsService)

    single<ITextPlaceholdersApi> {
        ServiceLoader.load(ITextPlaceholdersApiFactory::class.java)
            .firstNotNullOfOrNull { it.create(get()) }
            ?: DummyPlaceholders
    }
    singleOf(::PlaceholdersIntegration)

    single<IChunkyApiFactory> {
        ServiceLoader.load(IChunkyApiFactory::class.java).findFirst()
            .getOrNull()
            ?: DummyChunky.Factory
    }

    single<IPermissionsApi> {
        ServiceLoader.load(IPermissionsApiFactory::class.java)
            .firstNotNullOfOrNull { it.create(get()) }
            ?: FallbackPermissionsApi()
    }

    single<IVoiceApi> {
        ServiceLoader.load(IVoiceApiFactory::class.java)
            .firstNotNullOfOrNull { it.create(get()) }
            ?: DummyVoiceApi
    }

    single {
        BingoPluginHolder(
            getKoin(),
            ServiceLoader.load(IBingoInternalPlugin::class.java)
                .toList()
        )
    }

    singleOf(::TrackedFileService)
    singleOf(::FilterPresetsLoader)
    singleOf(::ObjectiveLoader)
    singleOf(::SpawnKitLoader)
    singleOf(::TierListLoader)
    singleOf(::TagLoader)
    singleOf(::TeamPresetLoader)
    singleOf(::ObtainableItemsLoader)
    singleOf(MessageService::Loader)
    singleOf(DatapackFunctionService::Loader)
    singleOf(::ScopedData)
    singleOf(::DataLoaderManager)

    scope<BingoScope> {
        scoped { ScopedEventBus(getKoin().get(), this) } bind IEventBus::class
        scopedOf(::ScopedEvents)
        scopedOf(::InteractionEntityEvents)
        scopedOf(::BingoApiImpl) bind IBingoApi::class

        scopedOf(::PersistentStateManager) bind IPersistentStateManager::class
        scopedOf(::PersistentStates)
        scoped<BingoState> {
            get<IPersistentStateManager>().getFromWorld(
                type = get<PersistentStates>().bingo,
            )
        }

        scoped { get<BingoScope>().server }
        scoped { get<BingoState>().options }

        scopedOf(::PlaceholderService)
        scopedOf(::MessageService)

        scopedOf(::DataCommandService)
        scopedOf(::TagExpansionService)

        scopedOf(::ObjectiveService)
        scopedOf(::ObjectiveListService)
        scopedOf(::ObjectiveDisplayService)
        scopedOf(::AdvancementObjectiveManager)
        scopedOf(::ItemObjectiveManager)
        scopedOf(::SomeOfObjectiveManager)
        scopedOf(::InverseObjectiveManager)
        scopedOf(::OpponentObjectiveManager)
        scopedOf(::StatsObjectiveManager)
        scopedOf(::ScoreboardObjectiveManager)
        scoped {
            BingoObjectiveManager(
                listOf(
                    get<AdvancementObjectiveManager>(),
                    get<ItemObjectiveManager>(),
                    get<SomeOfObjectiveManager>(),
                    get<InverseObjectiveManager>(),
                    get<OpponentObjectiveManager>(),
                    get<StatsObjectiveManager>(),
                    get<ScoreboardObjectiveManager>(),
                ),
                get(),
                get(),
            )
        }

        scopedOf(::DataTagProvider)
        scopedOf(::ObjectiveTagProvider)
        scopedOf(::TierListTagProvider)
        scopedOf(::UnobtainableTagProvider)
        scoped {
            TagService(
                listOf(
                    get<DataTagProvider>(),
                    get<ObjectiveTagProvider>(),
                    get<TierListTagProvider>(),
                    get<UnobtainableTagProvider>(),
                )
            )
        }

        scopedOf(::OptionsService)
        scopedOf(::ObjectiveFilterService)
        scopedOf(::CardService)

        scopedOf(::LobbyModeService)
        scopedOf(::LobbyModeController)

        scopedOf(::StatsLobbyController)
        scopedOf(::StatsSyncController)
        scopedOf(::WriteStatsService)

        scopedOf(::GameService)
        scopedOf(::GameOverService)
        scopedOf(::GameOverController)
        scopedOf(::GameResumeService)

        scopedOf(::InfoBookController)
        scopedOf(::InfoBookService)

        scopedOf(::ResetService)
        scopedOf(::ReadyService)
        scopedOf(::ReadyController)

        scopedOf(::ChatMessageService)
        scopedOf(::ChatMessageController)

        scopedOf(::MapItemService)
        scopedOf(::MapRenderService)
        scopedOf(::CardViewService)
        scopedOf(::CardImageService)
        scopedOf(::ScoreboardService)

        scopedOf(::GameStatusController)
        scopedOf(::GameMessageService)
        scopedOf(::GameMessageController)

        scopedOf(::ScoredItemCheck)
        scopedOf(::ScoreUpdateService)

        scopedOf(::TooltipState)
        scopedOf(::TooltipController)
        scopedOf(::MenuController)

        scopedOf(::BossBarService)
        scopedOf(::BossBarController)

        scopedOf(::BingoMapController)
        scopedOf(::DatapackFunctionService)
        scopedOf(::GameRuleController)
        scopedOf(::LobbyChaosController)
        scopedOf(::MapItemController)
        scopedOf(::MapItemHandler)
        scopedOf(::MotdController)
        scopedOf(::NightVisionController)
        scopedOf(::PlayerSettingsController)
        scopedOf(::PlayerSpectatorController)

        scopedOf(::ScoreboardDataController)
        scopedOf(::ScoreboardController)

        scopedOf(::ChestService)
        scopedOf(::ChestController)
        scopedOf(::ElytraService)
        scopedOf(::SpawnService)
        scopedOf(::SpawnKitService)
        scopedOf(SpreadPlayers::Factory)
        scopedOf(::PlayerController)
        scopedOf(::SpawnPreloadingController)

        scopedOf(::OfflinePlayerCache)
        scopedOf(::TeamService)
        scopedOf(::TeamController)

        scopedOf(::CountdownService)
        scopedOf(::CountdownController)
        scopedOf(::TimerCheck)
        scopedOf(::TimerPacketController)
        scopedOf(::WaitUntilLoadedController)

        scopedOf(::ResetOnLeaveController)

        scopedOf(::CommandTreeHandler)

        scoped<IVanishApi> {
            ServiceLoader.load(IVanishApiFactory::class.java)
                .firstNotNullOfOrNull { it.create(this) }
                ?: DummyVanish
        }

        scoped { get<IChunkyApiFactory>().create(this) }
        scopedOf(::ChunkyController)

        scopedOf(::VoiceGroupController)

        scopedOf(::PlayerAdvancementTrackerMixinHelper)
        scopedOf(::PlayerEntityMixinHandler)
        scopedOf(::PlayerManagerMixinHelper)
    }
}

fun Koin.commonInit() {
    get<BingoPluginHolder>()
    get<DataLoaderManager>()

    get<MapColors>()
    get<ServerDatapackManager>()
    get<IScopeManager>()
    get<ServerPacketEvents>()

    get<BingoCommand>()
    get<BingoCardCommand>()
    if (Build.isDebug)
        get<BingoDebugCommand>()
    get<BingoLobbyCommand>()
    get<BingoOptionsCommands>()
    get<BingoPrefsCommand>()
    get<ResetCommand>()
    get<GameCommands>()
    get<ChatCommand>()
    get<CoordsCommand>()
    get<JoinCommand>()
    get<ShuffleTeamsCommand>()
    get<SpectatorCommand>()
    get<ReadyCommand>()
    get<StatsCommand>()
    get<ObjectiveFilterCommand>()
    get<InfoBookCommand>()
    get<DataCommands>()

    get<ConnectionFactory>()
    get<StatsService>()

    get<PlaceholdersIntegration>()
}

fun Scope.commonInit() {
    get<ScoredItemCheck>() // must be registered before MenuController

    get<TooltipController>()
    get<MenuController>()
    get<InfoBookController>()

    get<BingoMapController>()
    get<BossBarController>()
    get<GameRuleController>()
    get<LobbyChaosController>()
    get<MapItemController>()
    get<MapItemHandler>()
    get<MotdController>()
    get<NightVisionController>()
    get<PlayerSettingsController>()
    get<PlayerSpectatorController>()
    get<ChatMessageController>()
    get<ReadyController>()

    get<ScoreboardDataController>()
    get<ScoreboardController>()

    get<GameStatusController>()
    get<GameMessageController>()
    get<GameOverController>()

    get<LobbyModeController>()

    get<StatsLobbyController>()
    get<StatsSyncController>()

    get<TeamController>()

    get<CountdownController>()
    get<TimerCheck>()
    get<TimerPacketController>()
    get<WaitUntilLoadedController>()

    get<ResetOnLeaveController>()

    get<CommandTreeHandler>()

    get<PlayerController>()
    get<ChestController>()
    get<SpawnPreloadingController>()

    get<ChunkyController>()
    get<VoiceGroupController>()

    get<PlayerAdvancementTrackerMixinHelper>()
    get<PlayerEntityMixinHandler>()
    get<PlayerManagerMixinHelper>()
}
