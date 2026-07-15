package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.game.DDIGameHistoryService
import me.jfenn.bingo.common.options.DDIObjectiveMode
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.player.PlayerProfile
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.GameMode
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DDIRoundConfig(
    val maxHearts: Int,
    val wordTimerSeconds: Int,
    val objectiveMode: DDIObjectiveMode,
) {
    companion object {
        fun validated(
            maxHearts: Int,
            wordTimerSeconds: Int,
            objectiveMode: DDIObjectiveMode,
        ) = DDIRoundConfig(
            maxHearts = maxHearts.coerceIn(1, 20),
            wordTimerSeconds = wordTimerSeconds.coerceIn(10, 600),
            objectiveMode = objectiveMode,
        )
    }
}

data class DDIParticipant(
    val profile: PlayerProfile,
    val teamKey: BingoTeamKey,
    val teamName: String = teamKey.label,
    val teamColor: Formatting = Formatting.WHITE,
)

/** Immutable diagnostic projection of one player- or team-owned objective. */
data class DDIObjectiveSnapshot(
    val objectiveId: String,
    val objectiveName: String,
    val teamId: String,
    val teamName: String,
    val memberNames: List<String>,
    val isTeamShared: Boolean,
    val wordId: String?,
    val wordText: String?,
    val triggerType: DDITriggerType?,
    val hearts: Int,
    val maxHearts: Int,
    val timerSeconds: Int,
    val maxTimerSeconds: Int,
    val isEliminated: Boolean,
)

/** Immutable diagnostic projection of the current authoritative DDI round. */
data class DDIRuntimeSnapshot(
    val gameId: UUID?,
    val config: DDIRoundConfig?,
    val participantCount: Int,
    val inactiveParticipantCount: Int,
    val isCompleted: Boolean,
    val objectives: List<DDIObjectiveSnapshot>,
) {
    val hasRound: Boolean get() = gameId != null && config != null
}

sealed interface DDIRoundResult {
    data class Winner(val teamKey: BingoTeamKey) : DDIRoundResult
    data object Draw : DDIRoundResult
}

/**
 * Authoritative server-side DDI round state.
 *
 * In individual mode each participant owns one objective. In team-shared mode
 * each Bingo team owns exactly one objective, word, timer and heart pool.
 * Mutations happen only on the server thread.
 */
class DDIObjectiveManager(
    private val state: BingoState,
    private val wordPool: DDIWordPool,
    private val triggerDetector: DDITriggerDetector,
    private val packets: DDIServerPackets,
    private val tabLivesService: DDITabLivesService,
    private val historyService: DDIGameHistoryService,
    private val log: Logger,
) {

    val playerStates = ConcurrentHashMap<UUID, DDIPlayerState>()
    val objectiveStates = ConcurrentHashMap<String, DDIObjectiveState>()

    private val playerObjectiveIds = ConcurrentHashMap<UUID, String>()
    private val inactivePlayerIds = ConcurrentHashMap.newKeySet<UUID>()
    private var currentServer: MinecraftServer? = null
    private var currentGameId: UUID? = null
    private var roundConfig: DDIRoundConfig? = null
    private var roundCompleted = false
    private var winnerAnnounced = false
    private val teamWordHistory = DDITeamWordHistory()

    var onCompletedHandler: ((DDIRoundResult) -> Unit)? = null

    val hasRound: Boolean
        get() = currentServer != null && currentGameId != null

    /**
     * Returns detached immutable values for diagnostics. Commands must use this
     * instead of retaining references to the mutable authoritative maps.
     */
    fun snapshot(): DDIRuntimeSnapshot {
        val objectives = objectiveStates.values
            .map { objective ->
                val word = objective.currentWord
                DDIObjectiveSnapshot(
                    objectiveId = objective.objectiveId,
                    objectiveName = objective.objectiveName,
                    teamId = objective.teamKey.id,
                    teamName = displayTeamName(objective),
                    memberNames = objective.memberNames.toList(),
                    isTeamShared = objective.isTeamShared,
                    wordId = word?.id,
                    wordText = word?.displayText,
                    triggerType = word?.triggerType,
                    hearts = objective.hearts,
                    maxHearts = objective.maxHearts,
                    timerSeconds = objective.wordTimerSeconds,
                    maxTimerSeconds = objective.maxWordTimerSeconds,
                    isEliminated = objective.isEliminated,
                )
            }
            .sortedWith(
                compareBy<DDIObjectiveSnapshot> { it.teamName.lowercase() }
                    .thenBy { it.objectiveName.lowercase() }
                    .thenBy { it.objectiveId }
            )

        return DDIRuntimeSnapshot(
            gameId = currentGameId,
            config = roundConfig,
            participantCount = playerStates.size,
            inactiveParticipantCount = inactivePlayerIds.size,
            isCompleted = roundCompleted,
            objectives = objectives,
        )
    }

    init {
        triggerDetector.onTriggeredHandler = ::onTriggered
        triggerDetector.activePlayerIdsHandler = {
            if (!hasRound || roundCompleted) emptySet()
            else playerStates.keys.asSequence()
                .filter { it !in inactivePlayerIds && objectiveFor(it)?.isAlive == true }
                .toSet()
        }
        triggerDetector.currentTriggerHandler = { playerId ->
            if (roundCompleted) null
            else objectiveFor(playerId)
                ?.takeIf { playerId !in inactivePlayerIds }
                ?.takeIf { it.isAlive }
                ?.currentWord
                ?.triggerType
        }
    }

    /** Starts a new round. The roster, ownership mode and options are fixed for that round. */
    fun start(
        server: MinecraftServer,
        gameId: UUID,
        participants: List<DDIParticipant>,
        config: DDIRoundConfig,
    ): Boolean {
        if (currentGameId == gameId && hasRound) return true
        stop(sendReset = false)

        val roster = participants.distinctBy { it.profile.uuid }
        val teamCount = roster.map { it.teamKey }.distinct().size
        if (teamCount < MINIMUM_TEAMS) {
            log.warn(
                "[DDI] Not starting game {}: DDI requires at least {} Bingo teams, found {}",
                gameId,
                MINIMUM_TEAMS,
                teamCount,
            )
            sendResetToClients(server)
            return false
        }

        currentServer = server
        currentGameId = gameId
        roundConfig = config
        roundCompleted = false
        winnerAnnounced = false
        playerStates.clear()
        objectiveStates.clear()
        playerObjectiveIds.clear()
        inactivePlayerIds.clear()
        teamWordHistory.reset()
        historyService.reset()

        roster.forEach { participant ->
            val profile = participant.profile
            playerStates[profile.uuid] = DDIPlayerState(
                playerId = profile.uuid,
                playerName = profile.name,
                teamKey = participant.teamKey,
            )
        }

        val objectiveGroups = when (config.objectiveMode) {
            DDIObjectiveMode.INDIVIDUAL -> roster.map(::listOf)
            DDIObjectiveMode.TEAM_SHARED -> roster.groupBy { it.teamKey }.values.toList()
        }

        val objectives = objectiveGroups.map { members ->
            val first = members.first()
            val isTeamShared = config.objectiveMode == DDIObjectiveMode.TEAM_SHARED
            val memberIds = members.mapTo(linkedSetOf()) { it.profile.uuid }
            val memberNames = members.map { it.profile.name }.sortedBy { it.lowercase() }
            val objectiveId = if (isTeamShared) {
                "team:${first.teamKey.id}"
            } else {
                "player:${first.profile.uuid}"
            }
            DDIObjectiveState(
                objectiveId = objectiveId,
                objectiveName = if (isTeamShared) first.teamName else first.profile.name,
                teamKey = first.teamKey,
                teamName = first.teamName,
                teamColor = first.teamColor,
                memberIds = memberIds,
                memberNames = memberNames,
                isTeamShared = isTeamShared,
                hearts = config.maxHearts,
                maxHearts = config.maxHearts,
                wordTimerSeconds = config.wordTimerSeconds,
                maxWordTimerSeconds = config.wordTimerSeconds,
            ).also { objective ->
                objectiveStates[objective.objectiveId] = objective
                objective.memberIds.forEach { playerObjectiveIds[it] = objective.objectiveId }
            }
        }
        objectives
            .distinctBy { it.teamKey }
            .forEach { historyService.registerTeam(it.teamKey, displayTeamName(it)) }

        // Clear a previous projection before any initial instant-word notices.
        sendResetToClients(server)
        triggerDetector.register()

        objectives.forEach { objective ->
            val initialWord = drawNextWord(objective, previous = null)
                ?: error("DDI word pool has no rule available for ${objective.objectiveId}")
            assignResolvedWord(objective, initialWord, announceInstant = true)
        }

        tabLivesService.start(::tabLivesFor)
        syncAllToAll()
        log.info(
            "[DDI] Started game {} in {} mode with {} objectives, {} participants and {} Bingo teams",
            gameId,
            config.objectiveMode,
            objectiveStates.size,
            playerStates.size,
            teamCount,
        )
        checkWinCondition()
        return true
    }

    /** Stops the current round. Safe to call more than once. */
    fun stop(sendReset: Boolean = true) {
        val server = currentServer
        if (sendReset && server != null) sendResetToClients(server)

        triggerDetector.unregister()
        tabLivesService.stop()
        playerStates.clear()
        objectiveStates.clear()
        playerObjectiveIds.clear()
        inactivePlayerIds.clear()
        teamWordHistory.reset()
        historyService.reset()
        currentServer = null
        currentGameId = null
        roundConfig = null
        roundCompleted = false
        winnerAnnounced = false
        log.debug("[DDI] Round state cleared")
    }

    internal fun onTriggered(player: ServerPlayerEntity, triggerType: DDITriggerType) {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return
        if (player.uuid in inactivePlayerIds) return

        val participant = playerStates[player.uuid] ?: return
        val objective = objectiveFor(player.uuid)?.takeIf { it.isAlive } ?: return
        val currentWord = objective.currentWord ?: return
        if (currentWord.triggerType != triggerType) return

        val triggerTick = currentServer?.ticks?.toLong() ?: return
        if (objective.lastAcceptedTriggerTick == triggerTick) return
        // This value deliberately survives word changes, preventing two
        // teammates' callbacks from settling two words in one physical tick,
        // without swallowing legitimate actions on the newly assigned word.
        objective.lastAcceptedTriggerTick = triggerTick

        // Instant entries normally settle while being dealt. Keep this branch
        // as a safety net if a future word provider assigns one directly.
        if (triggerType == DDITriggerType.INSTANT_LOSE_HEART ||
            triggerType == DDITriggerType.INSTANT_GAIN_HEART
        ) {
            assignResolvedWord(objective, currentWord, announceInstant = true)
            syncObjectiveToAll(objective)
            checkWinCondition()
            return
        }

        teamWordHistory.record(objective.teamKey, currentWord)
        val eliminated = objective.loseHeart()
        historyService.recordDamage(
            teamKey = objective.teamKey,
            teamName = displayTeamName(objective),
            wordText = currentWord.displayText,
            actorName = participant.playerName,
            heartsRemaining = objective.hearts,
            maxHearts = objective.maxHearts,
        )
        if (eliminated) eliminate(objective)
        broadcastTrigger(
            objective = objective,
            word = currentWord,
            actorName = participant.playerName,
            isElimination = eliminated,
            isGain = false,
        )

        if (objective.isAlive) {
            val nextWord = drawNextWord(objective, currentWord)
            if (nextWord == null) {
                completeByPoolExhaustion(objective)
            } else {
                assignResolvedWord(objective, nextWord, announceInstant = true)
            }
        } else {
            clearWord(objective)
        }

        syncObjectiveToAll(objective)
        checkWinCondition()
    }

    /** Called once per server second while the controller is Active. */
    fun tickWordTimers() {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return
        // Besides updating values, this notices if another system has claimed
        // the vanilla LIST slot and switches DDI to its name-suffix fallback.
        tabLivesService.refresh()

        // Complete all timer mutations before checking the winner so iteration
        // order cannot decide a same-second multi-team elimination.
        for (objective in objectiveStates.values) {
            if (!objective.isAlive || objective.currentWord == null) continue
            objective.wordTimerSeconds = (objective.wordTimerSeconds - 1).coerceAtLeast(0)
            if (objective.wordTimerSeconds == 0) {
                val previous = objective.currentWord
                val nextWord = drawNextWord(objective, previous)
                if (nextWord == null) {
                    completeByPoolExhaustion(objective)
                    syncObjectiveToAll(objective)
                    return
                } else {
                    assignResolvedWord(objective, nextWord, announceInstant = true)
                }
                syncObjectiveToAll(objective)
            }
        }
        checkWinCondition()
    }

    /**
     * Individual mode forfeits the leaving player's objective. Team-shared
     * mode keeps the team alive while another roster member remains online;
     * the final disconnect forfeits the team to avoid an immortal empty team.
     */
    fun onPlayerDisconnect(playerId: UUID) {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return
        val participant = playerStates[playerId] ?: return
        val objective = objectiveFor(playerId)?.takeIf { it.isAlive } ?: return

        // A disconnected shared-team member does not forfeit the team's
        // objective, but their edge/progress state must never survive a quick
        // reconnect or count offline time toward a continuous-condition word.
        triggerDetector.resetPlayerState(playerId)

        val server = currentServer ?: return
        val hasOnlineTeammate = objective.isTeamShared && objective.memberIds.any { memberId ->
            memberId != playerId &&
                memberId !in inactivePlayerIds &&
                server.playerManager.getPlayer(memberId) != null
        }
        if (hasOnlineTeammate) {
            log.info(
                "[DDI] {} disconnected; shared objective {} remains active",
                participant.playerName,
                objective.objectiveId,
            )
            return
        }

        objective.hearts = 0
        eliminate(objective)
        clearWord(objective)
        val message = if (objective.isTeamShared) {
            "§c${displayTeamName(objective)} §f已全员离线，按 DDI 规则淘汰。"
        } else {
            "§c${participant.playerName} §f已断线，按 DDI 规则淘汰。"
        }
        server.playerManager.broadcast(Text.literal(message), false)
        syncObjectiveToAll(objective)
        checkWinCondition()
    }

    /**
     * Removes a fixed-roster participant from active DDI play when they leave
     * their snapshot Bingo team. A surviving shared team may continue; an empty
     * shared team and every individual objective forfeit immediately.
     */
    fun onPlayerTeamChanged(playerId: UUID, newTeamKey: BingoTeamKey?) {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return

        val participant = playerStates[playerId]
        if (participant == null) {
            // A privileged late join is outside the immutable DDI roster.
            currentServer?.playerManager?.getPlayer(playerId)?.changeGameMode(GameMode.SPECTATOR)
            return
        }
        val objective = objectiveFor(playerId) ?: return

        if (newTeamKey == participant.teamKey) {
            // A roster member may rejoin their original shared team only while
            // that team is still alive. Individual elimination is irreversible.
            if (objective.isTeamShared && objective.isAlive) {
                inactivePlayerIds.remove(playerId)
                triggerDetector.resetPlayerState(playerId)
                currentServer?.playerManager?.getPlayer(playerId)?.let(::resyncTo)
            }
            return
        }

        if (!inactivePlayerIds.add(playerId)) return
        triggerDetector.resetPlayerState(playerId)
        currentServer?.playerManager?.getPlayer(playerId)?.changeGameMode(GameMode.SPECTATOR)

        val sharedTeamStillActive = objective.isTeamShared && objective.memberIds.any { memberId ->
            memberId !in inactivePlayerIds && currentServer?.playerManager?.getPlayer(memberId) != null
        }
        if (sharedTeamStillActive) {
            tabLivesService.refresh()
            log.info(
                "[DDI] {} left snapshot team {}; shared objective remains active",
                participant.playerName,
                participant.teamKey.id,
            )
            return
        }

        val message = if (objective.isTeamShared) {
            "§c${displayTeamName(objective)} §f已无有效成员，按 DDI 规则淘汰。"
        } else {
            "§c${participant.playerName} §f已离开开局队伍，按 DDI 规则淘汰。"
        }
        forfeit(objective, message)
    }

    /** Re-assert spectator after respawn/reconnect for every eliminated objective member. */
    fun enforceEliminatedSpectators() {
        if (!hasRound || state.state != GameState.PLAYING) return
        val server = currentServer ?: return
        for (player in server.playerManager.playerList) {
            val objective = objectiveFor(player.uuid)
            val mustSpectate = objective == null ||
                player.uuid in inactivePlayerIds ||
                objective.isEliminated
            if (mustSpectate && !player.isSpectator) player.changeGameMode(GameMode.SPECTATOR)
        }
    }

    /** Deals a word and immediately resolves bounded gain/lose-heart chains. */
    private fun assignResolvedWord(
        objective: DDIObjectiveState,
        firstWord: DDIWordPool.WordEntry,
        announceInstant: Boolean,
    ) {
        val config = roundConfig ?: return
        var nextWord = firstWord

        // Every instant rule is written to hard history before drawing again,
        // so at most one pass over the pool can be required. Basing the bound
        // on the pool size also keeps future instant-heavy pools from leaving
        // a live objective with no word after an arbitrary fixed limit.
        repeat(wordPool.size().coerceAtLeast(1)) {
            objective.assignWord(nextWord, config.wordTimerSeconds)
            resetObjectiveDetection(objective)

            when (nextWord.triggerType) {
                DDITriggerType.INSTANT_LOSE_HEART -> {
                    teamWordHistory.record(objective.teamKey, nextWord)
                    val eliminated = objective.loseHeart()
                    historyService.recordDamage(
                        teamKey = objective.teamKey,
                        teamName = displayTeamName(objective),
                        wordText = nextWord.displayText,
                        actorName = null,
                        heartsRemaining = objective.hearts,
                        maxHearts = objective.maxHearts,
                    )
                    if (eliminated) eliminate(objective)
                    if (announceInstant) {
                        broadcastTrigger(objective, nextWord, null, eliminated, isGain = false)
                    }
                    if (!objective.isAlive) {
                        clearWord(objective)
                        return
                    }
                }

                DDITriggerType.INSTANT_GAIN_HEART -> {
                    teamWordHistory.record(objective.teamKey, nextWord)
                    objective.addHeart()
                    if (announceInstant) {
                        broadcastTrigger(objective, nextWord, null, isElimination = false, isGain = true)
                    }
                }

                else -> return
            }

            nextWord = drawNextWord(objective, nextWord) ?: run {
                completeByPoolExhaustion(objective)
                return
            }
        }

        // Reaching this line would mean the pool changed while it was being
        // resolved or violated the stable repeat-key invariant. End cleanly
        // instead of leaving an immortal objective with a null word.
        log.error("[DDI] Instant-word resolution exceeded the pool bound for {}", objective.objectiveId)
        completeByPoolExhaustion(objective)
    }

    private fun clearWord(objective: DDIObjectiveState) {
        objective.currentWord = null
        objective.wordTimerSeconds = 0
        resetObjectiveDetection(objective)
    }

    private fun resetObjectiveDetection(objective: DDIObjectiveState) {
        val server = currentServer
        objective.memberIds.forEach { playerId ->
            triggerDetector.resetPlayerState(
                id = playerId,
                preserveDeathTimer = server?.playerManager?.getPlayer(playerId)?.isDead == true,
            )
        }
    }

    private fun eliminate(objective: DDIObjectiveState) {
        objective.isEliminated = true
        val server = currentServer ?: return
        objective.memberIds.forEach { playerId ->
            server.playerManager.getPlayer(playerId)?.changeGameMode(GameMode.SPECTATOR)
        }
    }

    private fun forfeit(objective: DDIObjectiveState, message: String) {
        if (!objective.isAlive) return
        objective.hearts = 0
        eliminate(objective)
        clearWord(objective)
        currentServer?.playerManager?.broadcast(Text.literal(message), false)
        syncObjectiveToAll(objective)
        checkWinCondition()
    }

    private fun objectiveFor(playerId: UUID): DDIObjectiveState? =
        playerObjectiveIds[playerId]?.let(objectiveStates::get)

    private fun drawNextWord(
        objective: DDIObjectiveState,
        previous: DDIWordPool.WordEntry?,
    ): DDIWordPool.WordEntry? {
        val activeTeammateRules = objectiveStates.values.asSequence()
            .filter { it.objectiveId != objective.objectiveId }
            .filter { it.teamKey == objective.teamKey && it.isAlive }
            .mapNotNull { it.currentWord?.repeatKey }
            .toSet()
        return wordPool.drawAvailable(
            previous = previous,
            triggeredRepeatKeys = teamWordHistory.get(objective.teamKey),
            softRepeatKeys = activeTeammateRules,
        )
    }

    internal fun tabLivesFor(playerId: UUID): Int? {
        if (!hasRound) return null
        val objective = objectiveFor(playerId) ?: return null
        return if (playerId in inactivePlayerIds || objective.isEliminated) 0 else objective.hearts
    }

    private fun completeByPoolExhaustion(objective: DDIObjectiveState) {
        if (roundCompleted || winnerAnnounced) return
        clearWord(objective)
        roundCompleted = true
        winnerAnnounced = true
        triggerDetector.unregister()
        currentServer?.playerManager?.broadcast(
            Text.literal(
                "§a★ ${displayTeamName(objective)} §f已触发本局全部可用词条，完成不要做挑战！"
            ),
            false,
        )
        log.info(
            "[DDI] Team {} exhausted every unique repeat key and wins the round",
            objective.teamKey.id,
        )
        onCompletedHandler?.invoke(DDIRoundResult.Winner(objective.teamKey))
    }

    private fun checkWinCondition() {
        if (!hasRound || roundCompleted || winnerAnnounced) return
        val aliveTeams = objectiveStates.values.asSequence()
            .filter { it.isAlive }
            .map { it.teamKey }
            .toSet()
        if (aliveTeams.size > 1) return

        roundCompleted = true
        winnerAnnounced = true
        triggerDetector.unregister()

        val winnerKey = aliveTeams.singleOrNull()
        onCompletedHandler?.invoke(
            winnerKey?.let(DDIRoundResult::Winner) ?: DDIRoundResult.Draw
        )
    }

    /** Sends the current projection after clearing every potentially stale entry. */
    fun resyncTo(target: ServerPlayerEntity) {
        packets.stateReset.send(target, DDIStateResetPacket())
        if (!hasRound) return
        syncSnapshotTo(target)
        tabLivesService.refresh()
    }

    private fun syncSnapshotTo(target: ServerPlayerEntity) {
        objectiveStates.values
            .sortedBy { it.objectiveName.lowercase() }
            .forEach { syncObjectiveTo(target, it) }
        if (state.state == GameState.PLAYING) {
            val objective = objectiveFor(target.uuid)
            if (objective == null || target.uuid in inactivePlayerIds || objective.isEliminated) {
                target.changeGameMode(GameMode.SPECTATOR)
            }
        }
    }

    private fun syncAllToAll() {
        val server = currentServer ?: return
        // start() clears every client projection immediately before calling
        // this method, so another reset per target would only duplicate work.
        for (target in server.playerManager.playerList) syncSnapshotTo(target)
        tabLivesService.refresh()
    }

    private fun syncObjectiveToAll(objective: DDIObjectiveState) {
        val server = currentServer ?: return
        for (target in server.playerManager.playerList) syncObjectiveTo(target, objective)
        tabLivesService.refresh()
    }

    private fun syncObjectiveTo(target: ServerPlayerEntity, objective: DDIObjectiveState) {
        if (objective.isTeamShared) {
            val isOwnTeam = target.uuid in objective.memberIds
            packets.teamSync.send(
                target,
                DDITeamSyncPacket(
                    teamId = objective.teamKey.id,
                    teamName = displayTeamName(objective),
                    teamColor = objective.teamColor,
                    memberNames = objective.memberNames,
                    // A shared word is hidden from every member of that team,
                    // not just from the member who caused this synchronization.
                    wordText = if (isOwnTeam) "" else objective.currentWord?.displayText.orEmpty(),
                    hearts = objective.hearts,
                    maxHearts = objective.maxHearts,
                    timerSeconds = objective.wordTimerSeconds,
                    maxTimerSeconds = objective.maxWordTimerSeconds,
                    isEliminated = objective.isEliminated,
                    isOwnTeam = isOwnTeam,
                ),
            )
            return
        }

        val playerId = objective.memberIds.single()
        val isSelf = target.uuid == playerId
        packets.wordSync.send(
            target,
            DDIWordSyncPacket(
                playerId = playerId,
                playerName = objective.objectiveName,
                wordText = if (isSelf) "" else objective.currentWord?.displayText.orEmpty(),
                hearts = objective.hearts,
                maxHearts = objective.maxHearts,
                timerSeconds = objective.wordTimerSeconds,
                maxTimerSeconds = objective.maxWordTimerSeconds,
                isEliminated = objective.isEliminated,
                isSelf = isSelf,
            ),
        )
    }

    private fun broadcastTrigger(
        objective: DDIObjectiveState,
        word: DDIWordPool.WordEntry,
        actorName: String?,
        isElimination: Boolean,
        isGain: Boolean,
    ) {
        val server = currentServer ?: return

        if (objective.isTeamShared) {
            val teamName = displayTeamName(objective)
            val actorPrefix = actorName?.let { "$it 代表$teamName " } ?: "$teamName "
            val message = when {
                isElimination -> "§c💀 $actorPrefix §f触发了「§b${word.displayText}§f」，全队淘汰！"
                isGain -> "§a💚 $actorPrefix §f抽到了「§b${word.displayText}§f」，共享生命 +1！❤×§c${objective.hearts}"
                else -> "§e⚡ $actorPrefix §f触发了「§b${word.displayText}§f」！队伍剩余 §c${objective.hearts} ❤️"
            }
            server.playerManager.broadcast(Text.literal(message), false)

            val packet = DDITeamTriggeredPacket(
                teamId = objective.teamKey.id,
                teamName = teamName,
                actorPlayerName = actorName.orEmpty(),
                wordText = word.displayText,
                heartsRemaining = objective.hearts,
                isElimination = isElimination,
                isGain = isGain,
            )
            for (target in server.playerManager.playerList) packets.teamTriggered.send(target, packet)
            return
        }

        val displayName = actorName ?: objective.objectiveName
        val message = when {
            isElimination -> "§c💀 $displayName §f已被淘汰！（词条：§b${word.displayText}§f）"
            isGain -> "§a💚 $displayName §f抽到了「§b${word.displayText}§f」，回心！❤×§c${objective.hearts}"
            else -> "§e⚡ $displayName §f触发了「§b${word.displayText}§f」！剩余 §c${objective.hearts} ❤️"
        }
        server.playerManager.broadcast(Text.literal(message), false)

        val packet = DDITriggeredPacket(
            playerId = objective.memberIds.single(),
            playerName = displayName,
            wordText = word.displayText,
            heartsRemaining = objective.hearts,
            isElimination = isElimination,
            isGain = isGain,
        )
        for (target in server.playerManager.playerList) packets.triggered.send(target, packet)
    }

    private fun displayTeamName(objective: DDIObjectiveState): String =
        objective.teamName.ifBlank { objective.teamKey.label }

    fun sendResetToClients(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            packets.stateReset.send(player, DDIStateResetPacket())
        }
    }

    private companion object {
        const val MINIMUM_TEAMS = 2
    }
}
