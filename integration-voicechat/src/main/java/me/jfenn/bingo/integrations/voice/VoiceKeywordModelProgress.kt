package me.jfenn.bingo.integrations.voice

/** 游戏内模型准备过程的无敏感信息进度投影。 */
enum class VoiceKeywordModelProgressPhase {
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    LOADING,
}

data class VoiceKeywordModelProgress(
    val phase: VoiceKeywordModelProgressPhase,
    val completedBytes: Long = 0L,
    val totalBytes: Long? = null,
) {
    val percent: Int?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { total -> ((completedBytes.coerceIn(0L, total) * 100L) / total).toInt() }
}
