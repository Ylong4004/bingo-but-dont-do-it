package me.jfenn.bingo.integrations.voice

import org.vosk.LibVosk
import java.nio.charset.StandardCharsets

/**
 * Vosk 的 C 接口规定所有字符串均使用 UTF-8，但 JNA 在 Windows 中文环境下
 * 默认可能使用 GBK。若不显式修正，中文 grammar 会在原生边界损坏并全部变成
 * `[unk]`，识别结果返回 Java 时也会成为乱码。
 */
internal object VoskNativeEncoding {
    private const val JNA_ENCODING_PROPERTY = "jna.encoding"
    private val requiredEncoding = StandardCharsets.UTF_8.name()

    @Volatile
    private var initialized = false

    /**
     * JNA 会在首次注册 [LibVosk] 时缓存字符串编码，因此必须把 UTF-8 设置包住
     * 第一次原生调用。注册完成后恢复全局属性，避免影响同一进程内的其他模组。
     */
    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val previous = System.getProperty(JNA_ENCODING_PROPERTY)
            try {
                System.setProperty(JNA_ENCODING_PROPERTY, requiredEncoding)
                LibVosk.vosk_set_log_level(-1)
                initialized = true
            } finally {
                if (previous == null) {
                    System.clearProperty(JNA_ENCODING_PROPERTY)
                } else {
                    System.setProperty(JNA_ENCODING_PROPERTY, previous)
                }
            }
        }
    }

    fun current(): String = if (initialized) requiredEncoding else "未初始化"

    fun isConfigured(): Boolean = initialized
}
