package me.jfenn.bingo.integrations.waystones

import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStarted
import me.jfenn.bingo.plugin.IBingoInternalPlugin
import org.koin.core.Koin

internal class WaystonesPluginController : IBingoInternalPlugin {
    override fun initialize(koin: Koin) {
        val environment = koin.get<IModEnvironment>()
        val events = koin.get<IEventBus>()

        if (environment.isModLoaded("waystones")) {
            events.register(ScopeStarted) {
                WaystonesPlugin(it.scope.get(), it.scope.get(), it.scope.get())
            }
        }
    }
}