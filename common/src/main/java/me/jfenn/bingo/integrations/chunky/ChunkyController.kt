package me.jfenn.bingo.integrations.chunky

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.GameState

class ChunkyController(
    events: ScopedEvents,
    private val chunkyIntegration: IChunkyApi,
) : BingoComponent() {

    init {
        events.onEnter(GameState.PREGAME) {
            chunkyIntegration.startPregen()
        }

        events.onEnter(GameState.STARTING) {
            chunkyIntegration.cancelTasks()
        }

        events.onEnter(GameState.PRELOADING) {
            chunkyIntegration.cancelTasks()
        }

        events.onEnter(GameState.PLAYING) {
            chunkyIntegration.cancelTasks()
        }
    }

}
