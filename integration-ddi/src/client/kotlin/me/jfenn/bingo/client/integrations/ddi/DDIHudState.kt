package me.jfenn.bingo.client.integrations.ddi

import net.minecraft.util.Formatting
import java.util.UUID

/** 服务端权威个人/队伍 DDI 状态在客户端的投影。 */
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
    data class WordSlotInfo(
        val index: Int,
        val wordText: String,
        val timerSeconds: Int,
        val maxTimerSeconds: Int,
    )

    var mySlots: List<WordSlotInfo> = emptyList()
        private set
    var isMyEliminated: Boolean = false
        private set
    var hasOwnObjective: Boolean = false
        private set

    data class PlayerDDIInfo(
        val playerName: String,
        val slots: List<WordSlotInfo>,
        val hearts: Int,
        val maxHearts: Int,
        val isEliminated: Boolean,
    )

    val otherPlayers = mutableMapOf<UUID, PlayerDDIInfo>()

    var hasOwnTeam: Boolean = false
        private set
    var myTeamId: String = ""
        private set
    var myTeamName: String = ""
        private set
    var myTeamColor: Formatting = Formatting.WHITE
        private set
    var myTeamMembers: List<String> = emptyList()
        private set

    data class TeamDDIInfo(
        val teamName: String,
        val teamColor: Formatting,
        val memberNames: List<String>,
        val slots: List<WordSlotInfo>,
        val hearts: Int,
        val maxHearts: Int,
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
        slots: List<WordSlotInfo>,
        isEliminated: Boolean,
    ) {
        ensureMode(ProjectionMode.PLAYER)
        hasOwnObjective = true
        updateOwnStatus(hearts, maxHearts, slots, isEliminated)
    }

    fun updateOther(
        playerId: UUID,
        playerName: String,
        slots: List<WordSlotInfo>,
        hearts: Int,
        maxHearts: Int,
        isEliminated: Boolean,
    ) {
        ensureMode(ProjectionMode.PLAYER)
        val safeMaxHearts = maxHearts.coerceAtLeast(0)
        otherPlayers[playerId] = PlayerDDIInfo(
            playerName = playerName,
            slots = normalizeSlots(slots),
            hearts = hearts.coerceIn(0, safeMaxHearts),
            maxHearts = safeMaxHearts,
            isEliminated = isEliminated,
        )
        isVisible = true
    }

    fun updateTeam(
        teamId: String,
        teamName: String,
        teamColor: Formatting,
        memberNames: List<String>,
        slots: List<WordSlotInfo>,
        hearts: Int,
        maxHearts: Int,
        isEliminated: Boolean,
        isOwnTeam: Boolean,
    ) {
        ensureMode(ProjectionMode.TEAM)
        if (isOwnTeam) {
            hasOwnTeam = true
            myTeamId = teamId
            myTeamName = teamName
            myTeamColor = teamColor
            myTeamMembers = memberNames.toList()
            otherTeams.remove(teamId)
            // 服务端会有意把本队词条留空；即使版本不匹配的服务端填入该字段，
            // 客户端也不能接受或保留它。
            updateOwnStatus(hearts, maxHearts, slots, isEliminated)
            return
        }

        val safeMaxHearts = maxHearts.coerceAtLeast(0)
        otherTeams[teamId] = TeamDDIInfo(
            teamName = teamName,
            teamColor = teamColor,
            memberNames = memberNames.toList(),
            slots = normalizeSlots(slots),
            hearts = hearts.coerceIn(0, safeMaxHearts),
            maxHearts = safeMaxHearts,
            isEliminated = isEliminated,
        )
        isVisible = true
    }

    private fun updateOwnStatus(
        hearts: Int,
        maxHearts: Int,
        slots: List<WordSlotInfo>,
        isEliminated: Boolean,
    ) {
        myMaxHearts = maxHearts.coerceAtLeast(0)
        myHearts = hearts.coerceIn(0, myMaxHearts)
        mySlots = normalizeSlots(slots).map { it.copy(wordText = "") }
        isMyEliminated = isEliminated
        isVisible = true
        timerTicks = 0
    }

    private fun normalizeSlots(slots: List<WordSlotInfo>): List<WordSlotInfo> = slots
        .asSequence()
        .filter { it.index >= 0 }
        .distinctBy(WordSlotInfo::index)
        .sortedBy(WordSlotInfo::index)
        .map { slot ->
            val maxTimer = slot.maxTimerSeconds.coerceAtLeast(0)
            slot.copy(timerSeconds = slot.timerSeconds.coerceIn(0, maxTimer), maxTimerSeconds = maxTimer)
        }
        .toList()

    fun addTrigger(notification: TriggerNotification) {
        recentTriggers.add(notification)
        while (recentTriggers.size > MAX_NOTIFICATIONS) recentTriggers.removeAt(0)
    }

    /** 每个未暂停客户端 tick 结束时调用一次。 */
    fun tick() {
        recentTriggers.forEach { it.timeAliveMs += MILLIS_PER_TICK }
        recentTriggers.removeAll { it.isExpired() }

        if (!isVisible) return
        timerTicks++
        if (timerTicks < TICKS_PER_SECOND) return
        timerTicks = 0

        when (projectionMode) {
            ProjectionMode.PLAYER -> {
                if (!isMyEliminated) {
                    mySlots = mySlots.map { slot ->
                        if (slot.timerSeconds > 0) slot.copy(timerSeconds = slot.timerSeconds - 1) else slot
                    }
                }
                otherPlayers.entries.forEach { entry ->
                    val info = entry.value
                    if (!info.isEliminated) {
                        entry.setValue(info.copy(slots = info.slots.map { slot ->
                            if (slot.timerSeconds > 0) slot.copy(timerSeconds = slot.timerSeconds - 1) else slot
                        }))
                    }
                }
            }

            ProjectionMode.TEAM -> {
                if (hasOwnTeam && !isMyEliminated) {
                    mySlots = mySlots.map { slot ->
                        if (slot.timerSeconds > 0) slot.copy(timerSeconds = slot.timerSeconds - 1) else slot
                    }
                }
                otherTeams.entries.forEach { entry ->
                    val info = entry.value
                    if (!info.isEliminated) {
                        entry.setValue(info.copy(slots = info.slots.map { slot ->
                            if (slot.timerSeconds > 0) slot.copy(timerSeconds = slot.timerSeconds - 1) else slot
                        }))
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
        mySlots = emptyList()
        isMyEliminated = false
        hasOwnObjective = false
        otherPlayers.clear()
        hasOwnTeam = false
        myTeamId = ""
        myTeamName = ""
        myTeamColor = Formatting.WHITE
        myTeamMembers = emptyList()
        otherTeams.clear()
        recentTriggers.clear()
        isVisible = false
        timerTicks = 0
    }
}
