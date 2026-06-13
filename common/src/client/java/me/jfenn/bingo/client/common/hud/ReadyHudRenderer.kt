package me.jfenn.bingo.client.common.hud

import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.common.KEYBIND_OPEN_CARD
import me.jfenn.bingo.common.map.rgba
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.div
import me.jfenn.bingo.common.utils.formatHHMMSS
import me.jfenn.bingo.common.utils.seconds
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.text.IText
import net.minecraft.text.Text
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal class ReadyHudRenderer(
    private val state: BingoHudState,
    private val text: TextProvider,
) {
    companion object {
        private const val TEXT_COLOR = 0xffA3F5A3.toInt()
        private const val LOADING_COLOR = 0xffffffff.toInt()
    }

    private var progressInterpolated: Float = 0f

    private fun drawProgressBar(drawService: IDrawService, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        progressInterpolated = (progressInterpolated.takeIf { it.isFinite() } ?: 0f) * 0.8f + progress * 0.2f

        val xProgress = x + ((width - 1) * progressInterpolated).roundToInt() + 1
        drawService.fill(x, y, xProgress, y + height + 1, LOADING_COLOR)
        drawService.drawHorizontalLine(xProgress, x + width, y, LOADING_COLOR)
        drawService.drawHorizontalLine(xProgress, x + width, y + height, LOADING_COLOR)
        drawService.drawVerticalLine(x + width, y, y + height, LOADING_COLOR)
    }

    fun drawReadyHud(drawService: IDrawService, x: Int, y: Int, width: Int) {
        val ready = state.ready ?: return
        if (!ready.isRunning) return
        val font = drawService.font

        val titleText = ready.title ?: when {
            ready.isReady -> text.string(StringKey.LobbyStartingPlayersReady, ready.readyPlayers, ready.totalPlayers)
            else -> text.string(StringKey.LobbyStartingReadyUp, Text.keybind("key.sneak"), Text.keybind(KEYBIND_OPEN_CARD))
        }
        val bodyText = ready.subtitle ?: when {
            ready.state == GameState.PREGAME -> text.string(StringKey.LobbyStartingTimeRemaining, ready.remainingDuration.formatHHMMSS())
            else -> text.string(StringKey.LobbyNextRoundTimeRemaining, ready.remainingDuration.formatHHMMSS())
        }

        val titleColor = when {
            ready.isReady -> LOADING_COLOR
            else -> {
                val titleAlpha = sin(state.now.nano.toDouble().div(1000000000.0) * 2.0 * PI) * 0.4 + 0.6
                rgba(255, 255, 255, (titleAlpha * 255).toInt())
            }
        }

        drawService.drawText(titleText, x + width/2 - font.getTextWidth(titleText)/2, y, titleColor, true)
        drawService.drawText(bodyText, x + width/2 - font.getTextWidth(bodyText)/2, y + font.getTextHeight() + 2, TEXT_COLOR, true)

        val remainingProgress = ready.remainingDuration / ready.totalDuration.coerceAtLeast(1.seconds)
        drawProgressBar(drawService, x, y + (font.getTextHeight() * 2) + 5, width, 5, 1f - remainingProgress.toFloat().coerceIn(0f, 1f))

        val charCount = ready.totalPlayers.coerceAtMost(10)
        val charReady = text.literal("✔")
        val charNot = text.literal("•")
        val chars = buildList(charCount) {
            for (i in 0 until charCount) {
                val isReady = i * ready.totalPlayers < charCount * ready.readyPlayers
                val char = if (isReady) charReady else charNot
                add(
                    ReadyChar(
                        char = char,
                        color = if (isReady) TEXT_COLOR else LOADING_COLOR,
                        width = font.getTextWidth(char) + 2
                    )
                )
            }
        }
        val charWidth = font.getTextWidth(charReady)
        val charTotalWidth = charWidth * chars.size
        for ((i, char) in chars.withIndex()) {
            drawService.drawText(
                text = char.char,
                x = x + width/2 - charTotalWidth/2 + (i*charWidth) + (charWidth-char.width)/2,
                y = y + (font.getTextHeight() * 2) + 12,
                color = char.color,
                shadow = true,
            )
        }
    }

    private class ReadyChar(
        val char: IText,
        val color: Int,
        val width: Int,
    )
}