package me.jfenn.bingo.common.spawn

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState

internal class ChestController(
    private val state: BingoState,
    private val chestService: ChestService,
    events: ScopedEvents,
) {

    init {
        // Creates team chest blocks when the game starts (if team kit is enabled)
        events.onEnter(GameState.PLAYING) { prevState ->
            if (prevState == GameState.PLAYING) return@onEnter

            for (team in state.getRegisteredTeams()) {
                chestService.createChestBlock(team)
            }
        }
    }

}