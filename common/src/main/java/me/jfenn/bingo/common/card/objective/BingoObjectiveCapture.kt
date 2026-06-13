package me.jfenn.bingo.common.card.objective

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.utils.InstantType
import me.jfenn.bingo.platform.player.PlayerProfile

@Serializable
data class BingoObjectiveCapture(
    val team: BingoTeamKey,
    val player: PlayerProfile,
    val instant: InstantType,
)