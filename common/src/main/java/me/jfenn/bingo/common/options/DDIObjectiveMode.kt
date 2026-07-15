package me.jfenn.bingo.common.options

import kotlinx.serialization.Serializable

/** Determines whether DDI words and hearts belong to players or Bingo teams. */
@Serializable
enum class DDIObjectiveMode {
    INDIVIDUAL,
    TEAM_SHARED,
}
