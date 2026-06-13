package me.jfenn.bingo.common.team

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.utils.InstantType

@Serializable
data class TeamWinner(
    val instant: InstantType,
)