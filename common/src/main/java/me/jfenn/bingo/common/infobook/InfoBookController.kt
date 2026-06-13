package me.jfenn.bingo.common.infobook

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.PlayerSettingsEvent
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus

/**
 * Gives the player a tutorial/lobby book when they join the game.
 * This should implement the following conditions:
 * - When isLobbyMode=true, books are persistently available in the lobby
 *   (unless config.lobbyTutorialBook is false)
 * - When isLobbyMode=false, books should never be given to players
 *   unless they run the "/bingo book" command
 */
internal class InfoBookController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val state: BingoState,
    private val config: BingoConfig,
    private val bookService: InfoBookService,
    private val playerManager: IPlayerManager,
) : BingoComponent() {

    init {
        events.onGameTick {
            if (state.state != GameState.PREGAME)
                return@onGameTick
            if (!config.lobbyTutorialBook)
                return@onGameTick
            if (!state.isLobbyMode)
                return@onGameTick

            for (player in playerManager.getPlayers()) {
                bookService.giveBookItem(player)
            }
        }

        eventBus.register(PlayerSettingsEvent) { (player) ->
            bookService.updateBookItem(player)
        }

        events.onPlayerChannelRegister { (player) ->
            // Update the book to reflect whether the player uses the bingo HUD
            bookService.updateBookItem(player)
        }
    }
}