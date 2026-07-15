package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.event.model.TeamWinnerEvent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamWinner
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger
import java.time.Instant

/**
 * Public bridge for optional game modes that can produce a Bingo winner.
 *
 * [GameService] intentionally remains internal to common. This bridge keeps
 * winner mutation, the standard team-winner event and normal game shutdown in
 * one guarded operation so integrations cannot partially end a game.
 */
class DDIGameEndService internal constructor(
    private val state: BingoState,
    private val eventBus: IEventBus,
    private val gameService: GameService,
    private val log: Logger,
) {
    /**
     * Ends the active game with [winnerKey], or as a draw when it is null.
     * An existing normal Bingo winner takes priority over [winnerKey].
     */
    fun end(winnerKey: BingoTeamKey?): Boolean {
        if (state.state != GameState.PLAYING || state.endedAt != null) return false

        // Never replace or add to a winner already produced by normal Bingo
        // scoring. DDI may end the round, but the existing Bingo result wins
        // the race and remains the result recorded by stats/game-over UI.
        if (state.getRegisteredTeams().any { it.isWinner() }) {
            log.info("[DDI] Ending via the existing Bingo winner instead of replacing it with a DDI winner")
            gameService.end(GameEndReason.Bingo)
            return true
        }

        val winner = winnerKey?.let { state.teams[it] }
        if (winnerKey != null && winner == null) {
            log.error(
                "[DDI] Winning team {} is no longer registered; ending the DDI result as a draw",
                winnerKey.id,
            )
            gameService.end(GameEndReason.DDI)
            return true
        }

        if (winner != null) {
            winner.winner = TeamWinner(state.updatedAt ?: Instant.now())
            eventBus.emit(TeamWinnerEvent, TeamWinnerEvent(winner))
        }

        gameService.end(GameEndReason.DDI)
        return true
    }
}
