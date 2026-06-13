package me.jfenn.bingo.client.common.hud

import me.jfenn.bingo.client.common.hud.card.CardTileRenderer
import me.jfenn.bingo.client.common.hud.card.ClientCardBufferRenderer
import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.CardAlignment
import me.jfenn.bingo.common.map.rgba
import me.jfenn.bingo.common.scoring.ScoreMessagePacket
import me.jfenn.bingo.common.utils.div
import me.jfenn.bingo.common.utils.milliseconds
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.seconds
import net.minecraft.util.Identifier
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

internal class BingoMessageRenderer(
    private val state: BingoHudState,
    private val config: BingoConfig,
    private val cardTileRenderer: CardTileRenderer,
    private val client: IClient,
) {

    companion object {
        const val ITEM_HEIGHT = 18

        val ICON_HIDDEN_ID = Identifier.of("minecraft", "bingo/message_item_hidden")!!
        val ICON_CHECK_ID = Identifier.of("minecraft", "bingo/message_check")!!
        val ICON_LEADING_ID = Identifier.of("minecraft", "bingo/message_leading")!!
    }

    private val fadeInDuration = 500.milliseconds

    fun drawMessages(
        drawService: IDrawService,
        x: Int,
        y: Int,
        z: Int,
        messageScale: Float = config.client.messageScale,
        cardAlignment: CardAlignment = config.client.cardAlignment,
        cardScale: Float = config.client.cardScale,
        messages: List<BingoHudState.ScoreMessage> = state.messages,
    ) {
        val isAboveCard = cardAlignment.y > 0
        val isLeftOfCard = cardAlignment.x > 0

        val direction = if (isAboveCard) -1 else 1
        val now = state.now
        val duration = config.client.messageDurationSeconds.seconds

        drawService.matrices.push()
        drawService.matrices.translate(x.toFloat(), y.toFloat(), z.toFloat())
        drawService.matrices.scale(messageScale, messageScale, 1f)

        var itemY = if (isAboveCard) -ITEM_HEIGHT else 0
        for (message in messages.reversed().take(5)) {
            val timeSinceCreated = now - message.createdAt
            val fadeIn = (timeSinceCreated / fadeInDuration).toFloat().coerceIn(0f, 1f)
            val fadeOut = (1f - ((timeSinceCreated - duration) / fadeInDuration).toFloat()).coerceIn(0f, 1f)
            val visibility = fadeIn * fadeOut

            if (timeSinceCreated > (duration + fadeInDuration)) {
                // if the item is no longer visible and its duration is over, remove it from the list
                state.messages.remove(message)
                continue
            }

            drawService.matrices.push()
            drawService.matrices.translate(
                if (isLeftOfCard) ClientCardBufferRenderer.CARD_WIDTH.toFloat() * cardScale / messageScale else 0f,
                itemY.toFloat() + (1f - fadeOut).pow(2) * message.height * direction,
                0f
            )

            drawMessage(
                drawService = drawService,
                message = message,
                visibility = visibility,
                isLeftOfCard = isLeftOfCard,
            )

            drawService.matrices.pop()

            itemY += (message.height * direction * fadeIn.pow(2)).roundToInt()
        }

        drawService.matrices.pop()
    }

    fun getMessageWrapped(message: BingoHudState.ScoreMessage, width: Int): BingoHudState.ScoreMessage {
        val messageWidth = client.font.getTextWidth(message.packet.message)

        val textLines = when {
            messageWidth > width - 28 -> client.font.wrapLines(message.packet.message, width - 28)
            else -> listOf(message.packet.message)
        }

        val textHeight = textLines.size*client.font.getTextHeight() + (textLines.size-1)*2
        val messageHeight = max(textHeight + 6, ITEM_HEIGHT)

        return message.copy(messageLines = textLines, height = messageHeight)
    }

    fun drawMessage(
        drawService: IDrawService,
        message: BingoHudState.ScoreMessage,
        visibility: Float,
        isLeftOfCard: Boolean,
    ) {
        val usingShaderColor = visibility < 0.99f
        val alpha = visibility.pow(2)
        if (usingShaderColor) {
            drawService.setShaderColor(1f, 1f, 1f, alpha)
        }

        val itemX = if (isLeftOfCard) -8 - message.height else 8
        val itemY = message.height/2 - 8
        if (message.packet.image.let { it.item != null || it.texture != null }) {
            cardTileRenderer.renderTile(
                drawService,
                message.packet.image,
                message.packet.imageList,
                message.packet.decoration,
                itemX, itemY
            )
            cardTileRenderer.renderTileDecorations(drawService, message.packet.decoration, itemX, itemY)
        } else {
            val iconTexture = when (message.packet.messageType) {
                ScoreMessagePacket.MessageType.LEADING_TEAM -> ICON_LEADING_ID
                ScoreMessagePacket.MessageType.CARD_COMPLETED,
                ScoreMessagePacket.MessageType.LINE_SCORED -> ICON_CHECK_ID
                ScoreMessagePacket.MessageType.ITEM_SCORED -> ICON_HIDDEN_ID
            }

            drawService.drawGuiTexture(
                texture = iconTexture,
                x = itemX, y = itemY,
                u = 0f, v = 0f,
                width = 16, height = 16,
                textureWidth = 16, textureHeight = 16,
            )
        }

        if (usingShaderColor) {
            drawService.setShaderColor(1f, 1f, 1f, 1f)
        }

        val messageWidth = message.messageLines.maxOf {
            drawService.font.getTextWidth(it)
        }

        val textX = if (isLeftOfCard) {
            -28 - messageWidth
        } else 28

        val textLines = message.messageLines

        val textY = message.height/2 - (textLines.size*drawService.font.getTextHeight() + (textLines.size-1)*2)/2

        textLines.forEachIndexed { index, text ->
            drawService.drawText(
                text,
                textX, textY + (drawService.font.getTextHeight() + 2)*index,
                rgba(255, 255, 255, (alpha * 255).toInt().coerceIn(5, 255)),
                true,
            )
        }
    }

}