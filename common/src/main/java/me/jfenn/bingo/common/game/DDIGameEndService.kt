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
 * 供能够产生 Bingo 胜者的可选玩法使用的公开桥接。
 *
 * [GameService] 特意保持为 common 内部实现。此桥接把胜者修改、标准队伍胜利事件
 * 和正常结束对局合并为一次受保护操作，避免集成模块只结束一部分流程。
 */
class DDIGameEndService internal constructor(
    private val state: BingoState,
    private val eventBus: IEventBus,
    private val gameService: GameService,
    private val log: Logger,
) {
    /**
     * 使用 [winnerKey] 结束当前对局；该值为空时按平局结束。
     * 已由普通 Bingo 玩法产生的胜者优先于 [winnerKey]。
     */
    fun end(winnerKey: BingoTeamKey?): Boolean {
        if (state.state != GameState.PLAYING || state.endedAt != null) return false

        // 绝不替换或追加普通 Bingo 计分已经产生的胜者。DDI 可以结束本局，
        // 但已有 Bingo 结果在竞态中优先，并继续作为统计和赛后界面记录的结果。
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
