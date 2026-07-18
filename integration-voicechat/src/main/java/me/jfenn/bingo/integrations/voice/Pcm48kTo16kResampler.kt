package me.jfenn.bingo.integrations.voice

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** 带状态的低通 FIR 滤波器，随后进行精确的 3:1 抽取。 */
internal class Pcm48kTo16kResampler {
    companion object {
        private const val DECIMATION = 3
        private const val TAP_COUNT = 31
        // 16 kHz 输出的奈奎斯特频率是 48 kHz 输入采样率的 1/6。
        // 在它下方保留过渡带，以降低语音噪声产生的混叠。
        private const val CUTOFF = 0.145

        private val COEFFICIENTS: DoubleArray = DoubleArray(TAP_COUNT) { index ->
            val center = (TAP_COUNT - 1) / 2.0
            val x = index - center
            val sinc = if (x == 0.0) {
                2.0 * CUTOFF
            } else {
                sin(2.0 * PI * CUTOFF * x) / (PI * x)
            }
            val hamming = 0.54 - 0.46 * kotlin.math.cos(2.0 * PI * index / (TAP_COUNT - 1))
            sinc * hamming
        }.let { raw ->
            val sum = raw.sum()
            DoubleArray(raw.size) { raw[it] / sum }
        }
    }

    private val history = DoubleArray(TAP_COUNT)
    private var writeIndex = 0
    private var phase = 0

    fun process(input: ShortArray): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        // 额外预留一个元素，保证所有跨调用相位下的缓冲区安全；
        // 下方会返回实际写入长度。
        val result = ShortArray((input.size + DECIMATION - 1) / DECIMATION + 1)
        var outputIndex = 0
        for (sample in input) {
            history[writeIndex] = sample.toDouble()
            writeIndex = (writeIndex + 1) % TAP_COUNT
            if (phase == 0) {
                var filtered = 0.0
                // 此时 writeIndex 指向环形缓冲区中最旧的值。
                for (tap in COEFFICIENTS.indices) {
                    filtered += COEFFICIENTS[tap] * history[(writeIndex + tap) % TAP_COUNT]
                }
                result[outputIndex++] = filtered.roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            phase = (phase + 1) % DECIMATION
        }
        return if (outputIndex == result.size) result else result.copyOf(outputIndex)
    }

    fun reset() {
        history.fill(0.0)
        writeIndex = 0
        phase = 0
    }
}
