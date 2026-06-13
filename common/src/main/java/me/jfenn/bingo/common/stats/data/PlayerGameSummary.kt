package me.jfenn.bingo.common.stats.data

import kotlinx.serialization.Serializable

@Serializable
class PlayerGameSummary(
    val game: GameInfo,
    val team: GameTeamInfo,
    val player: GamePlayerInfo,
)