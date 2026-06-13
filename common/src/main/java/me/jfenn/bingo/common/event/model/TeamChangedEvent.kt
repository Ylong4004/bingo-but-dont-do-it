package me.jfenn.bingo.common.event.model

import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEvent

data class TeamChangedEvent(
    val player: IPlayerHandle,
    val team: BingoTeam?,
) {
    companion object : IEvent<TeamChangedEvent>
}
