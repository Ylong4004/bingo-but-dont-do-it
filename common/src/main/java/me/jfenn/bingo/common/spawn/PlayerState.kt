package me.jfenn.bingo.common.spawn

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.utils.UuidAsString

@Serializable
data class PlayerState(
    val lastGameId: UuidAsString? = null,
    val lastState: GameState? = null,
    val lastTeam: BingoTeamKey? = null,
) {
    companion object {
        val DEFAULT = PlayerState()
    }
}