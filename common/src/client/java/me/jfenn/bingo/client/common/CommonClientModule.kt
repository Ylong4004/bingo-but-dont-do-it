package me.jfenn.bingo.client.common

import me.jfenn.bingo.client.common.hud.BingoCardImageController
import me.jfenn.bingo.client.common.hud.BingoHudController
import me.jfenn.bingo.client.common.hud.BingoMessageRenderer
import me.jfenn.bingo.client.common.hud.ReadyHudRenderer
import me.jfenn.bingo.client.common.hud.card.CardTileRenderer
import me.jfenn.bingo.client.common.hud.card.ClientCardBufferRenderer
import me.jfenn.bingo.client.common.hud.card.ClientCardManager
import me.jfenn.bingo.client.common.hud.card.ClientCardRenderer
import me.jfenn.bingo.client.common.hud.screen.BingoCardPlacementScreen
import me.jfenn.bingo.client.common.hud.screen.BingoHudScreen
import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.common.settings.ClientSettingsController
import me.jfenn.bingo.client.common.settings.ClientSettingsService
import me.jfenn.bingo.client.common.sound.ClientSounds
import me.jfenn.bingo.client.common.sound.ScorePacketReceiver
import me.jfenn.bingo.client.common.sound.SoundService
import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.common.stats.ClientStatsSyncController
import me.jfenn.bingo.client.common.world.BingoWorldManager
import me.jfenn.bingo.client.integrations.JeiIntegration
import me.jfenn.bingo.client.integrations.YetAnotherConfigLibIntegration
import me.jfenn.bingo.client.integrations.jei.IJeiApi
import me.jfenn.bingo.client.integrations.jei.IJeiApiFactory
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.*

val commonClientModule = module {
    singleOf(::ClientPacketEvents) withOptions { createdAtStart() }

    single { BingoHudState() }
    singleOf(::ClientCardBufferRenderer)
    singleOf(::ClientCardRenderer)
    singleOf(::ClientCardManager)
    singleOf(::BingoMessageRenderer)
    singleOf(::CardTileRenderer)
    singleOf(::ReadyHudRenderer)
    singleOf(BingoHudScreen::Factory)
    singleOf(BingoCardPlacementScreen::Factory)
    singleOf(::BingoHudController) withOptions { createdAtStart() }
    singleOf(::BingoCardImageController) withOptions { createdAtStart() }

    singleOf(::SoundService)
    singleOf(::ClientSounds) withOptions { createdAtStart() }
    singleOf(::ScorePacketReceiver) withOptions { createdAtStart() }

    singleOf(::ClientStatsSyncController) withOptions { createdAtStart() }

    singleOf(::BingoWorldManager) withOptions { createdAtStart() }

    singleOf(::ClientSettingsService)
    singleOf(::ClientSettingsController) withOptions { createdAtStart() }
    factoryOf(YetAnotherConfigLibIntegration::create)

    single {
        JeiIntegration(
            ServiceLoader.load(IJeiApiFactory::class.java)
                .mapNotNull { it.create(get()) }
        )
    } bind IJeiApi::class

    singleOf(::ResourcePackInit) withOptions { createdAtStart() }
}
