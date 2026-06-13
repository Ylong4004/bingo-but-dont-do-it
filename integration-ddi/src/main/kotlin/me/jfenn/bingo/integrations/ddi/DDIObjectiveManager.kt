package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.packet.IServerPacketHandlerS2C
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * DDI 词条管理器 — 管理玩家词条分配、触发检测、心数和淘汰。
 * 独立于 Bingo 卡片系统运行，词条通过客户端 HUD 显示。
 */
class DDIObjectiveManager(
    private val state: BingoState,
    private val options: BingoOptions,
    private val wordPool: DDIWordPool,
    private val triggerDetector: DDITriggerDetector,
    private val log: Logger,
) {

    /** 玩家 DDI 状态映射 */
    val playerStates = ConcurrentHashMap<UUID, DDIPlayerState>()

    /** 触发冷却时间（毫秒） */
    private val triggerCooldownMs = 1500L

    /** 当前游戏服务器引用（游戏开始时设置，结束时清除） */
    private var currentServer: MinecraftServer? = null

    /** 网络包处理器（由 DDIGameController 在构造后注入） */
    var wordSyncHandler: IServerPacketHandlerS2C<DDIWordSyncPacket>? = null
    var triggeredHandler: IServerPacketHandlerS2C<DDITriggeredPacket>? = null
    var stateResetHandler: IServerPacketHandlerS2C<DDIStateResetPacket>? = null

    init {
        triggerDetector.onTriggeredHandler = { player, triggerType ->
            onTriggered(player, triggerType)
        }
    }

    // ==================== 游戏生命周期 ====================

    fun onGameStarting(server: MinecraftServer) {
        if (!options.enableDDI) return
        currentServer = server
        playerStates.clear()
        assignWordsToPlayers(server)
        triggerDetector.register()
        log.info("[DDI] Game started with {} players", playerStates.size)
    }

    fun onGameEnding() {
        triggerDetector.unregister()
        cleanup()
        log.info("[DDI] Game ended, DDI state cleaned up")
    }

    // ==================== 词条分配 ====================

    private fun assignWordsToPlayers(server: MinecraftServer) {
        val players = server.playerManager.playerList
        if (players.isEmpty()) return

        val words = if (players.size <= wordPool.size()) {
            wordPool.drawWords(players.size)
        } else {
            // 玩家数超过词条池大小：循环使用
            players.indices.map { wordPool.drawSingle() }
        }

        for ((i, player) in players.withIndex()) {
            val word = words[i]
            val ps = DDIPlayerState(
                playerId = player.uuid,
                currentWord = word,
                hearts = options.ddiMaxHearts,
                maxHearts = options.ddiMaxHearts,
                wordTimerSeconds = options.ddiWordTimerSeconds,
                maxWordTimerSeconds = options.ddiWordTimerSeconds,
            )
            playerStates[player.uuid] = ps
        }
    }

    // ==================== 触发处理 ====================

    internal fun onTriggered(player: ServerPlayerEntity, triggerType: DDITriggerType) {
        if (!options.enableDDI) return
        if (state.state != GameState.PLAYING) return

        val ps = playerStates[player.uuid] ?: return
        if (!ps.isAlive) return
        val currentWord = ps.currentWord ?: return

        // 冷却检查
        val now = System.currentTimeMillis()
        val lastTrigger = ps.lastTriggerTick[triggerType]
        if (lastTrigger != null && (now - lastTrigger) < triggerCooldownMs) return

        ps.lastTriggerTick[triggerType] = now

        // 检查是否匹配当前词条
        if (currentWord.triggerType != triggerType) return

        // 处理不同类型的词条
        when (triggerType) {
            DDITriggerType.INSTANT_LOSE_HEART -> handleInstantLose(player, ps)
            DDITriggerType.INSTANT_GAIN_HEART -> handleInstantGain(player, ps)
            else -> handleNormalTrigger(player, ps)
        }
    }

    private fun handleNormalTrigger(player: ServerPlayerEntity, ps: DDIPlayerState) {
        val eliminated = ps.loseHeart()
        val word = ps.currentWord ?: return

        if (eliminated) {
            ps.isEliminated = true
            broadcast(player, word, ps, true)

checkWinCondition(currentServer)
        } else {
            broadcast(player, word, ps, false)
        }
        replaceWord(ps)
    }

    private fun handleInstantLose(player: ServerPlayerEntity, ps: DDIPlayerState) {
        val eliminated = ps.loseHeart()
        val word = ps.currentWord ?: return

        if (eliminated) {
            ps.isEliminated = true
            broadcast(player, word, ps, true)

checkWinCondition(currentServer)
        } else {
            broadcast(player, word, ps, false)
        }
        replaceWord(ps)
    }

    private fun handleInstantGain(player: ServerPlayerEntity, ps: DDIPlayerState) {
        if (ps.hearts < ps.maxHearts) {
            ps.addHeart()
        }
        val word = ps.currentWord ?: return
        broadcast(player, word, ps, false, isGain = true)
        replaceWord(ps)
    }

    // ==================== 词条管理 ====================

    private fun replaceWord(ps: DDIPlayerState) {
        val newWord = wordPool.drawSingle()
        ps.assignWord(newWord, options.ddiWordTimerSeconds)

        val pid = ps.playerId
        triggerDetector.resetJumpCount(pid)
        triggerDetector.resetLookSameDir(pid)
        triggerDetector.resetPlaceCount(pid)
        triggerDetector.resetDropCount(pid)
        triggerDetector.resetNoJumpState(pid)
        triggerDetector.resetNoSneakState(pid)
        triggerDetector.resetNoSprintState(pid)
        triggerDetector.resetBlockAboveHeadState(pid)
    }

    private fun tickWordTimers() {
        for (ps in playerStates.values) {
            if (!ps.isAlive || ps.currentWord == null) continue
            ps.wordTimerSeconds--
            if (ps.wordTimerSeconds <= 0) {
                replaceWord(ps)
            }
        }
    }

    // ==================== Bingo 计分集成 ====================

    // ==================== 胜利条件 ====================

    private fun checkWinCondition(server: MinecraftServer?) {
        if (server == null) return
        val alivePlayers = playerStates.values.filter { it.isAlive }
        if (alivePlayers.size <= 1 && alivePlayers.isNotEmpty()) {
            val winner = alivePlayers.firstOrNull() ?: return
            val winnerPlayer = server.playerManager.getPlayer(winner.playerId) ?: return
            server.playerManager.broadcast(
                Text.literal("§6🏆 ${winnerPlayer.name.string} §f是 DDI 最后的幸存者！"), false
            )
        }
    }

    // ==================== 广播 ====================

    /** 将单个玩家的 DDI 状态同步给全体玩家 */
    fun syncPlayer(player: ServerPlayerEntity, ps: DDIPlayerState) {
        val server = currentServer ?: return
        val word = ps.currentWord
        for (target in server.playerManager.playerList) {
            val isSelf = target.uuid == player.uuid
            val packet = DDIWordSyncPacket(
                playerId = player.uuid,
                playerName = player.name.string,
                wordText = word?.displayText ?: "",
                hearts = ps.hearts,
                maxHearts = ps.maxHearts,
                timerSeconds = ps.wordTimerSeconds,
                maxTimerSeconds = ps.maxWordTimerSeconds,
                isEliminated = ps.isEliminated,
                isSelf = isSelf,
            )
            wordSyncHandler?.send(target, packet)
        }
    }

    /** 发送触发通知给全体玩家 */
    fun sendTriggered(player: ServerPlayerEntity, word: DDIWordPool.WordEntry, ps: DDIPlayerState, isElimination: Boolean, isGain: Boolean) {
        val server = currentServer ?: return
        val packet = DDITriggeredPacket(
            playerId = player.uuid,
            playerName = player.name.string,
            wordText = word.displayText,
            heartsRemaining = ps.hearts,
            isElimination = isElimination,
            isGain = isGain,
        )
        for (target in server.playerManager.playerList) {
            triggeredHandler?.send(target, packet)
        }
    }

    /** 发送状态重置给全体玩家 */
    fun sendResetToClients(server: MinecraftServer) {
        val packet = DDIStateResetPacket()
        for (player in server.playerManager.playerList) {
            stateResetHandler?.send(player, packet)
        }
    }

    private fun broadcast(
        player: ServerPlayerEntity,
        word: DDIWordPool.WordEntry,
        ps: DDIPlayerState,
        isElimination: Boolean,
        isGain: Boolean = false,
    ) {
        val server = currentServer ?: return
        val msg = when {
            isElimination -> "§c💀 ${player.name.string} §f已被淘汰！（词条：§b${word.displayText}§f）"
            isGain -> "§a💚 ${player.name.string} §f触发了「§b${word.displayText}§f」，回心！❤×§c${ps.hearts}"
            else -> "§e⚡ ${player.name.string} §f触发了「§b${word.displayText}§f」！剩余 §c${ps.hearts} ❤️"
        }
        server.playerManager.broadcast(Text.literal(msg), false)

        // 发送网络包
        sendTriggered(player, word, ps, isElimination, isGain)
    }

    // ==================== 清理 ====================

    private fun cleanup() {
        playerStates.clear()
        triggerDetector.clearAllState()
    }
}
