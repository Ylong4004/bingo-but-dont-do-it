package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.team.BingoTeamKey
import net.minecraft.util.Formatting
import java.util.UUID

/** A fixed participant in the DDI roster. Mutable gameplay state lives on an objective. */
data class DDIPlayerState(
    val playerId: UUID,
    val playerName: String,
    val teamKey: BingoTeamKey,
)

/**
 * Server-authoritative state owned by either one player or one Bingo team.
 *
 * Team-shared mode creates exactly one objective per Bingo team, so its word,
 * timer and heart pool can never drift between teammates.
 */
data class DDIObjectiveState(
    val objectiveId: String,
    val objectiveName: String,
    val teamKey: BingoTeamKey,
    val teamName: String,
    val teamColor: Formatting,
    val memberIds: Set<UUID>,
    val memberNames: List<String>,
    val isTeamShared: Boolean,
    var currentWord: DDIWordPool.WordEntry? = null,
    var hearts: Int = 3,
    var maxHearts: Int = 3,
    var wordTimerSeconds: Int = 60,
    var maxWordTimerSeconds: Int = 60,
    var isEliminated: Boolean = false,
    /** Kept across word changes to reject two callbacks in one server tick. */
    var lastAcceptedTriggerTick: Long = Long.MIN_VALUE,
) {
    val isAlive: Boolean get() = !isEliminated && hearts > 0

    fun loseHeart(): Boolean {
        hearts = (hearts - 1).coerceAtLeast(0)
        return hearts <= 0
    }

    fun addHeart() {
        hearts = (hearts + 1).coerceAtMost(maxHearts)
    }

    fun assignWord(word: DDIWordPool.WordEntry, timerSeconds: Int) {
        currentWord = word
        wordTimerSeconds = timerSeconds
        maxWordTimerSeconds = timerSeconds
    }
}
