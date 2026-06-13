package me.jfenn.bingo.client.common.hud.screen

import me.jfenn.bingo.client.common.hud.BingoMessageRenderer
import me.jfenn.bingo.client.common.hud.card.ClientCardBufferRenderer.Companion.CARD_HEIGHT
import me.jfenn.bingo.client.common.hud.card.ClientCardBufferRenderer.Companion.CARD_WIDTH
import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.renderer.CursorType
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.use
import me.jfenn.bingo.client.platform.screen.*
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.CardAlignment
import me.jfenn.bingo.common.scoring.GameMessagePacket
import me.jfenn.bingo.common.scoring.ScoreMessagePacket
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import net.minecraft.util.Formatting
import org.joml.Vector2d
import org.joml.Vector2i
import org.koin.core.Koin
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal class BingoCardPlacementScreen(
    koin: Koin,
    private val onCloseCallback: (Result) -> Unit,
    private val text: TextProvider = koin.get(),
    private val state: BingoHudState = koin.get(),
    private val config: BingoConfig = koin.get(),
    private val helper: IMutableScreenHelper,
    private val messageRenderer: BingoMessageRenderer = koin.get(),
    buttonFactory: IButtonFactory = koin.get(),
) : IScreen {

    class Factory(
        private val koin: Koin,
        private val text: TextProvider,
        private val state: BingoHudState,
        private val config: BingoConfig,
        private val screenFactory: IScreenFactory,
    ) {
        fun create(onClose: (Result) -> Unit) = screenFactory.build(text.string(StringKey.CardTitle)) { helper ->
            BingoCardPlacementScreen(
                koin = koin,
                onCloseCallback = onClose,
                text = text,
                state = state,
                config = config,
                helper = helper,
            )
        }
    }

    sealed interface Result {
        data class Ok(
            val cardScale: Float,
            val cardAlignment: CardAlignment,
            val cardOffsetX: Int,
            val cardOffsetY: Int,
        ) : Result
        data object Cancel : Result
    }

    private val scaledWindowWidth by helper::width
    private val scaledWindowHeight by helper::height

    private val cancelButton = buttonFactory.createDefaultButton(
        message = text.translatable("gui.cancel", "Cancel"),
        onClick = { onCloseCallback(Result.Cancel) },
    )

    private val saveButton = buttonFactory.createDefaultButton(
        message = text.translatable("gui.ok", "Ok"),
        onClick = {
            writeCardPosition()
            onCloseCallback(Result.Ok(
                cardScale = cardScale,
                cardAlignment = cardAlignment,
                cardOffsetX = cardOffsetX,
                cardOffsetY = cardOffsetY,
            ))
        },
    )

    val cardColors = state.cardColors.getTeamColors(null, null)

    var cardScale: Float = config.client.cardScale
    var cardAlignment: CardAlignment = config.client.cardAlignment
    var cardOffsetX = config.client.cardOffsetX
    var cardOffsetY = config.client.cardOffsetY

    var cardLeft: Float = 0f
    var cardTop: Float = 0f
    val cardRight get() = cardLeft + (CARD_WIDTH * cardScale)
    val cardBottom get() = cardTop + (CARD_HEIGHT * cardScale)

    var dragFromLeft: Float = 0f
    var dragFromTop: Float = 0f
    var dragStart: Vector2d? = null

    var resizeFromScale: Float = 1f
    var resizeStart: Vector2d? = null

    private fun initCardPosition() {
        val offsetX = cardOffsetX * (if (cardAlignment.x > 0) -1 else 1)
        val offsetY = cardOffsetY * (if (cardAlignment.y > 0) -1 else 1)

        cardLeft = (cardAlignment.x * (scaledWindowWidth - (CARD_WIDTH * cardScale)) + offsetX)
            .coerceIn(0f, scaledWindowWidth - (CARD_WIDTH * cardScale))
        cardTop = (cardAlignment.y * (scaledWindowHeight - (CARD_HEIGHT * cardScale)) + offsetY)
            .coerceIn(0f, scaledWindowHeight - (CARD_HEIGHT * cardScale))
    }

    private fun applyCardPosition() {
        val isRightEdge = (scaledWindowWidth - cardRight) < cardLeft
        val isBottomEdge = (scaledWindowHeight - cardBottom) < cardTop

        cardAlignment = when {
            isRightEdge && isBottomEdge -> CardAlignment.BOTTOM_RIGHT
            isRightEdge -> CardAlignment.TOP_RIGHT
            isBottomEdge -> CardAlignment.BOTTOM_LEFT
            else -> CardAlignment.TOP_LEFT
        }

        cardOffsetX = when {
            isRightEdge -> scaledWindowWidth - cardRight
            else -> cardLeft
        }.roundToInt()

        cardOffsetY = when {
            isBottomEdge -> scaledWindowHeight - cardBottom
            else -> cardTop
        }.roundToInt()
    }

    private fun writeCardPosition() {
        config.client.cardAlignment = cardAlignment
        config.client.cardScale = cardScale
        config.client.cardOffsetX = cardOffsetX
        config.client.cardOffsetY = cardOffsetY
    }

    private fun initButtons() {
        helper.clearChildren()
        helper.addDrawable(CardDrawable())
        helper.addButton(cancelButton)
        helper.addButton(saveButton)

        cancelButton.position = Vector2i(
            scaledWindowWidth/2 - cancelButton.size.x - 4,
            scaledWindowHeight - cancelButton.size.y - 8,
        )
        saveButton.position = Vector2i(
            scaledWindowWidth/2 + 4,
            scaledWindowHeight - cancelButton.size.y - 8,
        )
    }

    override fun init() {
        initButtons()
        initCardPosition()
    }

    override fun resize(width: Int, height: Int) {
        initButtons()
        initCardPosition()
    }

    private val resizeHandleSize = 10

    private fun isIntersectingRightBorder(mouseX: Double, mouseY: Double): Boolean {
        return mouseX in cardRight-resizeHandleSize..cardRight+resizeHandleSize && mouseY in cardTop..cardBottom
    }

    private fun isIntersectingBottomBorder(mouseX: Double, mouseY: Double): Boolean {
        return mouseX in cardLeft..cardRight && mouseY in cardBottom-resizeHandleSize..cardBottom+resizeHandleSize
    }

    private fun isIntersectingCardBorder(mouseX: Double, mouseY: Double): Boolean {
        return isIntersectingRightBorder(mouseX, mouseY) || isIntersectingBottomBorder(mouseX, mouseY)
    }

    private fun isIntersectingCard(mouseX: Double, mouseY: Double): Boolean {
        return mouseX in cardLeft..cardRight && mouseY in cardTop..cardBottom
    }

    private val cardScaleSnapToValues = arrayOf(0.5f, 0.75f, 1f, 1.5f, 2f)

    override fun mouseDragged(mouseX: Double, mouseY: Double): Boolean {
        val pos = Vector2d(mouseX, mouseY)

        if (resizeStart != null || (isIntersectingCardBorder(mouseX, mouseY) && dragStart == null)) {
            val start = resizeStart ?: run {
                resizeFromScale = cardScale
                resizeStart = pos
                pos
            }

            val cardPos = Vector2d(cardLeft.toDouble(), cardTop.toDouble())
            val distanceToStart = cardPos.distance(start).toFloat()
            val distanceToPos = cardPos.distance(pos).toFloat()
            cardScale = ((resizeFromScale / distanceToStart) * distanceToPos)
                .coerceIn(.25f, scaledWindowHeight.toFloat()/CARD_HEIGHT)

            // snap to hardcoded fractions
            for (cardScaleValue in cardScaleSnapToValues) {
                if ((cardScaleValue - cardScale).absoluteValue < 0.05) {
                    cardScale = cardScaleValue
                }
            }
            return true
        }

        if (dragStart != null || isIntersectingCard(mouseX, mouseY)) {
            val start = dragStart ?: run {
                dragFromLeft = cardLeft
                dragFromTop = cardTop
                dragStart = pos
                pos
            }
            cardLeft = dragFromLeft + (pos.x - start.x).toFloat()
            cardTop = dragFromTop + (pos.y - start.y).toFloat()
            return true
        }

        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double): Boolean {
        if (resizeStart != null) {
            resizeStart = null
            applyCardPosition()
            initCardPosition()
            return true
        }
        if (dragStart != null) {
            dragStart = null
            applyCardPosition()
            initCardPosition()
            return true
        }
        return false
    }

    private val helpText1 = text.string(StringKey.ConfigCardPositionHelpMove)
    private val helpText2 = text.string(StringKey.ConfigCardPositionHelpResize)

    private fun createDummyMessages(): List<BingoHudState.ScoreMessage> {
        val playerName = text.literal("\uD83C\uDF3A fennifith").formatted(Formatting.LIGHT_PURPLE)
        val messages = listOf(
            text.string(StringKey.GameMessageCapturedItem, playerName, text.empty().append("[").append(text.string(StringKey.ObjectiveExampleYourNose)).append("]")),
        )
        return messages.map { message ->
            BingoHudState.ScoreMessage(
                createdAt = state.now.minus(500L, ChronoUnit.MILLIS),
                packet = GameMessagePacket(
                    id = UUID(0L, 0L),
                    timeElapsed = Duration.ZERO,
                    team = BingoTeamKey(""),
                    decoration = null,
                    messageType = ScoreMessagePacket.MessageType.ITEM_SCORED,
                    message = message,
                )
            )
        }
    }

    inner class CardDrawable : IDrawable {
        override fun render(drawService: IDrawService) {
            val (mouseX, mouseY) = drawService.mouse.let { Pair(it.x.toDouble(), it.y.toDouble()) }

            drawService.matrices.use {
                drawService.matrices.translate(cardLeft, cardTop, 0f)
                drawService.matrices.scale(cardScale, cardScale, 1f)

                drawService.drawGuiTexture(
                    texture = cardColors.cardTexture,
                    x = 0, y = 0,
                    u = 0f, v = 0f,
                    width = CARD_WIDTH,
                    height = CARD_HEIGHT,
                    textureWidth = CARD_WIDTH,
                    textureHeight = CARD_HEIGHT,
                )

                val isOverCardBorder = isIntersectingCardBorder(mouseX, mouseY)
                val isOverCard = isIntersectingCard(mouseX, mouseY)

                if (isOverCardBorder && resizeStart == null) {
                    drawService.setCursor(
                        when {
                            isIntersectingRightBorder(mouseX, mouseY) -> CursorType.RESIZE_HORIZONTAL
                            else -> CursorType.RESIZE_VERTICAL
                        }
                    )
                } else if (isOverCard || dragStart != null || resizeStart != null) {
                    drawService.setCursor(CursorType.RESIZE_ALL)
                }

                if (dragStart != null || resizeStart != null) {
                    drawService.overlayFill(0, 0, CARD_WIDTH, CARD_HEIGHT, 0x80ffffff.toInt())
                } else if (isOverCardBorder || isOverCard) {
                    drawService.drawGuiTexture(
                        texture = cardColors.cardTextureOutline,
                        x = 0, y = 0,
                        u = 0f, v = 0f,
                        width = CARD_WIDTH,
                        height = CARD_HEIGHT,
                        textureWidth = CARD_WIDTH,
                        textureHeight = CARD_HEIGHT,
                    )
                }
            }

            messageRenderer.drawMessages(
                drawService,
                x = cardLeft.toInt(),
                y = cardTop.toInt() + (CARD_HEIGHT * cardScale * (1 - cardAlignment.y)).toInt(),
                z = 0,
                cardAlignment = cardAlignment,
                cardScale = cardScale,
                messages = createDummyMessages(),
            )
        }
    }

    override fun render(drawService: IDrawService, mouseX: Int, mouseY: Int, delta: Float) {
        drawService.matrices.use {
            val helpText1Size = drawService.font.getTextWidth(helpText1)
            drawService.drawText(helpText1, scaledWindowWidth/2 - helpText1Size/2, 8, 0xFF_A3F5A3.toInt(), true)
            val helpText2Size = drawService.font.getTextWidth(helpText2)
            drawService.drawText(helpText2, scaledWindowWidth/2 - helpText2Size/2, 16 + drawService.font.getTextHeight(), 0xFF_A3F5A3.toInt(), true)
        }
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun keyPressed(input: IKeyInput): Boolean {
        if (input.isEscape) {
            onCloseCallback(Result.Cancel)
            return true
        }

        return false
    }
}