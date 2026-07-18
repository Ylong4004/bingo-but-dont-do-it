package me.jfenn.bingo.common.options

import kotlinx.serialization.Serializable

/** 决定 DDI 词条和生命归属于单个玩家还是 Bingo 队伍。 */
@Serializable
enum class DDIObjectiveMode {
    INDIVIDUAL,
    TEAM_SHARED,
}
