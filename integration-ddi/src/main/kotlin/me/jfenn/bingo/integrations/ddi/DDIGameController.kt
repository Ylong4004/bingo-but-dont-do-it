package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.game.DDIGameEndService
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.DDIObjectiveMode
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventConfig
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.util.UUID

sealed interface DDISessionState {
    data object Inactive : DDISessionState
    data class Active(val gameId: UUID) : DDISessionState
    data class Completed(val gameId: UUID) : DDISessionState
}

/** 将一个 DDI 会话绑定到权威的 Bingo 游戏作用域。 */
class DDIGameController(
    private val state: BingoState,
    private val options: BingoOptions,
    private val server: MinecraftServer,
    private val ddiManager: DDIObjectiveManager,
    private val specialEvents: DDISpecialEventService,
    private val voiceKeywords: DDIVoiceKeywordController,
    private val gameEndService: DDIGameEndService,
    events: ScopedEvents,
    private val log: Logger,
) {
    var sessionState: DDISessionState = DDISessionState.Inactive
        private set

    private var isShutdown = false
    private var pendingResult: DDIRoundResult? = null
    private var pendingResultTick: Long = Long.MIN_VALUE

    init {
        ddiManager.onCompletedHandler = { result ->
            val active = sessionState as? DDISessionState.Active
            if (active != null) {
                // 一旦 DDI 产生结果就立即停止效果和监听器，避免它们在
                // 用于 Bingo 裁决平局的一个游戏刻内继续运行。
                stopAuxiliarySystems()
                sessionState = DDISessionState.Completed(active.gameId)
                pendingResult = result
                pendingResultTick = server.ticks.toLong()
                log.info("[DDI] Game {} completed", active.gameId)
            }
        }

        events.onStateChange { event -> reconcile(event.to) }
        events.onGameTick { event ->
            if (isShutdown) return@onGameTick
            // 只获取本次回调开始前已经存在的结果。由下方计时器产生的结果
            // 要等到下一个游戏刻再处理，从而让 ScoredItemCheck 获得完整的一刻，
            // 优先赢得任何发生在同一游戏刻的竞态。
            val resultToFinalize = pendingResult?.takeIf {
                server.ticks.toLong() > pendingResultTick
            }
            ddiManager.enforceEliminatedSpectators()
            if (sessionState is DDISessionState.Active) {
                specialEvents.tickServerTick()
                voiceKeywords.tick()
                if (event.ticks % TICKS_PER_SECOND == 0 &&
                    sessionState is DDISessionState.Active
                ) {
                    ddiManager.tickWordTimers()
                }
            }
            if (resultToFinalize != null && pendingResult === resultToFinalize) {
                pendingResult = null
                pendingResultTick = Long.MIN_VALUE
                val winnerKey = (resultToFinalize as? DDIRoundResult.Winner)?.teamKey
                if (!gameEndService.end(winnerKey)) {
                    log.warn("[DDI] Could not apply deferred round result {}", resultToFinalize)
                }
            }
        }
        events.onPlayerChannelRegister { event ->
            // 在 Join 时 ScopedEvents 也会尝试同步；数据包处理器会检查支持情况，
            // 因此 ChannelRegister 才是能够可靠成功发送的时机。
            if (!isShutdown) ddiManager.resyncTo(event.player.player)
        }
        events.onPlayerDisconnect { event ->
            if (!isShutdown) {
                specialEvents.onPlayerLeaving(event.player.player)
                ddiManager.onPlayerDisconnect(event.player.uuid)
            }
        }
        events.onPlayerRespawn { event ->
            if (!isShutdown) ddiManager.enforceEliminatedSpectators()
        }
        events.onChangeTeam { event ->
            if (!isShutdown) ddiManager.onPlayerTeamChanged(event.player.uuid, event.team?.key)
        }
        events.onBingoTileCaptured { event ->
            if (!isShutdown) ddiManager.onBingoTileCaptured(event)
        }

        // 插件钩子通常会在 ScopeManager 重放保存状态之前创建此控制器。
        // 此处也执行一次状态协调，确保从其他入口构造时同样安全。
        reconcile(state.state)
        log.info("[DDI] GameController initialized in state {}", sessionState)
    }

    private fun reconcile(gameState: GameState) {
        if (isShutdown) return
        when (gameState) {
            GameState.PLAYING -> ensureStarted()
            GameState.POSTGAME -> completeAndStop()
            GameState.PREGAME -> reset()
            else -> Unit
        }
    }

    private fun ensureStarted() {
        val gameId = state.gameId
        when (val current = sessionState) {
            is DDISessionState.Active -> if (current.gameId == gameId) return
            // /bingo resume 会在 POSTGAME 清除旧 DDI 状态后，让同一 gameId
            // 重新进入 PLAYING。此时应启动一个新的 DDI 阶段，
            // 而不是在没有 DDI 的情况下静默恢复 Bingo。
            is DDISessionState.Completed -> Unit
            DDISessionState.Inactive -> Unit
        }

        // 恢复存档时可能不经过 PREGAME 就观察到新游戏。
        // 使用新 gameId 前先清除之前的本地会话。
        stopAuxiliarySystems()
        if (ddiManager.hasRound) ddiManager.stop(sendReset = true)

        if (!options.enableDDI) {
            sessionState = DDISessionState.Inactive
            log.debug("[DDI] Disabled for Bingo game {}", gameId)
            return
        }

        val registeredTeams = state.getRegisteredTeams()
            .filter { it.isPlaying() }
        val participatingTeams = when (options.ddiObjectiveMode) {
            DDIObjectiveMode.INDIVIDUAL -> registeredTeams
            DDIObjectiveMode.TEAM_SHARED -> registeredTeams.filter { team ->
                // 纳入参赛队伍中所有已注册的玩家档案，避免暂时离线的成员
                // 之后以未跟踪的旁观者身份加入，并收到自己队伍的词条。
                team.players.any { server.playerManager.getPlayer(it.uuid) != null }
            }
        }
        val participants = participatingTeams
            .asSequence()
            .flatMap { team ->
                team.players.asSequence().map { profile ->
                    DDIParticipant(
                        profile = profile,
                        teamKey = team.key,
                        teamName = team.getSimpleName().toString(),
                        teamColor = team.textColor,
                    )
                }
            }
            // 个人目标只为回合开始时在线的玩家创建。队伍模式已在上方
            // 按整队筛选，并保留每支参赛队伍固定注册名单中的全部成员。
            .filter {
                options.ddiObjectiveMode == DDIObjectiveMode.TEAM_SHARED ||
                    server.playerManager.getPlayer(it.profile.uuid) != null
            }
            .distinctBy { it.profile.uuid }
            .toList()
        val config = DDIRoundConfig.validated(
            maxHearts = options.ddiMaxHearts,
            wordTimerSeconds = options.ddiWordTimerSeconds,
            objectiveMode = options.ddiObjectiveMode,
        )

        // 必须在 start() 前设为 Active，因为解析初始即时词条时，
        // 可能通过上方回调同步完成整个 DDI 回合。
        sessionState = DDISessionState.Active(gameId)
        pendingResult = null
        pendingResultTick = Long.MIN_VALUE
        val started = ddiManager.start(server, gameId, participants, config)
        if (!started) {
            sessionState = DDISessionState.Completed(gameId)
            return
        }
        if (sessionState is DDISessionState.Active) {
            specialEvents.start(
                DDISpecialEventConfig(
                    enabled = options.ddiSpecialEventsEnabled,
                    intervalSeconds = options.ddiSpecialEventIntervalSeconds,
                    enabledEvents = options.ddiSpecialEventTypes,
                )
            )
            runCatching(voiceKeywords::start)
                .onFailure { log.error("[DDI Voice] Could not start the voice session", it) }
        }
    }

    private fun completeAndStop() {
        val gameId = state.gameId
        pendingResult = null
        pendingResultTick = Long.MIN_VALUE
        stopAuxiliarySystems()
        ddiManager.stop(sendReset = true)
        sessionState = DDISessionState.Completed(gameId)
    }

    private fun reset() {
        pendingResult = null
        pendingResultTick = Long.MIN_VALUE
        stopAuxiliarySystems()
        ddiManager.stop(sendReset = true)
        sessionState = DDISessionState.Inactive
    }

    /** 由内部插件钩子在 Koin 作用域关闭前调用。 */
    fun shutdown() {
        if (isShutdown) return
        isShutdown = true
        pendingResult = null
        pendingResultTick = Long.MIN_VALUE
        stopAuxiliarySystems()
        ddiManager.stop(sendReset = false)
        sessionState = DDISessionState.Inactive
        log.info("[DDI] GameController shut down")
    }

    private fun stopAuxiliarySystems() {
        runCatching(specialEvents::stop)
            .onFailure { log.error("[DDI Events] Failed to stop auxiliary event state", it) }
        runCatching(voiceKeywords::stop)
            .onFailure { log.error("[DDI Voice] Failed to stop the recognition session", it) }
    }

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
