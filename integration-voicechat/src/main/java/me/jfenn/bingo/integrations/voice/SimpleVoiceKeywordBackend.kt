package me.jfenn.bingo.integrations.voice

import de.maxhenkel.voicechat.api.VoicechatServerApi
import de.maxhenkel.voicechat.api.opus.OpusDecoder
import org.slf4j.LoggerFactory
import org.vosk.Model
import org.vosk.Recognizer
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * 按玩家隔离、容量受限且串行执行的 Opus/ASR 流水线。麦克风事件只复制并提交
 * 数据包；所有原生调用和高 CPU 开销操作都在这里执行。
 */
internal class SimpleVoiceKeywordBackend(
    private val api: VoicechatServerApi,
    private val modelProvider: () -> Model?,
    private val currentTarget: (UUID) -> VoiceKeywordTarget?,
    private val detectionSink: (VoiceKeywordDetection) -> Boolean,
) : VoiceKeywordAudioBackend {
    companion object {
        private const val MAX_QUEUED_PACKETS = 400
        private const val GAP_NANOS = 700_000_000L
        private const val MAX_SEGMENT_SAMPLES_16K = 8 * 16_000
        private const val IDLE_ACTOR_NANOS = 30_000_000_000L
    }

    private val log = LoggerFactory.getLogger("bingo")
    private val actors = ConcurrentHashMap<UUID, PlayerActor>()
    private val closed = AtomicBoolean(false)
    private val runtimeLock = Any()

    @Volatile
    private var workers: ExecutorService? = null

    @Volatile
    private var scheduler: ScheduledExecutorService? = null

    override fun offer(playerId: UUID, opusData: ByteArray, target: VoiceKeywordTarget) {
        if (closed.get() || currentTarget(playerId) != target) {
            VoiceKeywordDiagnostics.record(playerId, VoiceKeywordDiagnosticStage.QUEUE_DROPPED)
            return
        }
        if (ensureRuntime() == null) {
            VoiceKeywordDiagnostics.record(playerId, VoiceKeywordDiagnosticStage.PIPELINE_ERROR)
            return
        }
        actors.computeIfAbsent(playerId) { PlayerActor(playerId) }
            .offer(opusData, target)
    }

    override fun disconnect(playerId: UUID) {
        actors.remove(playerId)?.close()
    }

    override fun onSessionChanged() {
        // 该 DDI 作用域在 Minecraft 服务端线程开启或关闭。这里只让队列中的执行器
        // 失效；关闭原生工作线程时绝不能让服务端线程等待。
        clearActors()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        shutdownRuntime()
    }

    private fun ensureRuntime(): ExecutorService? {
        if (closed.get()) return null
        workers?.let { return it }
        synchronized(runtimeLock) {
            if (closed.get()) return null
            workers?.let { return it }
            val nextWorkers = Executors.newFixedThreadPool(workerCount()) { runnable ->
                Thread(runnable, "bingo-voice-asr").apply { isDaemon = true }
            }
            val nextScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "bingo-voice-boundaries").apply { isDaemon = true }
            }
            workers = nextWorkers
            scheduler = nextScheduler
            nextScheduler.scheduleAtFixedRate(::sweepActors, 100L, 100L, TimeUnit.MILLISECONDS)
            return nextWorkers
        }
    }

    private fun shutdownRuntime() {
        val currentWorkers: ExecutorService?
        synchronized(runtimeLock) {
            currentWorkers = workers
            val currentScheduler = scheduler
            if (currentWorkers == null && currentScheduler == null) return
            currentScheduler?.shutdownNow()
            clearActors()
            currentWorkers?.shutdown()
            workers = null
            scheduler = null
        }
        if (currentWorkers != null) {
            Thread({
                try {
                    if (!currentWorkers.awaitTermination(5L, TimeUnit.SECONDS)) {
                        currentWorkers.shutdownNow()
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    currentWorkers.shutdownNow()
                }
            }, "bingo-voice-asr-shutdown").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun clearActors() {
        val previous = actors.values.toList()
        actors.clear()
        previous.forEach(PlayerActor::close)
    }

    private fun sweepActors() {
        if (closed.get()) return
        val now = System.nanoTime()
        actors.entries.forEach { (playerId, actor) ->
            val target = currentTarget(playerId)
            if (target == null || actor.lastTarget?.let { it != target } == true) {
                if (actors.remove(playerId, actor)) actor.close()
                return@forEach
            }
            actor.offerGapBoundary(now)
            if (actor.isIdle(now) && actors.remove(playerId, actor)) actor.close()
        }
    }

    private fun workerCount(): Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    private sealed interface QueueItem {
        data class Audio(val data: ByteArray, val target: VoiceKeywordTarget) : QueueItem
        data class Boundary(val target: VoiceKeywordTarget) : QueueItem
        data object Abort : QueueItem
        data object Stop : QueueItem
    }

    private inner class PlayerActor(private val playerId: UUID) {
        private val queue = ArrayBlockingQueue<QueueItem>(MAX_QUEUED_PACKETS + 2)
        private val draining = AtomicBoolean(false)
        private val actorClosed = AtomicBoolean(false)
        private val enqueueLock = Any()

        @Volatile
        var lastTarget: VoiceKeywordTarget? = null
            private set

        @Volatile
        private var lastPacketNanos: Long = System.nanoTime()

        @Volatile
        private var utteranceOpen = false
        private var droppingUntilBoundary = false

        private var resourceTarget: VoiceKeywordTarget? = null
        private var grammar: VoiceKeywordGrammar? = null
        private var decoder: OpusDecoder? = null
        private var recognizer: Recognizer? = null
        private var resampler: Pcm48kTo16kResampler? = null
        private var segmentSamples = 0
        private var matchedCurrentTarget = false

        fun offer(opusData: ByteArray, target: VoiceKeywordTarget) {
            if (actorClosed.get()) return
            synchronized(enqueueLock) {
                if (actorClosed.get()) return
                lastTarget = target
                lastPacketNanos = System.nanoTime()
                if (opusData.isEmpty()) {
                    utteranceOpen = false
                    if (droppingUntilBoundary) {
                        droppingUntilBoundary = false
                        queue.clear()
                        queue.offer(QueueItem.Abort)
                    } else if (!queue.offer(QueueItem.Boundary(target))) {
                        abortQueuedUtterance()
                    }
                } else {
                    if (droppingUntilBoundary) {
                        VoiceKeywordDiagnostics.record(
                            playerId,
                            VoiceKeywordDiagnosticStage.QUEUE_DROPPED,
                        )
                        return
                    }
                    utteranceOpen = true
                    // 事件分发后可能复用数据包，因此必须自行持有字节副本。
                    if (!queue.offer(QueueItem.Audio(opusData.copyOf(), target))) {
                        VoiceKeywordDiagnostics.record(
                            playerId,
                            VoiceKeywordDiagnosticStage.QUEUE_DROPPED,
                        )
                        droppingUntilBoundary = true
                        utteranceOpen = false
                        abortQueuedUtterance()
                    } else {
                        VoiceKeywordDiagnostics.record(
                            playerId,
                            VoiceKeywordDiagnosticStage.PACKET_QUEUED,
                        )
                    }
                }
            }
            scheduleDrain()
        }

        fun offerGapBoundary(now: Long) {
            val target: VoiceKeywordTarget
            synchronized(enqueueLock) {
                if (actorClosed.get() || now - lastPacketNanos < GAP_NANOS) return
                target = lastTarget ?: return
                if (droppingUntilBoundary) {
                    droppingUntilBoundary = false
                    utteranceOpen = false
                    queue.clear()
                    queue.offer(QueueItem.Abort)
                } else {
                    if (!utteranceOpen) return
                    utteranceOpen = false
                    if (!queue.offer(QueueItem.Boundary(target))) {
                        abortQueuedUtterance()
                    }
                }
            }
            scheduleDrain()
        }

        fun isIdle(now: Long): Boolean =
            !utteranceOpen && queue.isEmpty() && now - lastPacketNanos >= IDLE_ACTOR_NANOS

        fun close() {
            if (!actorClosed.compareAndSet(false, true)) return
            synchronized(enqueueLock) {
                queue.clear()
                queue.offer(QueueItem.Stop)
                utteranceOpen = false
                droppingUntilBoundary = false
            }
            scheduleDrain()
        }

        private fun abortQueuedUtterance() {
            queue.clear()
            queue.offer(QueueItem.Abort)
        }

        private fun scheduleDrain() {
            if (!draining.compareAndSet(false, true)) return
            try {
                workers?.execute(::drain) ?: draining.set(false)
            } catch (_: RejectedExecutionException) {
                draining.set(false)
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.PIPELINE_ERROR,
                )
            }
        }

        private fun drain() {
            try {
                while (true) {
                    when (val item = queue.poll() ?: break) {
                        is QueueItem.Audio -> processAudio(item)
                        is QueueItem.Boundary -> processBoundary(item)
                        QueueItem.Abort -> closeResources()
                        QueueItem.Stop -> {
                            closeResources()
                            queue.clear()
                            return
                        }
                    }
                }
            } catch (_: Throwable) {
                // 解码器错误只影响当前语句。原生错误可能包含当前语法，不能写入日志。
                closeResources()
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.PIPELINE_ERROR,
                )
                log.warn("[VoiceKeywords] Discarded an invalid audio segment")
            } finally {
                draining.set(false)
                if (queue.isNotEmpty()) scheduleDrain()
            }
        }

        private fun processAudio(item: QueueItem.Audio) {
            if (currentTarget(playerId) != item.target) return
            if (!ensureResources(item.target)) return
            val decoded = decoder?.decode(item.data) ?: return
            val pcm16k = resampler?.process(decoded) ?: return
            if (pcm16k.isEmpty()) return
            var peakAmplitude = 0
            var absoluteAmplitudeSum = 0L
            pcm16k.forEach { sample ->
                val amplitude = abs(sample.toInt())
                peakAmplitude = maxOf(peakAmplitude, amplitude)
                absoluteAmplitudeSum += amplitude
            }
            VoiceKeywordDiagnostics.record(
                playerId,
                VoiceKeywordDiagnosticStage.AUDIO_DECODED,
                samples = pcm16k.size,
                peakAmplitude = peakAmplitude,
                absoluteAmplitudeSum = absoluteAmplitudeSum,
            )
            segmentSamples += pcm16k.size
            val endpoint = recognizer?.acceptWaveForm(pcm16k, pcm16k.size) == true
            if (endpoint) {
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.SEGMENT_FINALIZED,
                )
                consumeFinalResult(recognizer?.result)
                segmentSamples = 0
            } else if (segmentSamples >= MAX_SEGMENT_SAMPLES_16K) {
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.SEGMENT_FINALIZED,
                )
                consumeFinalResult(recognizer?.finalResult)
                recognizer?.reset()
                segmentSamples = 0
            }
        }

        private fun processBoundary(item: QueueItem.Boundary) {
            if (resourceTarget == item.target && segmentSamples > 0) {
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.SEGMENT_FINALIZED,
                )
                consumeFinalResult(recognizer?.finalResult)
            }
            closeResources()
        }

        private fun ensureResources(target: VoiceKeywordTarget): Boolean {
            if (resourceTarget == target && decoder != null && recognizer != null) return true
            closeResources()
            val model = modelProvider() ?: run {
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.MODEL_NOT_READY,
                )
                return false
            }
            val nextGrammar = VoiceKeywordGrammar.fromSubjects(target.subjectIds) ?: run {
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.PIPELINE_ERROR,
                )
                return false
            }
            val nextDecoder = try {
                api.createDecoder()
            } catch (_: Throwable) {
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.PIPELINE_ERROR,
                )
                return false
            }
            val nextRecognizer = try {
                // Vosk 的 grammar 和 JSON 结果均要求 UTF-8；Windows 中文系统上的
                // JNA 默认编码会让所有中文目标退化成 `[unk]`。
                VoskNativeEncoding.initialize()
                Recognizer(model, 16_000f, nextGrammar.grammarJson).apply {
                    setWords(true)
                    setMaxAlternatives(0)
                }
            } catch (_: Throwable) {
                nextDecoder.close()
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.PIPELINE_ERROR,
                )
                return false
            }
            resourceTarget = target
            grammar = nextGrammar
            decoder = nextDecoder
            recognizer = nextRecognizer
            resampler = Pcm48kTo16kResampler()
            segmentSamples = 0
            matchedCurrentTarget = false
            VoiceKeywordDiagnostics.record(
                playerId,
                VoiceKeywordDiagnosticStage.RESOURCES_READY,
            )
            return true
        }

        private fun consumeFinalResult(resultJson: String?) {
            if (matchedCurrentTarget) return
            if (resultJson == null) {
                VoiceKeywordDiagnostics.record(
                    playerId,
                    VoiceKeywordDiagnosticStage.RESULT_EMPTY,
                )
                return
            }
            val activeGrammar = grammar ?: return
            val target = resourceTarget ?: return
            val evaluation = VoiceKeywordResultMatcher.evaluateFinalResult(
                resultJson,
                activeGrammar,
            )
            val match = when (evaluation) {
                VoiceKeywordResultEvaluation.InvalidJson -> {
                    VoiceKeywordDiagnostics.record(
                        playerId,
                        VoiceKeywordDiagnosticStage.RESULT_INVALID,
                    )
                    return
                }
                VoiceKeywordResultEvaluation.EmptyResult -> {
                    VoiceKeywordDiagnostics.record(
                        playerId,
                        VoiceKeywordDiagnosticStage.RESULT_EMPTY,
                    )
                    return
                }
                VoiceKeywordResultEvaluation.TextMismatch -> {
                    VoiceKeywordDiagnostics.record(
                        playerId,
                        VoiceKeywordDiagnosticStage.RESULT_TEXT_MISMATCH,
                    )
                    return
                }
                is VoiceKeywordResultEvaluation.LowConfidence -> {
                    VoiceKeywordDiagnostics.record(
                        playerId,
                        VoiceKeywordDiagnosticStage.RESULT_LOW_CONFIDENCE,
                        averageConfidence = evaluation.average,
                        minimumWordConfidence = evaluation.minimum,
                    )
                    return
                }
                is VoiceKeywordResultEvaluation.Matched -> {
                    VoiceKeywordDiagnostics.record(
                        playerId,
                        if (evaluation.usedTextFallback) {
                            VoiceKeywordDiagnosticStage.RESULT_MATCHED_TEXT_FALLBACK
                        } else {
                            VoiceKeywordDiagnosticStage.RESULT_MATCHED
                        },
                        averageConfidence = evaluation.match.confidence
                            .takeUnless { evaluation.usedTextFallback },
                    )
                    evaluation.match
                }
            }
            val delivered = detectionSink(
                VoiceKeywordDetection(
                    playerId = playerId,
                    target = target,
                    matchedSubjectId = match.subjectId,
                    confidence = match.confidence,
                )
            )
            if (delivered) {
                matchedCurrentTarget = true
            }
        }

        private fun closeResources() {
            runCatching { recognizer?.close() }
            runCatching { decoder?.close() }
            recognizer = null
            decoder = null
            resampler = null
            grammar = null
            resourceTarget = null
            segmentSamples = 0
            matchedCurrentTarget = false
        }
    }
}
