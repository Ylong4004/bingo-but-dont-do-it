package me.jfenn.bingo.common.scope

import me.jfenn.bingo.api.BingoApi
import me.jfenn.bingo.api.BingoEvents
import me.jfenn.bingo.api.IBingoApi
import me.jfenn.bingo.common.MOD_ID
import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.commonInit
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.lobbyWorld
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStarted
import me.jfenn.bingo.platform.event.game.ScopeStopped
import me.jfenn.bingo.platform.event.model.ServerEvent
import me.jfenn.bingo.platform.scope.IScopeManager
import net.minecraft.server.MinecraftServer
import org.koin.core.Koin
import org.koin.core.error.ScopeNotCreatedException
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.slf4j.Logger

class ScopeManager(
    private val koin: Koin,
    private val log: Logger,
    private val configService: ConfigService,
    private val eventBus: IEventBus,
) : IScopeManager {

    private fun getScopeId(server: MinecraftServer): ScopeID =
        System.identityHashCode(server).toString()

    override fun getScope(server: MinecraftServer?) : Scope? {
        server ?: return null
        return try {
            koin.getScope(getScopeId(server))
        } catch (e: ScopeNotCreatedException) {
            log.error("getScope invoked, but the server scope does not exist!")
            null
        }
    }

    init {
        eventBus.register(ServerEvent.Started) { (server) ->
            val isLobbyMode = server.lobbyWorld != null
            if (isLobbyMode) log.info("[ScopeManager] $MOD_ID.zip datapack exists - starting with isLobbyMode=true")
            else log.info("[ScopeManager] $MOD_ID.zip datapack does not exist - starting with isLobbyMode=false")

            log.info("[ScopeManager] Starting server scope...")
            val ctx = BingoScope(server)
            val scopeId = getScopeId(server)
            val scope = koin.createScope<BingoScope>(scopeId, ctx)

            // obtain the bingo game state from the save file
            val state = scope.get<BingoState>()
            state.isLobbyMode = isLobbyMode

            scope.commonInit()

            // re-run state entry listeners
            if (state.state == GameState.UNINITIALIZED) {
                state.changeState(eventBus, GameState.PREGAME)
            } else {
                eventBus.emit(StateChangedEvent, StateChangedEvent(state.state, state.state))
            }

            // If the state somehow does not have any cards (save error), fix that
            if (state.cards.isEmpty()) {
                log.error("[ScopeManager] There were no bingo cards in the save! Your game might not have shut down correctly, or your save data was corrupted.")
                scope.get<CardService>().createInitialCards()
            }

            val api = scope.get<IBingoApi>();
            BingoApi.set(api)
            eventBus.emit(ScopeStarted, ScopeStarted(scope))
            BingoEvents.INIT.invoke(api)
        }

        eventBus.register(ServerEvent.Stopped) { (server) ->
            val scope = getScope(server) ?: return@register
            log.info("[ScopeManager] Closing server scope...")
            configService.writeOptions(scope.get())
            BingoApi.set(null)
            eventBus.emit(ScopeStopped, ScopeStopped(scope))
            BingoEvents.CLOSE.invoke(null)
            scope.close()
        }
    }
}