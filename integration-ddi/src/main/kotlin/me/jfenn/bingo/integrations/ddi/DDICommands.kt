package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.DDIObjectiveMode
import me.jfenn.bingo.common.options.DDISpecialEventType
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.PermissionDefault
import me.jfenn.bingo.integrations.permissions.PermissionKey
import me.jfenn.bingo.integrations.voice.VoiceKeywordBridge
import me.jfenn.bingo.integrations.voice.VoiceKeywordDiagnosticStage
import me.jfenn.bingo.integrations.voice.VoiceKeywordDiagnosticsSnapshot
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Formatting
import java.util.Locale
import java.util.UUID

/** 仅供管理员使用的 DDI 诊断与客户端投影恢复命令。 */
class DDICommands(
    commandManager: ICommandManager,
    private val text: TextProvider,
) : BingoComponent() {

    private fun modeText(mode: DDIObjectiveMode): IText = text.string(
        when (mode) {
            DDIObjectiveMode.INDIVIDUAL -> StringKey.DdiOptionModeIndividual
            DDIObjectiveMode.TEAM_SHARED -> StringKey.DdiOptionModeTeam
        }
    )

    private fun sessionText(state: DDISessionState): IText = text.string(
        when (state) {
            DDISessionState.Inactive -> StringKey.DdiCommandStatusSessionInactive
            is DDISessionState.Active -> StringKey.DdiCommandStatusSessionActive
            is DDISessionState.Completed -> StringKey.DdiCommandStatusSessionCompleted
        }
    )

    private fun sessionGameId(state: DDISessionState) = when (state) {
        DDISessionState.Inactive -> null
        is DDISessionState.Active -> state.gameId
        is DDISessionState.Completed -> state.gameId
    }

    private fun IExecutionContext.showStatus(reveal: Boolean) {
        val options = scope.get<BingoOptions>()
        val controller = scope.get<DDIGameController>()
        val snapshot = scope.get<DDIObjectiveManager>().snapshot()

        sendMessage(
            text.string(
                if (reveal) StringKey.DdiCommandStatusHeaderReveal
                else StringKey.DdiCommandStatusHeader
            ).formatted(Formatting.GOLD, Formatting.BOLD)
        )
        sendMessage(
            text.string(
                StringKey.DdiCommandOptionsSummary,
                text.boolean(options.enableDDI),
                modeText(options.ddiObjectiveMode).formatted(Formatting.YELLOW),
                text.literal(options.ddiMaxHearts.toString()).formatted(Formatting.YELLOW),
                text.literal(options.ddiWordTimerSeconds.toString()).formatted(Formatting.YELLOW),
            ).formatted(Formatting.GRAY)
        )

        val sessionState = controller.sessionState
        val gameId = sessionGameId(sessionState)
            ?.let { text.literal(it.toString()) }
            ?: text.string(StringKey.DdiCommandStatusNone)
        sendMessage(
            text.string(
                StringKey.DdiCommandStatusSession,
                sessionText(sessionState),
                gameId,
            ).formatted(Formatting.GRAY)
        )

        val config = snapshot.config
        if (!snapshot.hasRound || config == null) {
            sendMessage(text.string(StringKey.DdiCommandStatusNoRound).formatted(Formatting.GRAY))
            return
        }

        sendMessage(
            text.string(
                StringKey.DdiCommandStatusRound,
                modeText(config.objectiveMode).formatted(Formatting.YELLOW),
                snapshot.participantCount,
                snapshot.inactiveParticipantCount,
                snapshot.objectives.size,
            ).formatted(Formatting.GRAY)
        )

        if (snapshot.objectives.isEmpty()) {
            sendMessage(text.string(StringKey.DdiCommandStatusNoObjectives).formatted(Formatting.GRAY))
            return
        }

        snapshot.objectives.forEach { objective ->
            val objectiveStatus = text.string(
                if (objective.isEliminated) StringKey.DdiCommandStatusEliminated
                else StringKey.DdiCommandStatusAlive
            )
            sendMessage(
                text.string(
                    StringKey.DdiCommandStatusObjective,
                    objective.objectiveName,
                    objective.teamName,
                    objective.hearts,
                    objective.maxHearts,
                    objective.timerSeconds,
                    objective.maxTimerSeconds,
                    objectiveStatus,
                )
            )

            if (reveal) {
                val wordText = objective.wordText
                val wordId = objective.wordId
                val ruleSummary = objective.ruleSummary
                if (wordText == null || wordId == null || ruleSummary == null) {
                    sendMessage(
                        text.string(StringKey.DdiCommandStatusWordNone)
                            .formatted(Formatting.DARK_GRAY)
                    )
                } else {
                    sendMessage(
                        text.string(
                            StringKey.DdiCommandStatusWord,
                            wordText,
                            wordId,
                            ruleSummary,
                        ).formatted(Formatting.DARK_GRAY)
                    )
                }
            }
        }
    }

    private fun IExecutionContext.syncAllPlayers() {
        val manager = scope.get<DDIObjectiveManager>()
        val targets = server.playerManager.playerList.toList()
        targets.forEach(manager::resyncTo)
        sendFeedback(text.string(StringKey.DdiCommandSyncAllSuccess, targets.size))
    }

    private fun IExecutionContext.showSpecialEventStatus() {
        val snapshot = scope.get<DDISpecialEventService>().snapshot()
        val active = snapshot.activeDisplayName?.let { name ->
            "$name（剩余 ${snapshot.activeRemainingSeconds} 秒）"
        } ?: "无"
        sendMessage(
            text.literal(
                "§6[DDI 特殊事件] §f运行=${snapshot.running}，间隔=${snapshot.intervalSeconds}s，" +
                    "下次=${snapshot.countdownSeconds}s，已选=${snapshot.enabledEvents.size}/" +
                    "${DDISpecialEventType.entries.size}，当前=$active"
            )
        )
    }

    private fun IExecutionContext.triggerSpecialEvent(id: String) {
        val type = DDISpecialEventType.fromId(id)
        if (type == null) {
            sendMessage(text.literal("§c未知特殊事件：$id"))
            return
        }
        val triggered = scope.get<DDISpecialEventService>().trigger(type)
        sendFeedback(
            text.literal(
                if (triggered) "§a已强制触发特殊事件：${type.id}"
                else "§c当前没有正在运行且已启用特殊事件的 DDI 对局。"
            )
        )
    }

    private fun IExecutionContext.showVoiceStatus() {
        val status = scope.get<DDIVoiceKeywordController>().status()
        sendMessage(
            text.literal(
                "§6[DDI 语音] §f状态=${status.state.name.lowercase()}，" +
                    "Simple Voice Chat=${status.voiceChatAvailable}，" +
                    "详情=${status.detail ?: "无"}"
            )
        )
    }

    private fun IExecutionContext.sendDebugResult(result: DDIDebugActionResult) {
        sendFeedback(
            text.literal(
                (if (result.success) "§a[DDI 调试] " else "§c[DDI 调试] ") + result.message
            )
        )
    }

    private fun diagnosticStageText(stage: VoiceKeywordDiagnosticStage?): String = when (stage) {
        VoiceKeywordDiagnosticStage.MICROPHONE_PACKET -> "收到麦克风包"
        VoiceKeywordDiagnosticStage.NO_ACTIVE_TARGET -> "玩家没有活动语音目标"
        VoiceKeywordDiagnosticStage.TARGETED_PACKET -> "麦克风包已命中目标"
        VoiceKeywordDiagnosticStage.MODEL_NOT_READY -> "模型尚未加载"
        VoiceKeywordDiagnosticStage.BACKEND_NOT_READY -> "语音后端不可用"
        VoiceKeywordDiagnosticStage.PACKET_QUEUED -> "音频已进入识别队列"
        VoiceKeywordDiagnosticStage.QUEUE_DROPPED -> "识别队列丢包"
        VoiceKeywordDiagnosticStage.RESOURCES_READY -> "解码器和识别器已创建"
        VoiceKeywordDiagnosticStage.AUDIO_DECODED -> "Opus 已解码"
        VoiceKeywordDiagnosticStage.SEGMENT_FINALIZED -> "语句已结段"
        VoiceKeywordDiagnosticStage.RESULT_EMPTY -> "Vosk 最终结果为空"
        VoiceKeywordDiagnosticStage.RESULT_INVALID -> "Vosk 返回格式无效"
        VoiceKeywordDiagnosticStage.RESULT_TEXT_MISMATCH -> "最终文本不匹配目标"
        VoiceKeywordDiagnosticStage.RESULT_LOW_CONFIDENCE -> "匹配文本但置信度不足"
        VoiceKeywordDiagnosticStage.RESULT_MATCHED -> "最终结果已匹配"
        VoiceKeywordDiagnosticStage.RESULT_MATCHED_TEXT_FALLBACK -> "最终文本回退已匹配"
        VoiceKeywordDiagnosticStage.DETECTION_DELIVERED -> "检测已通过桥接校验"
        VoiceKeywordDiagnosticStage.DETECTION_REJECTED -> "检测被桥接校验拒绝"
        VoiceKeywordDiagnosticStage.DDI_SETTLED -> "DDI 已完成结算"
        VoiceKeywordDiagnosticStage.DDI_REJECTED -> "DDI 拒绝结算"
        VoiceKeywordDiagnosticStage.PIPELINE_ERROR -> "音频/识别流水线异常"
        null -> "尚无活动"
    }

    private fun voiceDiagnosticHint(
        snapshot: VoiceKeywordDiagnosticsSnapshot,
        player: DDIVoicePlayerDebugSnapshot?,
    ): String {
        if (snapshot.nativeStringEncoding != "未初始化" &&
            !snapshot.nativeStringEncoding.equals("UTF-8", ignoreCase = true)
        ) {
            return "Vosk/JNA 字符串编码不是 UTF-8，中文语法会失效；请重新启动服务端。"
        }
        if (player != null) {
            if (!player.hasRound) return "当前没有活动 DDI 回合。"
            if (!player.isParticipant) return "该玩家不在本局固定 DDI 名单中。"
            if (!player.isActiveParticipant) return "该玩家或其目标已经失效/淘汰。"
            if (!player.isVoiceWord) return "当前不是语音词条；可用 word set 强制发放后再测。"
            if (!player.hasConsent) return "玩家尚未同意本地语音识别。"
            if (!player.isVoiceConnected) return "Simple Voice Chat 尚未报告该玩家已连接。"
            if (!player.isTargetPublished) return "词条存在但识别目标未发布，请检查后端状态和词条资格。"
        }
        if (snapshot.microphonePackets == 0L) {
            return "未收到麦克风包：检查客户端语音连接、静音/PTT 和 Simple Voice Chat。"
        }
        if (snapshot.targetedPackets == 0L) {
            return "收到语音包但没有命中目标：检查当前词条和目标发布状态。"
        }
        if (snapshot.queuedPackets == 0L) {
            return when {
                snapshot.modelNotReadyPackets > 0L -> "音频被模型加载状态拦截。"
                snapshot.backendNotReadyPackets > 0L -> "音频被 Simple Voice Chat 后端状态拦截。"
                else -> "目标已命中但音频没有入队，请检查队列/目标版本。"
            }
        }
        if (snapshot.decodedFrames == 0L) return "音频已入队但未解码，检查 Opus 解码器和流水线异常。"
        if (snapshot.decodedPeakAmplitude < 128) {
            return "解码后的 PCM 几乎无声；检查麦克风输入设备、增益和降噪设置。"
        }
        if (snapshot.finalizedSegments == 0L) {
            return "音频已解码但尚未结段；说完后松开 PTT/停顿约 0.7 秒再查询。"
        }
        when (snapshot.lastStage) {
            VoiceKeywordDiagnosticStage.DDI_SETTLED ->
                return "完整识别与 DDI 结算链路工作正常。"
            VoiceKeywordDiagnosticStage.DDI_REJECTED ->
                return "桥接已交付，但 DDI 服务端规则校验拒绝了本次结算。"
            VoiceKeywordDiagnosticStage.DETECTION_REJECTED ->
                return "Vosk 已匹配，但结果被目标版本、冷却或会话门拒绝。"
            VoiceKeywordDiagnosticStage.DETECTION_DELIVERED ->
                return "桥接已交付，Minecraft 主线程正在等待或即将执行 DDI 结算。"
            VoiceKeywordDiagnosticStage.RESULT_LOW_CONFIDENCE ->
                return "Vosk 听到了目标词，但置信度仍低于安全阈值。"
            VoiceKeywordDiagnosticStage.RESULT_TEXT_MISMATCH ->
                return "Vosk 有最终结果，但规范化后不是当前关键词。"
            VoiceKeywordDiagnosticStage.RESULT_INVALID ->
                return "Vosk 返回了无法解析的最终结果。"
            VoiceKeywordDiagnosticStage.RESULT_EMPTY ->
                return "音频已识别，但最终结果为空或只包含未知词。"
            else -> Unit
        }
        if (snapshot.matchedResults > snapshot.deliveredDetections) {
            return "Vosk 已匹配，但结果尚未通过目标版本、冷却或会话门。"
        }
        if (snapshot.deliveredDetections > snapshot.settledDetections) {
            return "桥接已交付，但 DDI 尚未回报结算结果。"
        }
        if (snapshot.pipelineErrors > 0L || snapshot.droppedPackets > 0L) {
            return "流水线发生异常或丢包；先重置计数并单人短句重试。"
        }
        return "数据还不足；请说一次完整关键词、停顿，然后再次查询。"
    }

    private fun IExecutionContext.showVoiceDebug(playerId: UUID?, playerName: String?) {
        val snapshot = VoiceKeywordBridge.diagnostics(playerId)
        val playerState = playerId?.let { scope.get<DDIObjectiveManager>().debugVoiceState(it) }
        val scopeName = playerName ?: "全局"
        val elapsed = snapshot.lastActivityEpochMillis
            ?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }
            ?.let { "${it}ms 前" }
            ?: "无"
        val windowSeconds =
            ((System.currentTimeMillis() - snapshot.sinceEpochMillis).coerceAtLeast(0L) / 1000L)
        val confidence = snapshot.lastAverageConfidence?.let { average ->
            val minimum = snapshot.lastMinimumWordConfidence
                ?.let { String.format(Locale.ROOT, "%.3f", it) }
                ?: "无"
            "${String.format(Locale.ROOT, "%.3f", average)}/$minimum"
        } ?: "无"
        val meanAmplitude = snapshot.decodedMeanAbsoluteAmplitude
            ?.let { String.format(Locale.ROOT, "%.1f", it) }
            ?: "无"

        sendMessage(text.literal("§6[DDI 语音诊断·$scopeName] §f统计窗口=${windowSeconds}s"))
        sendMessage(
            text.literal(
                "§7连接=${snapshot.connectedPlayers}，活动目标=${snapshot.activeTargets}，" +
                    "麦克风包=${snapshot.microphonePackets}，命中目标包=${snapshot.targetedPackets}，" +
                    "无目标包=${snapshot.noTargetPackets}"
            )
        )
        sendMessage(
            text.literal(
                "§7入队=${snapshot.queuedPackets}，丢包=${snapshot.droppedPackets}，" +
                    "解码帧=${snapshot.decodedFrames}，PCM样本=${snapshot.decodedSamples}，" +
                    "峰值=${snapshot.decodedPeakAmplitude}，均幅=$meanAmplitude，" +
                    "结段=${snapshot.finalizedSegments}，Vosk编码=${snapshot.nativeStringEncoding}"
            )
        )
        sendMessage(
            text.literal(
                "§7空结果=${snapshot.emptyResults}，无效=${snapshot.invalidResults}，" +
                    "文本不符=${snapshot.textMismatches}，低置信=${snapshot.lowConfidenceResults}，" +
                    "最近置信(均/低)=$confidence，" +
                    "匹配=${snapshot.matchedResults}，桥接=${snapshot.deliveredDetections}/" +
                    "${snapshot.rejectedDetections}，DDI=${snapshot.settledDetections}/" +
                    "${snapshot.rejectedSettlements}，异常=${snapshot.pipelineErrors}"
            )
        )
        if (playerState != null) {
            sendMessage(
                text.literal(
                    "§7玩家状态：参赛=${playerState.isParticipant}，有效=${playerState.isActiveParticipant}，" +
                        "授权=${playerState.hasConsent}，语音连接=${playerState.isVoiceConnected}，" +
                        "语音词条=${playerState.isVoiceWord}，目标已发布=${playerState.isTargetPublished}"
                )
            )
            sendMessage(
                text.literal(
                    "§7当前目标=${playerState.objectiveName ?: "无"}，词条=" +
                        "${playerState.wordId ?: "无"}（${playerState.wordText ?: "无"}），" +
                        "分配版本=${playerState.assignmentRevision ?: -1}"
                )
            )
        }
        sendMessage(
            text.literal(
                "§e最后阶段：${diagnosticStageText(snapshot.lastStage)}（$elapsed）"
            )
        )
        sendMessage(text.literal("§b判断：${voiceDiagnosticHint(snapshot, playerState)}"))
        sendMessage(
            text.literal(
                "§8建议测试前执行 /bingo ddi voice debug reset，单人说一次后松开按键再查询。"
            )
        )
    }

    private fun IExecutionContext.showDebugWordInfo(id: String) {
        val word = scope.get<DDIObjectiveManager>().debugWord(id)
        if (word == null) {
            sendMessage(text.literal("§c未知词条 ID：$id"))
            return
        }
        sendMessage(
            text.literal(
                "§6[DDI 词条] §f${word.id}：${word.displayText}，分类=${word.category}，" +
                    "触发=${word.triggerType}，规则=${word.rule.diagnosticName()}"
            )
        )
    }

    init {
        commandManager.register("bingo") {
            literal("ddi") {
                requires { hasPermission(DDI_ADMIN_PERMISSION) }

                literal("status") {
                    executes { showStatus(reveal = false) }
                    literal("reveal") {
                        executes { showStatus(reveal = true) }
                    }
                }

                literal("sync") {
                    executes { syncAllPlayers() }
                    player("player") { playerArg ->
                        executes {
                            val target = getArgument(playerArg)
                            scope.get<DDIObjectiveManager>().resyncTo(target.player)
                            sendFeedback(
                                text.string(StringKey.DdiCommandSyncPlayerSuccess, target.playerName)
                            )
                        }
                    }
                }

                literal("event") {
                    literal("status") {
                        executes { showSpecialEventStatus() }
                    }
                    literal("trigger") {
                        string(
                            "event",
                            suggestions = { DDISpecialEventType.entries.map(DDISpecialEventType::id) },
                        ) { eventArg ->
                            executes { triggerSpecialEvent(getArgument(eventArg)) }
                        }
                    }
                    literal("stop") {
                        executes {
                            val stopped = scope.get<DDISpecialEventService>().stopActive()
                            sendFeedback(
                                text.literal(
                                    if (stopped) "§a已清理当前特殊事件，自动调度会从完整间隔重新计时。"
                                    else "§e当前没有持续中的特殊事件。"
                                )
                            )
                        }
                    }
                }

                literal("word") {
                    literal("next") {
                        literal("status") {
                            executes {
                                val id = scope.get<DDIObjectiveManager>()
                                    .debugNextRoundWordId()
                                sendMessage(
                                    text.literal(
                                        "§6[DDI 调试] §f下一局首词条：${id ?: "未指定"}"
                                    )
                                )
                            }
                        }
                        literal("clear") {
                            executes {
                                sendDebugResult(
                                    scope.get<DDIObjectiveManager>().clearDebugNextRoundWord()
                                )
                            }
                        }
                        string(
                            "word_id",
                            suggestions = {
                                scope.get<DDIObjectiveManager>().debugWordIds()
                            },
                        ) { wordArg ->
                            executes {
                                sendDebugResult(
                                    scope.get<DDIObjectiveManager>()
                                        .configureDebugNextRoundWord(getArgument(wordArg))
                                )
                            }
                        }
                    }

                    literal("set") {
                        player("player") { playerArg ->
                            string(
                                "word_id",
                                suggestions = {
                                    scope.get<DDIObjectiveManager>().debugWordIds()
                                },
                            ) { wordArg ->
                                executes {
                                    val target = getArgument(playerArg)
                                    sendDebugResult(
                                        scope.get<DDIObjectiveManager>().debugForceWord(
                                            target.player.uuid,
                                            getArgument(wordArg),
                                        )
                                    )
                                }
                            }
                        }
                    }

                    literal("reroll") {
                        literal("all") {
                            executes {
                                sendDebugResult(
                                    scope.get<DDIObjectiveManager>().debugRerollAllWords()
                                )
                            }
                        }
                        player("player") { playerArg ->
                            executes {
                                val target = getArgument(playerArg)
                                sendDebugResult(
                                    scope.get<DDIObjectiveManager>()
                                        .debugRerollWord(target.player.uuid)
                                )
                            }
                        }
                    }

                    literal("info") {
                        string(
                            "word_id",
                            suggestions = {
                                scope.get<DDIObjectiveManager>().debugWordIds()
                            },
                        ) { wordArg ->
                            executes { showDebugWordInfo(getArgument(wordArg)) }
                        }
                    }
                }

                literal("voice") {
                    literal("status") {
                        executes { showVoiceStatus() }
                    }
                    literal("debug") {
                        executes { showVoiceDebug(playerId = null, playerName = null) }
                        literal("reset") {
                            executes {
                                VoiceKeywordBridge.resetDiagnostics()
                                sendFeedback(
                                    text.literal(
                                        "§a[DDI 语音诊断] 已清空全局与各玩家计数；" +
                                            "现在请单人说一次完整关键词并停顿。"
                                    )
                                )
                            }
                        }
                        player("player") { playerArg ->
                            executes {
                                val target = getArgument(playerArg)
                                showVoiceDebug(target.player.uuid, target.playerName)
                            }
                        }
                    }
                    literal("simulate") {
                        player("player") { playerArg ->
                            executes {
                                val target = getArgument(playerArg)
                                sendDebugResult(
                                    scope.get<DDIObjectiveManager>()
                                        .debugSimulateVoiceDetection(target.player.uuid)
                                )
                            }
                        }
                    }
                    literal("model") {
                        literal("download") {
                            executes {
                                scope.get<DDIVoiceKeywordController>().requestModelDownload()
                                sendFeedback(
                                    text.literal(
                                        "§a已异步检查/下载校验后的本地中文语音模型；" +
                                            "使用 /bingo ddi voice status 查看进度。"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val DDI_ADMIN_PERMISSION = PermissionKey(
            "$MOD_ID_BINGO.command.ddi",
            PermissionDefault.OPERATORS,
        )
    }
}
