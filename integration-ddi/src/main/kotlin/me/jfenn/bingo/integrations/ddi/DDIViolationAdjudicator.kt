package me.jfenn.bingo.integrations.ddi

/** 违规证据进入裁决模块时的来源。 */
enum class DDIEvidenceSource {
    /** 由服务端已验证的游戏事件或规则引擎直接产生。 */
    AUTHORITATIVE_GAMEPLAY,
    /** 由服务端计时器按已分配词条产生。 */
    AUTHORITATIVE_DEADLINE,
    /** 由离线语音识别器给出的候选结果。 */
    VOICE_RECOGNITION,
}

/**
 * 传入违规裁决模块的最小证据投影。
 *
 * 具体的玩家、词条和分配版本仍由调用方在其自己的游戏作用域内校验；本模块只决定
 * 该证据能否直接导致处罚。这样投票、其它 ASR adapter 和未来的复核 UI 可以复用同一
 * 裁决策略，而无需理解 Minecraft 或 Simple Voice Chat 状态。
 */
data class DDIViolationEvidence(
    val source: DDIEvidenceSource,
    val exactTargetMatch: Boolean,
    /**
     * 识别器提供的 0.0–1.0 最终词条置信度。null 表示来源没有提供可审计的词级置信度。
     */
    val confidence: Double? = null,
)

/** 裁决结果；只有 [AUTOMATIC_PENALTY] 可以进入扣血链路。 */
enum class DDIAdjudicationDecision {
    AUTOMATIC_PENALTY,
    /** 候选可供玩家主动指控，但绝不会自行创建投票或扣血。 */
    MANUAL_ACCUSATION_ONLY,
    REJECTED,
}

/**
 * 将证据质量策略收口为一个纯模块。
 *
 * Interface：调用方只传入来源、精确匹配状态和置信度，并只根据返回结果决定是否处罚。
 * Implementation：语音质量阈值、缺失置信度与非法数值等细节均保持在此处，避免扩散到
 * 语音桥、目标管理器和未来投票实现中。
 */
class DDIViolationAdjudicator(
    private val automaticVoiceConfidence: Double = DEFAULT_AUTOMATIC_VOICE_CONFIDENCE,
) {
    init {
        require(automaticVoiceConfidence in 0.0..1.0) {
            "Automatic voice confidence must be between 0.0 and 1.0"
        }
    }

    fun decide(evidence: DDIViolationEvidence): DDIAdjudicationDecision {
        if (!evidence.exactTargetMatch) return DDIAdjudicationDecision.REJECTED
        return when (evidence.source) {
            DDIEvidenceSource.AUTHORITATIVE_GAMEPLAY,
            DDIEvidenceSource.AUTHORITATIVE_DEADLINE,
            -> DDIAdjudicationDecision.AUTOMATIC_PENALTY

            DDIEvidenceSource.VOICE_RECOGNITION -> decideVoiceEvidence(evidence.confidence)
        }
    }

    private fun decideVoiceEvidence(confidence: Double?): DDIAdjudicationDecision {
        if (confidence == null) return DDIAdjudicationDecision.MANUAL_ACCUSATION_ONLY
        if (confidence.isNaN() || confidence.isInfinite() || confidence !in 0.0..1.0) {
            return DDIAdjudicationDecision.REJECTED
        }
        return if (confidence >= automaticVoiceConfidence) {
            DDIAdjudicationDecision.AUTOMATIC_PENALTY
        } else {
            DDIAdjudicationDecision.MANUAL_ACCUSATION_ONLY
        }
    }

    companion object {
        /** 与当前 Vosk 最终词平均置信度门槛保持一致，且不放宽词级门槛。 */
        const val DEFAULT_AUTOMATIC_VOICE_CONFIDENCE = 0.55
    }
}
