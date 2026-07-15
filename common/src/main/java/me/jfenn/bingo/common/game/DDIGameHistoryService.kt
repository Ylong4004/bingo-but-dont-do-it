package me.jfenn.bingo.common.game

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.team.BingoTeamKey

/** One forbidden word that actually reduced an objective's hearts. */
@Serializable
data class DDIDamageHistoryEntry(
    val wordText: String,
    val actorName: String? = null,
    val heartsRemaining: Int,
    val maxHearts: Int,
)

/** Ordered damage history for one Bingo team. */
@Serializable
data class DDITeamDamageHistory(
    val teamKey: BingoTeamKey,
    val teamName: String,
    val entries: List<DDIDamageHistoryEntry> = emptyList(),
)

/**
 * Scoped bridge between the optional DDI integration and the common game-over
 * pipeline. GameOverInfo snapshots this data before POSTGAME clears DDI state.
 */
class DDIGameHistoryService {
    private data class MutableTeamHistory(
        val teamKey: BingoTeamKey,
        var teamName: String,
        val entries: MutableList<DDIDamageHistoryEntry> = mutableListOf(),
    )

    private val teams = linkedMapOf<BingoTeamKey, MutableTeamHistory>()

    fun reset() {
        teams.clear()
    }

    fun registerTeam(teamKey: BingoTeamKey, teamName: String) {
        teams.getOrPut(teamKey) {
            MutableTeamHistory(teamKey, teamName.ifBlank { teamKey.label })
        }.teamName = teamName.ifBlank { teamKey.label }
    }

    fun recordDamage(
        teamKey: BingoTeamKey,
        teamName: String,
        wordText: String,
        actorName: String?,
        heartsRemaining: Int,
        maxHearts: Int,
    ) {
        registerTeam(teamKey, teamName)
        teams.getValue(teamKey).entries += DDIDamageHistoryEntry(
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
        )
    }
}
