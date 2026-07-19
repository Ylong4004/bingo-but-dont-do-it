package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.game.DDIGameHistoryService
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.event.model.BingoTileCapturedEvent
import me.jfenn.bingo.common.options.DDIObjectiveMode
import me.jfenn.bingo.common.options.DDISpecialEventType
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.integrations.voice.VoiceKeywordBridge
import me.jfenn.bingo.integrations.voice.VoiceKeywordDetection
import me.jfenn.bingo.integrations.voice.VoiceKeywordTarget
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventCatalog
import me.jfenn.bingo.integrations.ddi.special.DDISpecialHeartAdjustment
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

/** 单个玩家或队伍所拥有目标的不可变诊断投影。 */
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
    val ruleSummary: String?,
    val hearts: Int,
    val maxHearts: Int,
    val timerSeconds: Int,
    val maxTimerSeconds: Int,
    val isEliminated: Boolean,
    val assignmentRevision: Long,
)

/** 当前权威 DDI 回合的不可变诊断投影。 */
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

/** 管理员调试操作的结构化结果。 */
data class DDIDebugActionResult(
    val success: Boolean,
    val message: String,
)

/** 指定玩家当前语音词条资格链的隐私安全快照。 */
data class DDIVoicePlayerDebugSnapshot(
    val hasRound: Boolean,
    val isParticipant: Boolean,
    val isActiveParticipant: Boolean,
    val objectiveId: String?,
    val objectiveName: String?,
    val assignmentRevision: Long?,
    val wordId: String?,
    val wordText: String?,
    val isVoiceWord: Boolean,
    val hasConsent: Boolean,
    val isVoiceConnected: Boolean,
    val isTargetPublished: Boolean,
)

/**
 * 服务端权威 DDI 回合状态。
 *
 * 在个人模式中，每位参与者拥有一个目标。在队伍共享模式中，
 * 每支 Bingo 队伍只拥有一个目标、一条词条、一个计时器和一个生命池。
 * 所有状态变更都只发生在服务端线程。
 */
class DDIObjectiveManager(
    private val state: BingoState,
    private val wordPool: DDIWordPool,
    private val triggerDetector: DDITriggerDetector,
    private val packets: DDIServerPackets,
    private val tabLivesService: DDITabLivesService,
    private val historyService: DDIGameHistoryService,
    private val playerSettingsService: PlayerSettingsService,
    private val log: Logger,
    private val violationAdjudicator: DDIViolationAdjudicator = DDIViolationAdjudicator(),
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
    /** 仅保存在当前服务端进程中，并在下一局成功启动时一次性消费。 */
    private var debugNextRoundWordId: String? = null

    var onCompletedHandler: ((DDIRoundResult) -> Unit)? = null

    val hasRound: Boolean
        get() = currentServer != null && currentGameId != null

    /**
     * 返回与内部状态分离的不可变诊断数据。命令必须使用此方法，
     * 而不能持有指向可变权威映射的引用。
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
                    ruleSummary = word?.rule?.diagnosticName(),
                    hearts = objective.hearts,
                    maxHearts = objective.maxHearts,
                    timerSeconds = objective.wordTimerSeconds,
                    maxTimerSeconds = objective.maxWordTimerSeconds,
                    isEliminated = objective.isEliminated,
                    assignmentRevision = objective.assignmentRevision,
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

    /** 为 Brigadier 补全和调试查询返回稳定排序的全部词条 ID。 */
    internal fun debugWordIds(): List<String> {
        if (!hasRound) wordPool.setCustomVoiceKeywords(state.options.ddiVoiceCustomKeywords)
        return wordPool.getAllWords().map { it.id }.distinct().sorted()
    }

    internal fun debugWord(id: String): DDIWordPool.WordEntry? {
        if (!hasRound) wordPool.setCustomVoiceKeywords(state.options.ddiVoiceCustomKeywords)
        return wordPool.findById(id)
    }

    internal fun debugNextRoundWordId(): String? = debugNextRoundWordId

    /** 仅允许在大厅中指定下一局所有目标的首个词条。 */
    internal fun configureDebugNextRoundWord(id: String): DDIDebugActionResult {
        if (state.state != GameState.PREGAME || hasRound) {
            return DDIDebugActionResult(false, "只能在 Bingo 大厅中指定下一局首词条。")
        }
        val word = debugWord(id)
            ?: return DDIDebugActionResult(false, "未知词条 ID：$id")
        debugNextRoundWordId = word.id
        return DDIDebugActionResult(
            true,
            "下一局所有 DDI 目标的首词条已指定为 ${word.id}（${word.displayText}）。",
        )
    }

    internal fun clearDebugNextRoundWord(): DDIDebugActionResult {
        if (state.state != GameState.PREGAME || hasRound) {
            return DDIDebugActionResult(false, "只能在 Bingo 大厅中清除下一局首词条。")
        }
        val previous = debugNextRoundWordId
        debugNextRoundWordId = null
        return DDIDebugActionResult(
            true,
            previous?.let { "已清除下一局首词条：$it。" } ?: "当前没有指定下一局首词条。",
        )
    }

    internal fun debugVoiceState(playerId: UUID): DDIVoicePlayerDebugSnapshot {
        val objective = objectiveFor(playerId)
        val word = objective?.currentWord
        return DDIVoicePlayerDebugSnapshot(
            hasRound = hasRound && !roundCompleted,
            isParticipant = playerStates.containsKey(playerId),
            isActiveParticipant = hasRound && !roundCompleted &&
                playerId !in inactivePlayerIds && objective?.isAlive == true,
            objectiveId = objective?.objectiveId,
            objectiveName = objective?.objectiveName,
            assignmentRevision = objective?.assignmentRevision,
            wordId = word?.id,
            wordText = word?.displayText,
            isVoiceWord = word?.category == DDIWordPool.VOICE_CATEGORY,
            hasConsent = playerSettingsService.getPlayer(playerId).ddiVoiceConsent,
            isVoiceConnected = VoiceKeywordBridge.isPlayerConnected(playerId),
            isTargetPublished = voiceTargets().containsKey(playerId),
        )
    }

    init {
        triggerDetector.onSignalHandler = ::onSignal
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
        triggerDetector.currentRuleHandler = { playerId ->
            if (roundCompleted) null
            else objectiveFor(playerId)
                ?.takeIf { playerId !in inactivePlayerIds }
                ?.takeIf { it.isAlive }
                ?.currentWord
                ?.rule
        }
        triggerDetector.isEnemyPlayerHandler = { attackerId, victimId ->
            val attacker = playerStates[attackerId]
            val victim = playerStates[victimId]
            attacker != null && victim != null &&
                attackerId != victimId &&
                attacker.teamKey != victim.teamKey &&
                attackerId !in inactivePlayerIds && victimId !in inactivePlayerIds &&
                objectiveFor(attackerId)?.isAlive == true && objectiveFor(victimId)?.isAlive == true
        }
        triggerDetector.onEnemyPlayerDamageHandler = ::onEnemyPlayerDamage
    }

    /** 开始新回合。该回合的参赛名单、归属模式和选项均会固定。 */
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
        wordPool.setCustomVoiceKeywords(state.options.ddiVoiceCustomKeywords)
        val forcedInitialWordId = debugNextRoundWordId
        val forcedInitialWord = forcedInitialWordId?.let(wordPool::findById)
        debugNextRoundWordId = null

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

        // 在发送任何初始即时词条通知前，先清除之前的客户端投影。
        sendResetToClients(server)
        triggerDetector.register()

        objectives.forEach { objective ->
            val initialWord = forcedInitialWord
                ?.takeIf { isWordAvailableForObjective(objective, it) }
                ?: drawNextWord(objective, previous = null)
                ?: error("DDI word pool has no rule available for ${objective.objectiveId}")
            if (forcedInitialWordId != null && initialWord.id != forcedInitialWordId) {
                log.warn(
                    "[DDI Debug] Forced first word {} was unavailable for {}; used {}",
                    forcedInitialWordId,
                    objective.objectiveId,
                    initialWord.id,
                )
            }
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

    /** 停止当前回合。可安全重复调用。 */
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

    internal fun onSignal(player: ServerPlayerEntity, signal: DDISignal): Boolean {
        return applyPlayerSignal(player, signal, finalizeWinCheck = true)
    }

    /** 供进程级语音桥接器使用的不可变投影。 */
    internal fun voiceTargets(): Map<UUID, VoiceKeywordTarget> {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return emptyMap()
        if (!state.options.ddiVoiceKeywordsEnabled || !VoiceKeywordBridge.status().isReady) {
            return emptyMap()
        }
        val gameId = currentGameId ?: return emptyMap()
        return buildMap {
            objectiveStates.values.forEach { objective ->
                val word = objective.currentWord ?: return@forEach
                if (word.category != DDIWordPool.VOICE_CATEGORY ||
                    !isVoiceWordAvailable(objective, word)
                ) return@forEach

                val target = VoiceKeywordTarget(
                    gameId = gameId,
                    objectiveId = objective.objectiveId,
                    revision = objective.assignmentRevision,
                    wordId = word.id,
                    subjectIds = word.rule.subjectIds,
                )
                activeOnlineMemberIds(objective).forEach { put(it, target) }
            }
        }
    }

    /** 在桥接器令牌和作用域令牌均验证通过后，由服务端线程进行结算。 */
    internal fun onVoiceKeywordDetection(detection: VoiceKeywordDetection): Boolean {
        val expected = voiceTargets()[detection.playerId] ?: return false
        if (expected != detection.target || detection.matchedSubjectId !in expected.subjectIds) {
            return false
        }
        when (
            violationAdjudicator.decide(
                DDIViolationEvidence(
                    source = DDIEvidenceSource.VOICE_RECOGNITION,
                    exactTargetMatch = true,
                    confidence = detection.confidence,
                ),
            )
        ) {
            DDIAdjudicationDecision.AUTOMATIC_PENALTY -> Unit
            DDIAdjudicationDecision.MANUAL_ACCUSATION_ONLY,
            DDIAdjudicationDecision.REJECTED,
            -> return false
        }
        val player = currentServer?.playerManager?.getPlayer(detection.playerId) ?: return false
        return applyPlayerSignal(
            player,
            DDISignal(
                kind = DDISignalKind.VOICE_KEYWORD_SPOKEN,
                subjectId = detection.matchedSubjectId,
            ),
            finalizeWinCheck = true,
        )
    }

    /** 应用大厅或局内的自定义关键词变更，同时不修改静态词条。 */
    internal fun updateCustomVoiceKeywords(keywords: Iterable<String>) {
        wordPool.setCustomVoiceKeywords(keywords)
        rerollUnavailableVoiceObjectives()
    }

    internal fun refreshVoiceAvailability() {
        rerollUnavailableVoiceObjectives()
    }

    /** 不可变 DDI 名单中在线且未被淘汰的成员。 */
    internal fun activePlayers(): List<ServerPlayerEntity> {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return emptyList()
        val server = currentServer ?: return emptyList()
        return server.playerManager.playerList.filter { player ->
            player.uuid !in inactivePlayerIds && objectiveFor(player.uuid)?.isAlive == true
        }
    }

    internal fun activeObjectiveId(playerId: UUID): String? {
        if (!hasRound || roundCompleted || playerId in inactivePlayerIds) return null
        return objectiveFor(playerId)?.takeIf { it.isAlive }?.objectiveId
    }

    /** 强制发放指定词条；调试操作有意绕过“已触发词条不再随机抽取”的历史。 */
    internal fun debugForceWord(playerId: UUID, wordId: String): DDIDebugActionResult {
        val objective = debugActiveObjective(playerId)
            ?: return DDIDebugActionResult(false, "该玩家当前没有存活的 DDI 目标。")
        val word = wordPool.findById(wordId)
            ?: return DDIDebugActionResult(false, "未知词条 ID：$wordId")
        if (!isWordAvailableForObjective(objective, word)) {
            return DDIDebugActionResult(
                false,
                "词条 $wordId 当前不可用；语音词条请先检查模型、授权、连接与目标发布状态。",
            )
        }

        assignResolvedWord(objective, word, announceInstant = true)
        syncObjectiveToAll(objective)
        if (!roundCompleted) checkWinCondition()
        val current = objective.currentWord
        val suffix = if (current?.id == word.id) {
            ""
        } else {
            " 该词条属于即时规则，结算后当前词条为 ${current?.id ?: "无"}。"
        }
        return DDIDebugActionResult(
            true,
            "已为 ${displayTeamName(objective)} 强制发放 $wordId。$suffix",
        )
    }

    /** 无惩罚重抽，并继续遵守本队已触发词条的硬去重历史。 */
    internal fun debugRerollWord(playerId: UUID): DDIDebugActionResult {
        val objective = debugActiveObjective(playerId)
            ?: return DDIDebugActionResult(false, "该玩家当前没有存活的 DDI 目标。")
        val previous = objective.currentWord
        val next = drawNextWord(objective, previous)
            ?: return DDIDebugActionResult(false, "没有符合当前限制且未被本队触发过的替代词条。")
        assignResolvedWord(objective, next, announceInstant = true)
        syncObjectiveToAll(objective)
        if (!roundCompleted) checkWinCondition()
        return DDIDebugActionResult(
            true,
            "已为 ${displayTeamName(objective)} 无惩罚换词：${previous?.id ?: "无"} → ${objective.currentWord?.id ?: "无"}。",
        )
    }

    internal fun debugRerollAllWords(): DDIDebugActionResult {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) {
            return DDIDebugActionResult(false, "当前没有正在进行的 DDI 回合。")
        }
        var changed = 0
        var skipped = 0
        objectiveStates.values
            .filter { it.isAlive }
            .sortedBy { it.objectiveId }
            .forEach { objective ->
                if (roundCompleted) return@forEach
                val next = drawNextWord(objective, objective.currentWord)
                if (next == null) {
                    skipped++
                } else {
                    assignResolvedWord(objective, next, announceInstant = true)
                    syncObjectiveToAll(objective)
                    changed++
                }
            }
        if (changed > 0 && !roundCompleted) checkWinCondition()
        return DDIDebugActionResult(
            changed > 0,
            "已无惩罚换词 $changed 个目标，跳过 $skipped 个无可用候选目标。",
        )
    }

    /**
     * 跳过音频和 Vosk，直接构造当前目标的合法检测结果，
     * 用于确认识别后的服务端校验、触发与扣血链路。
     */
    internal fun debugSimulateVoiceDetection(playerId: UUID): DDIDebugActionResult {
        val target = voiceTargets()[playerId]
            ?: return DDIDebugActionResult(
                false,
                "该玩家当前没有已发布的语音目标；请先运行 voice debug <玩家> 查看资格链。",
            )
        val subjectId = target.subjectIds.sorted().first()
        val accepted = onVoiceKeywordDetection(
            VoiceKeywordDetection(
                playerId = playerId,
                target = target,
                matchedSubjectId = subjectId,
                confidence = 1.0,
            )
        )
        return DDIDebugActionResult(
            accepted,
            if (accepted) {
                "模拟检测已被 DDI 接受并完成结算；故障位于音频/ASR/桥接阶段。"
            } else {
                "模拟检测被 DDI 拒绝；请检查对局状态、分配版本和同刻重复触发保护。"
            },
        )
    }

    private fun debugActiveObjective(playerId: UUID): DDIObjectiveState? {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return null
        if (playerId in inactivePlayerIds) return null
        return objectiveFor(playerId)?.takeIf { it.isAlive }
    }

    /**
     * 应用一次特殊事件造成的生命变化，但暂不判定胜者。
     * 事件桥接器会在整个事件回调结算完毕后调用 [finalizeSpecialHeartAdjustments]，
     * 因此多个目标同时归零时，不会由映射或玩家的迭代顺序决定胜者。
     */
    internal fun adjustSpecialHeart(
        objectiveId: String,
        delta: Int,
        eventType: DDISpecialEventType,
        actorId: UUID?,
    ): DDISpecialHeartAdjustment {
        val objective = objectiveStates[objectiveId]
        val before = objective?.hearts ?: 0
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING ||
            objective == null || !objective.isAlive || delta == 0
        ) {
            return DDISpecialHeartAdjustment(
                requestedDelta = delta,
                appliedDelta = 0,
                hearts = before,
                maxHearts = objective?.maxHearts ?: 0,
                eliminated = objective?.isEliminated == true,
            )
        }

        objective.hearts = (before + delta).coerceIn(0, objective.maxHearts)
        val applied = objective.hearts - before
        val eliminated = objective.hearts <= 0
        if (applied < 0) {
            historyService.recordDamage(
                teamKey = objective.teamKey,
                teamName = displayTeamName(objective),
                wordText = "特殊事件：${DDISpecialEventCatalog[eventType].displayName}",
                actorName = actorId?.let { playerStates[it]?.playerName },
                heartsRemaining = objective.hearts,
                maxHearts = objective.maxHearts,
            )
        }
        if (eliminated) {
            eliminate(objective)
            clearWord(objective)
        }
        syncObjectiveToAll(objective)
        tabLivesService.refresh()
        return DDISpecialHeartAdjustment(
            requestedDelta = delta,
            appliedDelta = applied,
            hearts = objective.hearts,
            maxHearts = objective.maxHearts,
            eliminated = eliminated,
        )
    }

    internal fun finalizeSpecialHeartAdjustments() {
        checkWinCondition()
    }

    private fun applyPlayerSignal(
        player: ServerPlayerEntity,
        signal: DDISignal,
        finalizeWinCheck: Boolean,
    ): Boolean {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return false
        if (player.uuid in inactivePlayerIds) return false

        val participant = playerStates[player.uuid] ?: return false
        val objective = objectiveFor(player.uuid)?.takeIf { it.isAlive } ?: return false
        return applySignal(objective, signal, participant.playerName, finalizeWinCheck)
    }

    private fun onEnemyPlayerDamage(
        attacker: ServerPlayerEntity,
        victim: ServerPlayerEntity,
    ): Pair<Boolean, Boolean> {
        val attackerAccepted = applyPlayerSignal(
            attacker,
            DDISignal(
                kind = DDISignalKind.ENEMY_PLAYER_DAMAGED,
                legacyAliases = setOf(DDITriggerType.DAMAGE_ENEMY_PLAYER),
            ),
            finalizeWinCheck = false,
        )
        val victimAccepted = applyPlayerSignal(
            victim,
            DDISignal(
                kind = DDISignalKind.DAMAGED_BY_ENEMY_PLAYER,
                legacyAliases = setOf(DDITriggerType.DAMAGED_BY_ENEMY_PLAYER),
            ),
            finalizeWinCheck = false,
        )
        if (attackerAccepted || victimAccepted) checkWinCondition()
        return attackerAccepted to victimAccepted
    }

    /** 只处理当前 Bingo 游戏发出的计分事务。 */
    internal fun onBingoTileCaptured(event: BingoTileCapturedEvent): Boolean {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return false
        if (event.gameId != currentGameId || event.x !in 0..4 || event.y !in 0..4) return false
        val bingoTeam = state.teams[event.team] ?: return false
        if (bingoTeam.cardId != event.cardId) return false

        val config = roundConfig ?: return false
        val objective = when (config.objectiveMode) {
            DDIObjectiveMode.TEAM_SHARED -> objectiveStates.values.firstOrNull {
                it.isTeamShared && it.teamKey == event.team && it.isAlive
            }
            DDIObjectiveMode.INDIVIDUAL -> event.playerId
                ?.takeIf { it !in inactivePlayerIds }
                ?.let(::objectiveFor)
                ?.takeIf { !it.isTeamShared && it.teamKey == event.team && it.isAlive }
        }
        val actorName = event.playerId?.let { playerStates[it]?.playerName }
        val accepted = objective?.let {
            applySignal(
                it,
                DDIBingoSignals.capturedTile(event.x, event.y),
                actorName,
                finalizeWinCheck = true,
            )
        } ?: false
        // 在个人模式中，队友可能占领另一名玩家词条所指定的固定格子。
        // 此时该词条对其拥有者已无法完成，因此立即更换词条，
        // 且不记录触发，也不扣除生命。
        if (!roundCompleted) rerollUnavailableBingoObjectives(event.team)
        return accepted
    }

    private fun rerollUnavailableBingoObjectives(teamKey: BingoTeamKey) {
        var changed = false
        objectiveStates.values
            .filter { it.teamKey == teamKey && it.isAlive }
            .forEach { objective ->
                if (roundCompleted) return@forEach
                val word = objective.currentWord ?: return@forEach
                if (word.rule.signalKind != DDISignalKind.BINGO_TILE_CAPTURED ||
                    isWordAvailableForObjective(objective, word)
                ) return@forEach

                val nextWord = drawNextWord(objective, word)
                if (nextWord == null) {
                    completeByPoolExhaustion(objective)
                } else {
                    assignResolvedWord(objective, nextWord, announceInstant = true)
                }
                syncObjectiveToAll(objective)
                changed = true
            }
        if (changed && !roundCompleted) checkWinCondition()
    }

    private fun applySignal(
        objective: DDIObjectiveState,
        signal: DDISignal,
        actorName: String?,
        finalizeWinCheck: Boolean,
    ): Boolean {
        val currentWord = objective.currentWord ?: return false
        val progress = DDIRuleEngine.apply(currentWord.rule, objective.ruleProgress, signal)
            ?: return false

        val triggerTick = currentServer?.ticks?.toLong() ?: return false
        if (objective.lastAcceptedTriggerTick == triggerTick) return false
        objective.ruleProgress = progress.progress
        if (!progress.completed) return false
        // 此值会特意跨词条变更保留，防止两名队友的回调在同一个实际游戏刻内
        // 结算两条词条，同时又不会吞掉对新分配词条的合法操作。
        objective.lastAcceptedTriggerTick = triggerTick

        if (currentWord.rule.matchBehavior == DDIMatchBehavior.SATISFY_DEADLINE) {
            objective.deadlineSatisfied = true
            return true
        }

        // 即时词条通常会在发放时完成结算。保留此分支作为保险，
        // 以防未来的词条提供器直接分配即时词条。
        if (currentWord.triggerType == DDITriggerType.INSTANT_LOSE_HEART ||
            currentWord.triggerType == DDITriggerType.INSTANT_GAIN_HEART
        ) {
            assignResolvedWord(objective, currentWord, announceInstant = true)
            syncObjectiveToAll(objective)
            if (finalizeWinCheck) checkWinCondition()
            return true
        }

        settleViolation(objective, currentWord, actorName)
        syncObjectiveToAll(objective)
        if (finalizeWinCheck) checkWinCondition()
        return true
    }

    /** 控制器激活期间每个服务端秒调用一次。 */
    fun tickWordTimers() {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return
        rerollUnavailableVoiceObjectives()

        // 先完成所有计时器状态变更，再检查胜者，避免同一秒内多队淘汰时
        // 由迭代顺序决定结果。
        for (objective in objectiveStates.values) {
            if (!objective.isAlive || objective.currentWord == null) continue
            objective.wordTimerSeconds = (objective.wordTimerSeconds - 1).coerceAtLeast(0)
            if (objective.wordTimerSeconds == 0) {
                handleExpiredObjective(objective)
                syncObjectiveToAll(objective)
                if (roundCompleted) break
            }
        }
        checkWinCondition()
    }

    /** 由服务端计时循环和专门的截止时间契约测试共同使用。 */
    internal fun handleExpiredObjective(objective: DDIObjectiveState) {
        val previous = objective.currentWord ?: return
        if (previous.rule.deadlineBehavior == DDIDeadlineBehavior.TRIGGER_ON_EXPIRY) {
            if (!objective.deadlineSatisfied) {
                settleViolation(objective, previous, actorName = null)
                return
            }
        }

        val nextWord = drawNextWord(objective, previous)
        if (nextWord == null) {
            completeByPoolExhaustion(objective)
        } else {
            assignResolvedWord(objective, nextWord, announceInstant = true)
        }
    }

    /**
     * 个人模式下，离线玩家的目标会直接判负。队伍共享模式下，只要名单中
     * 还有其他成员在线，队伍就会继续存活；最后一名成员断开连接时队伍判负，
     * 以免出现永远不会淘汰的空队伍。
     */
    fun onPlayerDisconnect(playerId: UUID) {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return
        val participant = playerStates[playerId] ?: return
        val objective = objectiveFor(playerId)?.takeIf { it.isAlive } ?: return

        // 共享队伍成员断开连接不会使队伍目标判负，但其边沿和进度状态
        // 绝不能在快速重连后继续保留，也不能把离线时间计入连续条件词条。
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
     * 固定名单中的参与者离开其快照记录的 Bingo 队伍时，
     * 将其移出当前 DDI 游戏。仍有成员的共享队伍可以继续；
     * 空的共享队伍以及所有个人目标会立即判负。
     */
    fun onPlayerTeamChanged(playerId: UUID, newTeamKey: BingoTeamKey?) {
        if (!hasRound || roundCompleted || state.state != GameState.PLAYING) return

        val participant = playerStates[playerId]
        if (participant == null) {
            // 获得特权而后加入的玩家不属于不可变 DDI 名单。
            currentServer?.playerManager?.getPlayer(playerId)?.changeGameMode(GameMode.SPECTATOR)
            return
        }
        val objective = objectiveFor(playerId) ?: return

        if (newTeamKey == participant.teamKey) {
            // 只有原共享队伍仍存活时，名单成员才能重新加入该队伍。
            // 个人淘汰不可逆转。
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

    /** 对所有已淘汰目标的成员，在重生或重连后重新强制设为旁观者。 */
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

    /** 发放词条，并立即解析有界的增减生命连锁。 */
    private fun assignResolvedWord(
        objective: DDIObjectiveState,
        firstWord: DDIWordPool.WordEntry,
        announceInstant: Boolean,
    ) {
        val config = roundConfig ?: return
        var nextWord = firstWord

        // 每条即时规则都会在再次抽取前写入硬历史，因此最多只需遍历词池一次。
        // 以词池大小作为上限，也可以防止未来即时词条较多的词池因任意固定上限
        // 而留下一个没有词条却仍存活的目标。
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

        // 执行到此处意味着词池在解析过程中发生了变化，
        // 或违反了稳定重复键的不变量。此时应干净地结束回合，
        // 而不是留下一个词条为空且永远不会淘汰的目标。
        log.error("[DDI] Instant-word resolution exceeded the pool bound for {}", objective.objectiveId)
        completeByPoolExhaustion(objective)
    }

    private fun clearWord(objective: DDIObjectiveState) {
        objective.clearWord()
        resetObjectiveDetection(objective)
    }

    /** 结算一次动作或截止时间违规，并发放下一条可用词条。 */
    private fun settleViolation(
        objective: DDIObjectiveState,
        word: DDIWordPool.WordEntry,
        actorName: String?,
    ) {
        teamWordHistory.record(objective.teamKey, word)
        val eliminated = objective.loseHeart()
        historyService.recordDamage(
            teamKey = objective.teamKey,
            teamName = displayTeamName(objective),
            wordText = word.displayText,
            actorName = actorName,
            heartsRemaining = objective.hearts,
            maxHearts = objective.maxHearts,
        )
        if (eliminated) eliminate(objective)
        broadcastTrigger(
            objective = objective,
            word = word,
            actorName = actorName,
            isElimination = eliminated,
            isGain = false,
        )

        if (!objective.isAlive) {
            clearWord(objective)
            return
        }
        val nextWord = drawNextWord(objective, word)
        if (nextWord == null) {
            completeByPoolExhaustion(objective)
        } else {
            assignResolvedWord(objective, nextWord, announceInstant = true)
        }
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
            predicate = { isWordAvailableForObjective(objective, it) },
        )
    }

    private fun isWordAvailableForObjective(
        objective: DDIObjectiveState,
        word: DDIWordPool.WordEntry,
    ): Boolean {
        if (word.category == DDIWordPool.VOICE_CATEGORY) {
            return isVoiceWordAvailable(objective, word)
        }
        if (word.rule.signalKind != DDISignalKind.BINGO_TILE_CAPTURED) return true
        val team = state.teams[objective.teamKey] ?: return false
        val card = state.getCard(team) ?: return false
        val coordinates = DDIBingoSignals.selectedCoordinates(word.rule) ?: return true
        return coordinates.any { (x, y) ->
            val bingoObjective = card.objective(x, y)?.second ?: return@any false
            !bingoObjective.hasAchieved(objective.teamKey)
        }
    }

    private fun isVoiceWordAvailable(
        objective: DDIObjectiveState,
        word: DDIWordPool.WordEntry,
    ): Boolean {
        if (!state.options.ddiVoiceKeywordsEnabled || !VoiceKeywordBridge.status().isReady) {
            return false
        }
        if (word.rule.signalKind != DDISignalKind.VOICE_KEYWORD_SPOKEN ||
            word.rule.subjectIds.isEmpty() ||
            wordPool.findById(word.id) == null
        ) return false
        val activeMembers = activeOnlineMemberIds(objective)
        return activeMembers.isNotEmpty() && activeMembers.all { playerId ->
            playerSettingsService.getPlayer(playerId).ddiVoiceConsent &&
                VoiceKeywordBridge.isPlayerConnected(playerId)
        }
    }

    private fun activeOnlineMemberIds(objective: DDIObjectiveState): List<UUID> {
        val server = currentServer ?: return emptyList()
        return objective.memberIds.filter { playerId ->
            playerId !in inactivePlayerIds && server.playerManager.getPlayer(playerId) != null
        }
    }

    /** 模型丢失、撤销同意或自定义词条被删除后，绝不能再造成惩罚。 */
    private fun rerollUnavailableVoiceObjectives() {
        if (!hasRound || roundCompleted) return
        var changed = false
        objectiveStates.values
            .filter { it.isAlive && it.currentWord?.category == DDIWordPool.VOICE_CATEGORY }
            .forEach { objective ->
                val word = objective.currentWord ?: return@forEach
                if (isVoiceWordAvailable(objective, word)) return@forEach
                val nextWord = drawNextWord(objective, word)
                if (nextWord == null) {
                    completeByPoolExhaustion(objective)
                } else {
                    assignResolvedWord(objective, nextWord, announceInstant = true)
                    syncObjectiveToAll(objective)
                }
                changed = true
            }
        if (changed && !roundCompleted) checkWinCondition()
    }

    internal fun tabLivesFor(playerId: UUID): Int? {
        return DDITabLivesProjection.resolve(
            playerId = playerId,
            roundActive = hasRound && !roundCompleted,
            inactivePlayerIds = inactivePlayerIds,
            playerObjectiveIds = playerObjectiveIds,
            objectiveStates = objectiveStates,
        )
    }

    private fun completeByPoolExhaustion(objective: DDIObjectiveState) {
        if (roundCompleted || winnerAnnounced) return
        clearWord(objective)
        roundCompleted = true
        winnerAnnounced = true
        triggerDetector.unregister()
        tabLivesService.refresh()
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
        tabLivesService.refresh()

        val winnerKey = aliveTeams.singleOrNull()
        onCompletedHandler?.invoke(
            winnerKey?.let(DDIRoundResult::Winner) ?: DDIRoundResult.Draw
        )
    }

    /** 清除所有可能过期的条目后，发送当前状态投影。 */
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
        // 调用此方法前，start() 会立即清除所有客户端投影，
        // 因此再逐个重置目标只会造成重复工作。
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
                    // 共享词条会对该队伍的每一名成员隐藏，
                    // 而不只是对触发本次同步的成员隐藏。
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
