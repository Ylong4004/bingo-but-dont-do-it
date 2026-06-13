package me.jfenn.bingo.client.common.hud.screen

import me.jfenn.bingo.client.common.hud.BingoMessageRenderer
import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.IScrollableWidget
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatString
import me.jfenn.bingo.generated.StringKey
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.koin.core.Koin

internal class MessageHistoryWidget(
    koin: Koin,
    private val gameOver: BingoHudState.GameOver? = null,
    private val state: BingoHudState = koin.get(),
    private val messageRenderer: BingoMessageRenderer = koin.get(),
    private val client: IClient = koin.get(),
    private val text: TextProvider = koin.get(),
) : IScrollableWidget() {

    private val marginX = 14
    private val marginY = 12

    init {
        padding = 0
        background = 0
    }

    private var titleText: List<IText> = emptyList()
    private var subtitleText: List<IText> = emptyList()
    private var titleHeight: Int = 0

    private var statusText: List<IText> = emptyList()
    private var statusTextWidths: List<Int> = emptyList()
    private var statusHeight: Int = 0

    private var messages: List<BingoHudState.ScoreMessage> = emptyList()

    fun resize() {
        titleText = gameOver?.packet?.title
            ?.let { client.font.wrapLines(it, (width - marginX*2)/2) }
            ?: emptyList()
        subtitleText = gameOver?.packet?.subtitle
            ?.let { client.font.wrapLines(it, width - marginX*2) }
            ?: emptyList()
        titleHeight = (titleText.size * (client.font.getTextHeight() + 2) * 2) +
                (subtitleText.size * (client.font.getTextHeight() + 2)) +
                4 + 12 + 8

        statusText = listOfNotNull(
            state.gameStatus.remainingDuration
                ?.let { text.string(StringKey.ScoreboardTimeLeft, text.literal(it.formatString()).formatted(Formatting.YELLOW)) }
                ?: state.gameStatus.ingameDuration
                    ?.let { text.string(StringKey.ScoreboardTime, text.literal(it.formatString()).formatted(Formatting.YELLOW)) },
            text.string(StringKey.GameWaitingToStart)
                .formatted(Formatting.GRAY)
                .takeIf { !state.gameStatus.isDefaultInstance && state.gameStatus.ingameDuration == null }
        )
        statusTextWidths = statusText.map { client.font.getTextWidth(it) }
        statusHeight = (statusText.size * (client.font.getTextHeight() + 2)) +
                8 + 8

        messages = state.pastMessages.map {
            messageRenderer.getMessageWrapped(it, width - 4)
        }
    }

    override fun measureContentHeight(): Int {
        val gameOverHeight = when {
            state.gameOver != null -> titleHeight + 8
            else -> 0
        }

        val messagesHeight = messages.sumOf { it.height }
        return marginY + gameOverHeight + messagesHeight + marginY
    }

    private fun renderGameOver(drawService: IDrawService) {
        var titleY = 12
        var titleScale = 2f

        drawService.drawNinePatch(
            texture = Identifier.of("minecraft", "bingo/game_container")!!,
            x = 0,
            y = 0,
            width = width,
            height = titleHeight,
            sliceSize = 8,
            textureWidth = 200,
            textureHeight = 40,
        )

        drawService.matrices.push()
        drawService.matrices.scale(titleScale, titleScale, titleScale)
        for (line in titleText) {
            drawService.drawText(
                line,
                (marginX).div(titleScale).toInt(),
                (titleY/titleScale).toInt(),
                0xff_ffffff.toInt(),
                false,
            )
            titleY += (drawService.font.getTextHeight() + 2).times(titleScale).toInt()
        }
        drawService.matrices.pop()

        titleY += 4
        titleScale = 1f

        for (line in subtitleText) {
            drawService.drawText(
                line,
                (marginX).div(titleScale).toInt(),
                (titleY/titleScale).toInt(),
                0xff_ffffff.toInt(),
                false,
            )
            titleY += (drawService.font.getTextHeight() + 2).times(titleScale).toInt()
        }
    }

    private fun renderGameStatus(drawService: IDrawService) {
        var titleY = 9

        drawService.drawNinePatch(
            texture = Identifier.of("minecraft", "bingo/game_container")!!,
            x = 0,
            y = 0,
            width = width,
            height = statusHeight,
            sliceSize = 8,
            textureWidth = 200,
            textureHeight = 40,
        )

        for ((line, lineWidth) in statusText.zip(statusTextWidths)) {
            drawService.drawText(
                line,
                (width - lineWidth)/2,
                titleY,
                0xff_ffffff.toInt(),
                false,
            )
            titleY += drawService.font.getTextHeight() + 2
        }
    }

    override fun renderContents(drawService: IDrawService, mouseX: Int, mouseY: Int) {
        var drawY = marginY

        if (gameOver != null) {
            drawService.matrices.push()
            drawService.matrices.translate(0f, drawY.toFloat(), 0f)
            renderGameOver(drawService)
            drawService.matrices.pop()

            drawY += titleHeight + 8
        } else if (statusText.isNotEmpty()) {
            drawService.matrices.push()
            drawService.matrices.translate(0f, drawY.toFloat(), 0f)
            renderGameStatus(drawService)
            drawService.matrices.pop()

            drawY += statusHeight + 8
        }

        messages.reversed().forEach { scoreMessage ->
            drawService.matrices.push()
            drawService.matrices.translate(
                -6f,
                drawY.toFloat(),
                0f
            )

            messageRenderer.drawMessage(
                drawService = drawService,
                message = scoreMessage,
                visibility = 1f,
                isLeftOfCard = false,
            )

            drawService.matrices.pop()

            drawY += scoreMessage.height
        }
    }
}