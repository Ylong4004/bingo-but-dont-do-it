package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.integrations.voice.VoiceKeywordBackendState
import me.jfenn.bingo.integrations.voice.VoiceKeywordBackendStatus
import me.jfenn.bingo.integrations.voice.VoiceKeywordBridge
import me.jfenn.bingo.integrations.voice.VoiceKeywordSession
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import org.slf4j.Logger

/**
 * 负责管理游戏作用域对进程级语音后端的订阅。
 *
 * 麦克风解码和 ASR 在数量受限的工作线程上执行。只有不可变的词条分配令牌会跨越该边界，
 * 且每个结果都会先切回 Minecraft 服务端线程，再由目标管理器重新校验。
 */
class DDIVoiceKeywordController(
    private val options: BingoOptions,
    private val server: MinecraftServer,
    private val manager: DDIObjectiveManager,
    private val log: Logger,
) {
    private var session: VoiceKeywordSession? = null
    private var sessionHandle: AutoCloseable? = null
    private var generation = 0L
    private var lastKeywords: List<String> = emptyList()
    private var lastStatus: VoiceKeywordBackendState? = null
    private var automaticModelPreparationRequested = false

    val isRunning: Boolean get() = session != null

    fun start() {
        stop()
        if (!options.ddiVoiceKeywordsEnabled || !manager.hasRound) return

        generation++
        val myGeneration = generation
        lastKeywords = options.ddiVoiceCustomKeywords.toList()
        manager.updateCustomVoiceKeywords(lastKeywords)

        lateinit var nextSession: VoiceKeywordSession
        nextSession = VoiceKeywordSession { detection ->
            // VoiceKeywordSession 回调始终来自 ASR 工作线程。
            // 切勿在该线程中访问世界、玩家或目标状态。
            server.execute {
                if (generation == myGeneration && session === nextSession) {
                    val settled = manager.onVoiceKeywordDetection(detection)
                    VoiceKeywordBridge.recordSettlementResult(detection.playerId, settled)
                    refreshTargets()
                }
            }
        }
        session = nextSession
        sessionHandle = VoiceKeywordBridge.openSession(nextSession)
        val initialStatus = VoiceKeywordBridge.status()
        lastStatus = initialStatus.state
        refreshTargets()
        sendPrivacyNotice()

        if (!initialStatus.voiceChatAvailable) {
            announceStatusIfNeeded(force = true)
            return
        }
        requestAutomaticModelPreparation(myGeneration, nextSession)
    }

    /** DDI 会话处于活动状态时，每个服务端游戏刻调用一次。 */
    fun tick() {
        if (session == null) return
        if (!options.ddiVoiceKeywordsEnabled || !manager.hasRound) {
            stop()
            return
        }

        val currentKeywords = options.ddiVoiceCustomKeywords.toList()
        if (currentKeywords != lastKeywords) {
            lastKeywords = currentKeywords
            manager.updateCustomVoiceKeywords(currentKeywords)
        }
        manager.refreshVoiceAvailability()
        if (session == null || !manager.hasRound) return
        requestAutomaticModelPreparation(generation, session ?: return)
        announceStatusIfNeeded(force = false)
        refreshTargets()
    }

    fun stop() {
        generation++
        session?.targets?.set(emptyMap())
        session = null
        runCatching { sessionHandle?.close() }
            .onFailure { log.warn("[DDI Voice] Failed to close the recognition session") }
        sessionHandle = null
        lastKeywords = emptyList()
        lastStatus = null
        automaticModelPreparationRequested = false
    }

    fun status(): VoiceKeywordBackendStatus = VoiceKeywordBridge.status()

    fun requestModelDownload() = VoiceKeywordBridge.requestModelDownload()

    private fun refreshTargets() {
        val activeSession = session ?: return
        val targets = manager.voiceTargets()
        if (activeSession.targets.get() != targets) activeSession.targets.set(targets)
    }

    /**
     * 管理员启用大厅选项即表示同意下载经过校验的本地模型。
     * 每刻重试此检查，也能处理恢复已保存的 PLAYING 对局后语音服务才变为可用的情况。
     */
    private fun requestAutomaticModelPreparation(
        requestedGeneration: Long,
        requestedSession: VoiceKeywordSession,
    ) {
        if (automaticModelPreparationRequested || !VoiceKeywordBridge.status().voiceChatAvailable) {
            return
        }
        automaticModelPreparationRequested = true
        VoiceKeywordBridge.requestModelDownload().whenComplete { _, failure ->
            server.execute {
                if (generation != requestedGeneration || session !== requestedSession) return@execute
                if (failure != null) {
                    log.warn(
                        "[DDI Voice] Failed to prepare the local recognition model: {}",
                        failure.javaClass.simpleName,
                    )
                }
                announceStatusIfNeeded(force = true)
                refreshTargets()
            }
        }
    }

    private fun sendPrivacyNotice() {
        val message = Text.literal(
            "§e[不要做·语音] §f语音关键词已开启：仅在本机离线识别，不上传、保存音频或记录识别文本。" +
                "每位玩家需自行输入 §b/bingoprefs ddi_voice_consent true §f同意后，语音词条才会对其目标生效。"
        )
        manager.activePlayers().forEach { it.sendMessage(message, false) }
    }

    private fun announceStatusIfNeeded(force: Boolean) {
        val status = VoiceKeywordBridge.status()
        if (!force && status.state == lastStatus) return
        lastStatus = status.state

        val message = when (status.state) {
            VoiceKeywordBackendState.READY ->
                "§a[不要做·语音] 本地语音识别已就绪，符合授权条件的语音词条现在可以抽取。"
            VoiceKeywordBackendState.VOICECHAT_UNAVAILABLE ->
                "§e[不要做·语音] 未检测到可用的 Simple Voice Chat 服务，本局不会抽取语音词条。"
            VoiceKeywordBackendState.UNSUPPORTED_PLATFORM ->
                "§c[不要做·语音] 当前系统或 CPU 架构不受本地识别器支持，本局不会抽取语音词条。"
            VoiceKeywordBackendState.MODEL_INVALID,
            VoiceKeywordBackendState.ERROR ->
                "§c[不要做·语音] 本地识别模型准备失败，本局不会抽取语音词条；管理员可用 /bingo ddi voice status 检查。"
            else -> null
        } ?: return
        manager.activePlayers().forEach { it.sendMessage(Text.literal(message), false) }
    }
}
