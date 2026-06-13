package me.jfenn.bingo.client

import me.jfenn.bingo.client.common.commonClientModule
import me.jfenn.bingo.client.impl.*
import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.impl.event.ClientCloseEvent
import me.jfenn.bingo.client.impl.event.ClientEventsImpl
import me.jfenn.bingo.client.impl.screen.ScreenFactory
import me.jfenn.bingo.client.impl.screen.button.ButtonFactory
import me.jfenn.bingo.client.platform.*
import me.jfenn.bingo.client.platform.renderer.IDrawServiceFactory
import me.jfenn.bingo.client.platform.screen.IButtonFactory
import me.jfenn.bingo.client.platform.screen.IScreenFactory
import me.jfenn.bingo.client.world.BingoWorldController
import me.jfenn.bingo.client.world.BingoWorldState
import me.jfenn.bingo.client.world.WorldServiceImpl
import me.jfenn.bingo.common.baseModule
import me.jfenn.bingo.common.commonInit
import me.jfenn.bingo.common.commonModule
import me.jfenn.bingo.common.config.MigrationHandler
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.sharedBaseModule
import me.jfenn.bingo.sharedModule
import net.fabricmc.api.ClientModInitializer
import org.koin.core.logger.Level
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.logger.SLF4JLogger
import org.slf4j.LoggerFactory

object Main : ClientModInitializer {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val clientModule = module {
        singleOf(::ClientCloseEvent) withOptions { createdAtStart() }
        singleOf(::ClientEventsImpl) withOptions { createdAtStart() }
        singleOf(::ScreenFactory) bind IScreenFactory::class

        singleOf(::ButtonFactory) bind IButtonFactory::class
        singleOf(::ClientImpl) bind IClient::class
        singleOf(::ClientNetworkingImpl) bind IClientNetworking::class
        singleOf(::ClientSoundManager) bind IClientSoundManager::class
        single { DrawService } bind IDrawServiceFactory::class
        singleOf(::HudCallbackImpl) withOptions { createdAtStart() }
        singleOf(::KeyBindingManager) bind IKeyBindingManager::class
        single { NativeImageFactory } bind INativeImageFactory::class
        single { ScrollableWidgetFactory } bind IScrollableWidgetFactory::class
        single { OptionsAccessor } bind IOptionsAccessor::class
        singleOf(::ResourcePackManager) bind IResourcePackManager::class
        single { SessionAccessor } bind ISessionAccessor::class
        single { TabsWidgetFactory } bind ITabsWidgetFactory::class

        single { BingoWorldState() }
        singleOf(::WorldServiceImpl) bind IWorldService::class
        singleOf(::BingoWorldController) withOptions { createdAtStart() }
    }

    override fun onInitializeClient() {
        log.info("Starting Bingo migration runner...")

        koinApplication {
            logger(SLF4JLogger(level = Level.DEBUG))
            modules(sharedBaseModule, baseModule)
        }.also {
            it.koin.get<MigrationHandler>().runMigrations()
            it.close()
        }

        log.info("Starting Bingo Client application...")

        koinApplication {
            BingoKoin.koinApp = this@koinApplication
            logger(SLF4JLogger(level = Level.DEBUG))
            modules(sharedModule, commonModule, clientModule, commonClientModule)
        }.also {
            it.koin.commonInit()
        }
    }
}
