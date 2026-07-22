package me.jfenn.bingo.common.game

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.team.BingoTeamKey

/** 一条确实导致 objective 扣除生命的禁做词。 */
@Serializable
data class DDIDamageHistoryEntry(
    val wordText: String,
    val actorName: String? = null,
    val heartsRemaining: Int,
    val maxHearts: Int,
)

/** 一支 Bingo 队伍按发生顺序排列的扣血记录。 */
@Serializable
data class DDITeamDamageHistory(
    val teamKey: BingoTeamKey,
    val teamName: String,
    val entries: List<DDIDamageHistoryEntry> = emptyList(),
    /** 结算时的 DDI 剩余生命；零表示已经淘汰。 */
    val heartsRemaining: Int = 0,
    val maxHearts: Int = 0,
)

/**
 * 可选 DDI 集成与通用赛后流程之间的作用域桥接。
 * GameOverInfo 会在 POSTGAME 清理 DDI 状态前保存这些数据的快照。
 */
class DDIGameHistoryService {
    private data class MutableTeamHistory(
        val teamKey: BingoTeamKey,
        var teamName: String,
        val entries: MutableList<DDIDamageHistoryEntry> = mutableListOf(),
        var heartsRemaining: Int = 0,
        var maxHearts: Int = 0,
    )

    private val teams = linkedMapOf<BingoTeamKey, MutableTeamHistory>()

    fun reset() {
        teams.clear()
    }

    fun registerTeam(
        teamKey: BingoTeamKey,
        teamName: String,
        heartsRemaining: Int = 0,
        maxHearts: Int = 0,
    ) {
        val team = teams.getOrPut(teamKey) {
            MutableTeamHistory(teamKey, teamName.ifBlank { teamKey.label })
        }
        team.teamName = teamName.ifBlank { teamKey.label }
        if (maxHearts > 0) {
            team.maxHearts = maxHearts
            team.heartsRemaining = heartsRemaining.coerceIn(0, maxHearts)
        }
    }

    fun updateHearts(
        teamKey: BingoTeamKey,
        teamName: String,
        heartsRemaining: Int,
        maxHearts: Int,
    ) {
        registerTeam(teamKey, teamName, heartsRemaining, maxHearts)
    }

    fun recordDamage(
        teamKey: BingoTeamKey,
        teamName: String,
        wordText: String,
        actorName: String?,
        heartsRemaining: Int,
        maxHearts: Int,
    ) {
        registerTeam(teamKey, teamName, heartsRemaining, maxHearts)
        val team = teams.getValue(teamKey)
        team.entries += DDIDamageHistoryEntry(
            wordText = wordText,
            actorName = actorName,
            heartsRemaining = heartsRemaining.coerceAtLeast(0),
            maxHearts = maxHearts.coerceAtLeast(0),
        )
    }

    fun snapshot(): List<DDITeamDamageHistory> = teams.values.map { team ->
        DDITeamDamageHistory(
            teamKey = team.teamKey,
            teamName = team.teamName,
            entries = team.entries.toList(),
            heartsRemaining = team.heartsRemaining,
            maxHearts = team.maxHearts,
        )
    }
}
