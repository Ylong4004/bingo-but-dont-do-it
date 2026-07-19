package me.jfenn.bingo.integrations.voice

import org.vosk.Model
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** 供 DDI 大厅菜单和诊断功能使用的公开状态值。 */
enum class VoiceKeywordBackendState {
    VOICECHAT_UNAVAILABLE,
    MODEL_MISSING,
    MODEL_AVAILABLE,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    LOADING,
    READY,
    UNSUPPORTED_PLATFORM,
    MODEL_INVALID,
    ERROR,
}

data class VoiceKeywordBackendStatus(
    val state: VoiceKeywordBackendState,
    val voiceChatAvailable: Boolean,
    /** 稳定且不含敏感信息的诊断码；绝不包含识别出的文本。 */
    val detail: String? = null,
    /** 模型下载、校验、解压或加载进度；就绪和错误时为空。 */
    val progress: VoiceKeywordModelProgress? = null,
) {
    val isReady: Boolean
        get() = state == VoiceKeywordBackendState.READY && voiceChatAvailable
}

/**
 * 由服务端线程发布的不可变词条分配快照。
 *
 * 目标 ID 特意采用可直接还原的 `voice:<词语>` 形式。它们是 DDI 内部字符串，
 * 并非 Minecraft 标识符，因此可以包含中文。每个异步结果都会携带这份完整快照。
 */
data class VoiceKeywordTarget(
    val gameId: UUID,
    val objectiveId: String,
    val revision: Long,
    val wordId: String,
    val subjectIds: Set<String>,
) {
    init {
        require(objectiveId.isNotBlank()) { "Voice objective ID cannot be blank" }
        require(revision >= 0L) { "Voice assignment revision cannot be negative" }
        require(wordId.isNotBlank()) { "Voice word ID cannot be blank" }
        require(subjectIds.isNotEmpty()) { "A voice target requires at least one subject" }
        require(subjectIds.all { it.startsWith(VOICE_SUBJECT_PREFIX) }) {
            "Voice subjects must use the voice: namespace"
        }
    }

    companion object {
        const val VOICE_SUBJECT_PREFIX = "voice:"
    }
}

data class VoiceKeywordDetection(
    val playerId: UUID,
    val target: VoiceKeywordTarget,
    val matchedSubjectId: String,
    val confidence: Double,
)

/**
 * 表示一个 DDI 作用域。[targets] 必须始终保存不可变 Map 快照。
 * [onDetection] 在 ASR 工作线程执行，绝不会在 Minecraft 服务端线程执行。
 */
class VoiceKeywordSession(
    val targets: AtomicReference<Map<UUID, VoiceKeywordTarget>> =
        AtomicReference(emptyMap()),
    val onDetection: (VoiceKeywordDetection) -> Unit,
)

/** 进程级后端边界；任何 Simple Voice Chat 类型都不会越过此边界。 */
internal interface VoiceKeywordAudioBackend : AutoCloseable {
    fun offer(playerId: UUID, opusData: ByteArray, target: VoiceKeywordTarget)
    fun disconnect(playerId: UUID)
    fun onSessionChanged()
}

/**
 * 这是供 DDI 使用的稳定集成入口，使其无需直接链接 Simple Voice Chat。
 * 语音插件会在 UDP 服务启动时安装音频后端。
 */
object VoiceKeywordBridge {
    private val log = LoggerFactory.getLogger("bingo")
    private val lifecycleLock = Any()
    private val gate = VoiceKeywordSessionGate()
    private val backend = AtomicReference<VoiceKeywordAudioBackend?>()
    private val connectedPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val modelManager = VoiceKeywordModelManager(defaultModelRoot())

    @JvmStatic
    fun status(): VoiceKeywordBackendStatus {
        val voiceAvailable = backend.get() != null
        val modelStatus = modelManager.status()
        val resolved = if (!voiceAvailable) {
            VoiceKeywordBackendState.VOICECHAT_UNAVAILABLE
        } else {
            modelStatus.state
        }
        return VoiceKeywordBackendStatus(
            state = resolved,
            voiceChatAvailable = voiceAvailable,
            detail = modelStatus.detail,
            progress = modelStatus.progress,
        )
    }

    /**
     * 返回全局或指定玩家的流水线计数。该快照不含音频、识别文本或关键词，
     * 可安全地由局内管理员命令显示。
     */
    @JvmStatic
    fun diagnostics(playerId: UUID? = null): VoiceKeywordDiagnosticsSnapshot =
        VoiceKeywordDiagnostics.snapshot(
            playerId = playerId,
            connectedPlayers = connectedPlayers.size,
            activeTargets = gate.targetCount(),
        )

    @JvmStatic
    fun resetDiagnostics() {
        VoiceKeywordDiagnostics.reset()
    }

    /** 由 DDI 服务端线程补上异步检测后的最终结算阶段。 */
    @JvmStatic
    fun recordSettlementResult(playerId: UUID, accepted: Boolean) {
        VoiceKeywordDiagnostics.record(
            playerId,
            if (accepted) VoiceKeywordDiagnosticStage.DDI_SETTLED
            else VoiceKeywordDiagnosticStage.DDI_REJECTED,
        )
    }

    /**
     * 原子替换已有作用域。关闭旧句柄绝不会误关恢复对局后创建的新会话。
     */
    @JvmStatic
    fun openSession(session: VoiceKeywordSession): AutoCloseable {
        val id: Long
        synchronized(lifecycleLock) {
            backend.get()?.onSessionChanged()
            id = gate.activate(session)
            if (backend.get() != null) modelManager.ensureLoadedAsync()
        }
        return AutoCloseable {
            synchronized(lifecycleLock) {
                if (gate.deactivate(id)) backend.get()?.onSessionChanged()
            }
        }
    }

    /** 管理员显式执行的操作；除此之外不会发起网络请求。 */
    @JvmStatic
    fun requestModelDownload(): CompletableFuture<VoiceKeywordBackendStatus> =
        modelManager.requestDownload().thenApply { status() }

    /** 加载已经安装的模型，不执行下载。 */
    @JvmStatic
    fun prepareInstalledModel(): CompletableFuture<VoiceKeywordBackendStatus> =
        modelManager.ensureLoadedAsync().thenApply { status() }

    internal fun installBackend(value: VoiceKeywordAudioBackend) {
        synchronized(lifecycleLock) {
            backend.getAndSet(value)?.close()
            VoiceKeywordDiagnostics.reset()
            value.onSessionChanged()
            if (gate.hasSession()) modelManager.ensureLoadedAsync()
        }
    }

    internal fun uninstallBackend(value: VoiceKeywordAudioBackend) {
        synchronized(lifecycleLock) {
            if (backend.compareAndSet(value, null)) {
                connectedPlayers.clear()
                value.close()
            }
        }
    }

    /** 由语音 UDP 数据包线程直接调用，必须保持 O(1) 复杂度。 */
    internal fun acceptMicrophonePacket(playerId: UUID, opusData: ByteArray) {
        val target = gate.currentTarget(playerId)
        if (target == null) {
            VoiceKeywordDiagnostics.record(
                playerId,
                VoiceKeywordDiagnosticStage.NO_ACTIVE_TARGET,
            )
            return
        }
        VoiceKeywordDiagnostics.record(playerId, VoiceKeywordDiagnosticStage.TARGETED_PACKET)
        // 这里只允许 volatile、并发计数器和队列的快速路径；
        // 该线程禁止访问磁盘、加载模型或执行网络操作。
        if (modelManager.loadedModel() == null) {
            VoiceKeywordDiagnostics.record(playerId, VoiceKeywordDiagnosticStage.MODEL_NOT_READY)
            return
        }
        val activeBackend = backend.get()
        if (activeBackend == null) {
            VoiceKeywordDiagnostics.record(playerId, VoiceKeywordDiagnosticStage.BACKEND_NOT_READY)
            return
        }
        activeBackend.offer(playerId, opusData, target)
    }

    internal fun recordMicrophonePacket(playerId: UUID) {
        VoiceKeywordDiagnostics.record(playerId, VoiceKeywordDiagnosticStage.MICROPHONE_PACKET)
    }

    internal fun recordPipelineError(playerId: UUID) {
        VoiceKeywordDiagnostics.record(playerId, VoiceKeywordDiagnosticStage.PIPELINE_ERROR)
    }

    internal fun onPlayerDisconnected(playerId: UUID) {
        connectedPlayers.remove(playerId)
        backend.get()?.disconnect(playerId)
        // 全局累计值仍会保留；移除明细可限制长期服务器的 UUID 映射大小。
        VoiceKeywordDiagnostics.removePlayer(playerId)
    }

    internal fun onPlayerConnected(playerId: UUID) {
        connectedPlayers += playerId
    }

    @JvmStatic
    fun isPlayerConnected(playerId: UUID): Boolean = playerId in connectedPlayers

    internal fun currentTarget(playerId: UUID): VoiceKeywordTarget? =
        gate.currentTarget(playerId)

    internal fun loadedModel(): Model? = modelManager.loadedModel()

    internal fun acceptDetection(detection: VoiceKeywordDetection): Boolean {
        return try {
            gate.deliver(detection).also { delivered ->
                VoiceKeywordDiagnostics.record(
                    detection.playerId,
                    if (delivered) VoiceKeywordDiagnosticStage.DETECTION_DELIVERED
                    else VoiceKeywordDiagnosticStage.DETECTION_REJECTED,
                )
            }
        } catch (_: Throwable) {
            // 日志中绝不能包含 ASR 输出或自定义关键词。
            VoiceKeywordDiagnostics.record(
                detection.playerId,
                VoiceKeywordDiagnosticStage.PIPELINE_ERROR,
            )
            log.error("[VoiceKeywords] Detection callback failed")
            false
        }
    }

    private fun defaultModelRoot(): Path = Paths.get(
        System.getProperty("user.dir"),
        "config",
        "yet-another-minecraft-bingo",
        "ddi",
        "asr",
    ).toAbsolutePath().normalize()
}

/** 纯词条分配校验门，在结果进入 DDI 前拒绝过期的 ASR 任务。 */
internal class VoiceKeywordSessionGate(
    private val cooldownNanos: Long = 2_000_000_000L,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private data class Active(
        val id: Long,
        val session: VoiceKeywordSession,
        val lastDetectionNanos: ConcurrentHashMap<CooldownKey, Long> = ConcurrentHashMap(),
    )

    private data class CooldownKey(
        val playerId: UUID,
        val target: VoiceKeywordTarget,
    )

    private val nextId = AtomicLong()
    private val active = AtomicReference<Active?>()

    fun activate(session: VoiceKeywordSession): Long {
        val id = nextId.incrementAndGet()
        active.set(Active(id, session))
        return id
    }

    fun deactivate(id: Long): Boolean {
        while (true) {
            val current = active.get() ?: return false
            if (current.id != id) return false
            if (active.compareAndSet(current, null)) return true
        }
    }

    fun hasSession(): Boolean = active.get() != null

    fun targetCount(): Int = active.get()?.session?.targets?.get()?.size ?: 0

    fun currentTarget(playerId: UUID): VoiceKeywordTarget? =
        active.get()?.session?.targets?.get()?.get(playerId)

    fun deliver(detection: VoiceKeywordDetection): Boolean {
        val current = active.get() ?: return false
        val target = current.session.targets.get()[detection.playerId] ?: return false
        if (target != detection.target) return false
        if (detection.matchedSubjectId !in target.subjectIds) return false

        val now = nanoTime()
        val cooldownKey = CooldownKey(detection.playerId, target)
        synchronized(current) {
            if (active.get() !== current) return false
            val last = current.lastDetectionNanos[cooldownKey]
            if (last != null && now - last < cooldownNanos) return false
            current.lastDetectionNanos[cooldownKey] = now
        }
        current.session.onDetection(detection)
        return true
    }
}
