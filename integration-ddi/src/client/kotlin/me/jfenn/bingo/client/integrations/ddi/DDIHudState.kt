package me.jfenn.bingo.client.integrations.ddi

import java.util.UUID

/** Client projection of the server-authoritative individual or team DDI state. */
class DDIHudState {
    companion object {
        private const val TICKS_PER_SECOND = 20
        private const val MILLIS_PER_TICK = 50L
        private const val MAX_NOTIFICATIONS = 5
    }

    enum class ProjectionMode {
        PLAYER,
        TEAM,
    }

    var projectionMode: ProjectionMode? = null
        private set

    var myHearts: Int = 0
        private set
    var myMaxHearts: Int = 0
        private set
    var myTimerSeconds: Int = 0
        private set
    var myMaxTimerSeconds: Int = 0
        private set
    var isMyEliminated: Boolean = false
        private set
    var hasOwnObjective: Boolean = false
        private set

    data class PlayerDDIInfo(
        val playerName: String,
        val wordText: String,
        val hearts: Int,
        val maxHearts: Int,
        val timerSeconds: Int,
        val isEliminated: Boolean,
    )

    val otherPlayers = mutableMapOf<UUID, PlayerDDIInfo>()

    var hasOwnTeam: Boolean = false
        private set
    var myTeamId: String = ""
        private set
    var myTeamName: String = ""
        private set
    var myTeamMembers: List<String> = emptyList()
        private set

    data class TeamDDIInfo(
        val teamName: String,
        val memberNames: List<String>,
        val wordText: String,
        val hearts: Int,
        val maxHearts: Int,
        val timerSeconds: Int,
        val isEliminated: Boolean,
    )

    val otherTeams = mutableMapOf<String, TeamDDIInfo>()

    data class TriggerNotification(
        val actorName: String,
        val teamName: String?,
        val wordText: String,
        val remainingHearts: Int,
        val isElimination: Boolean,
        val isGain: Boolean,
        var timeAliveMs: Long = 0L,
    ) {
        fun isExpired(): Boolean = timeAliveMs >= 4_000L
    }

    val recentTriggers = mutableListOf<TriggerNotification>()

    var isVisible: Boolean = false
        private set

    private var timerTicks: Int = 0

    fun updateSelf(
        hearts: Int,
        maxHearts: Int,
        timerSeconds: Int,
        maxTimerSeconds: Int,
        isEliminated: Boolean,
    ) {
        ensureMode(ProjectionMode.PLAYER)
        hasOwnObjective = true
        updateOwnStatus(hearts, maxHearts, timerSeconds, maxTimerSeconds, isEliminated)
    }

    fun updateOther(
        playerId: UUID,
        playerName: String,
        wordText: String,
        hearts: Int,
        maxHearts: Int,
        timerSeconds: Int,
        maxTimerSeconds: Int,
        isEliminated: Boolean,
    ) {
        ensureMode(ProjectionMode.PLAYER)
        val safeMaxHearts = maxHearts.coerceAtLeast(0)
        val safeMaxTimer = maxTimerSeconds.coerceAtLeast(0)
        otherPlayers[playerId] = PlayerDDIInfo(
            playerName = playerName,
            wordText = wordText,
            hearts = hearts.coerceIn(0, safeMaxHearts),
            maxHearts = safeMaxHearts,
            timerSeconds = timerSeconds.coerceIn(0, safeMaxTimer),
            isEliminated = isEliminated,
        )
        isVisible = true
    }

    fun updateTeam(
        teamId: String,
        teamName: String,
        memberNames: List<String>,
        wordText: String,
        hearts: Int,
        maxHearts: Int,
        timerSeconds: Int,
        maxTimerSeconds: Int,
        isEliminated: Boolean,
        isOwnTeam: Boolean,
    ) {
        ensureMode(ProjectionMode.TEAM)
        if (isOwnTeam) {
            hasOwnTeam = true
            myTeamId = teamId
            myTeamName = teamName
            myTeamMembers = memberNames.toList()
            otherTeams.remove(teamId)
            // The server intentionally sends an empty own-team word. Do not
            // accept or retain it even if a mismatched server fills the field.
            updateOwnStatus(hearts, maxHearts, timerSeconds, maxTimerSeconds, isEliminated)
            return
        }

        val safeMaxHearts = maxHearts.coerceAtLeast(0)
        val safeMaxTimer = maxTimerSeconds.coerceAtLeast(0)
        otherTeams[teamId] = TeamDDIInfo(
            teamName = teamName,
            memberNames = memberNames.toList(),
            wordText = wordText,
            hearts = hearts.coerceIn(0, safeMaxHearts),
            maxHearts = safeMaxHearts,
            timerSeconds = timerSeconds.coerceIn(0, safeMaxTimer),
            isEliminated = isEliminated,
        )
        isVisible = true
    }

    private fun updateOwnStatus(
        hearts: Int,
        maxHearts: Int,
        timerSeconds: Int,
        maxTimerSeconds: Int,
        isEliminated: Boolean,
    ) {
        myMaxHearts = maxHearts.coerceAtLeast(0)
        myHearts = hearts.coerceIn(0, myMaxHearts)
        myMaxTimerSeconds = maxTimerSeconds.coerceAtLeast(0)
        myTimerSeconds = timerSeconds.coerceIn(0, myMaxTimerSeconds)
        isMyEliminated = isEliminated
        isVisible = true
        timerTicks = 0
    }

    fun addTrigger(notification: TriggerNotification) {
        recentTriggers.add(notification)
        while (recentTriggers.size > MAX_NOTIFICATIONS) recentTriggers.removeAt(0)
    }

    /** Called once at the end of each unpaused client tick. */
    fun tick() {
        recentTriggers.forEach { it.timeAliveMs += MILLIS_PER_TICK }
        recentTriggers.removeAll { it.isExpired() }

        if (!isVisible) return
        timerTicks++
        if (timerTicks < TICKS_PER_SECOND) return
        timerTicks = 0

        when (projectionMode) {
            ProjectionMode.PLAYER -> {
                if (!isMyEliminated && myTimerSeconds > 0) myTimerSeconds--
                otherPlayers.entries.forEach { entry ->
                    val info = entry.value
                    if (!info.isEliminated && info.timerSeconds > 0) {
                        entry.setValue(info.copy(timerSeconds = info.timerSeconds - 1))
                    }
                }
            }

            ProjectionMode.TEAM -> {
                if (hasOwnTeam && !isMyEliminated && myTimerSeconds > 0) myTimerSeconds--
                otherTeams.entries.forEach { entry ->
                    val info = entry.value
                    if (!info.isEliminated && info.timerSeconds > 0) {
                        entry.setValue(info.copy(timerSeconds = info.timerSeconds - 1))
                    }
                }
            }

            null -> Unit
        }
    }

    private fun ensureMode(mode: ProjectionMode) {
        if (projectionMode == mode) return
        val pendingNotifications = recentTriggers.toList()
        reset()
        recentTriggers.addAll(pendingNotifications)
        projectionMode = mode
        isVisible = true
    }

    fun reset() {
        projectionMode = null
        myHearts = 0
        myMaxHearts = 0
        myTimerSeconds = 0
        myMaxTimerSeconds = 0
        isMyEliminated = false
        hasOwnObjective = false
        otherPlayers.clear()
        hasOwnTeam = false
        myTeamId = ""
        myTeamName = ""
        myTeamMembers = emptyList()
        otherTeams.clear()
        recentTriggers.clear()
        isVisible = false
        timerTicks = 0
    }
}
