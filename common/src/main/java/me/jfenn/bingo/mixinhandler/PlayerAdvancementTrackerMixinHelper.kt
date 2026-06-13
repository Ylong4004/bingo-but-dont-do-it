package me.jfenn.bingo.mixinhandler

import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStopped

internal class PlayerAdvancementTrackerMixinHelper(
    private val state: BingoState,
    private val teamService: TeamService,
    eventBus: IEventBus,
) {
    internal fun shouldPreventSpectatorAdvancementsInternal(player: IPlayerHandle): Boolean {
        if (!state.isLobbyMode) return false
        return state.state != GameState.PLAYING || !teamService.isPlaying(player)
    }

    init {
        instance = this
        eventBus.register(ScopeStopped) {
            instance = null
        }
    }

    companion object {
        var instance: PlayerAdvancementTrackerMixinHelper? = null

        @JvmStatic
        fun shouldPreventSpectatorAdvancements(player: IPlayerHandle): Boolean {
            return instance?.shouldPreventSpectatorAdvancementsInternal(player) ?: false
        }
    }
}