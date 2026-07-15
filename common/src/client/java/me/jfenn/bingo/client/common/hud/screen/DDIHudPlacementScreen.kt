package me.jfenn.bingo.client.common.hud.screen

import me.jfenn.bingo.client.common.hud.DDIHudStyle
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.renderer.CursorType
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.use
import me.jfenn.bingo.client.platform.screen.*
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.CardAlignment
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import net.minecraft.util.Formatting
import org.joml.Vector2d
import org.joml.Vector2i
import org.koin.core.Koin
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt

/** Drag/resize editor for the DDI status panel, modelled after the Bingo card editor. */
internal class DDIHudPlacementScreen(
    private val onCloseCallback: (Result) -> Unit,
    private val text: TextProvider,
    private val config: BingoConfig,
    private val client: IClient,
    private val helper: IMutableScreenHelper,
    buttonFactory: IButtonFactory,
) : IScreen {

    class Factory(
        private val koin: Koin,
        private val text: TextProvider,
        private val config: BingoConfig,
        private val client: IClient,
        private val screenFactory: IScreenFactory,
    ) {
        fun create(onClose: (Result) -> Unit) = screenFactory.build(
            text.string(StringKey.ConfigDdiHudPosition)
        ) { helper ->
            DDIHudPlacementScreen(
                onCloseCallback = onClose,
                text = text,
                config = config,
                client = client,
                helper = helper,
                buttonFactory = koin.get(),
            )
        }
    }

    sealed interface Result {
        data class Ok(
            val scale: Float,
            val alignment: CardAlignment,
            val offsetX: Int,
            val offsetY: Int,
        ) : Result

        data object Cancel : Result
    }

    private val width by helper::width
    private val height by helper::height
    private val basePanelHeight: Int
        get() = DDIHudStyle.HEADER_HEIGHT + DDIHudStyle.PANEL_PADDING * 2 +
            (client.font.getTextHeight() + DDIHudStyle.LINE_GAP) * previewLines.size -
            DDIHudStyle.LINE_GAP

    private var scale = config.client.ddiHudScale
    private var alignment = config.client.ddiHudAlignment
    private var offsetX = config.client.ddiHudOffsetX
    private var offsetY = config.client.ddiHudOffsetY

    private var panelLeft = 0f
    private var panelTop = 0f
    private val panelWidth get() = DDIHudStyle.PANEL_WIDTH * scale
    private val panelHeight get() = basePanelHeight * scale
    private val panelRight get() = panelLeft + panelWidth
    private val panelBottom get() = panelTop + panelHeight
    private val panelLeftD get() = panelLeft.toDouble()
    private val panelTopD get() = panelTop.toDouble()
    private val panelRightD get() = panelRight.toDouble()
    private val panelBottomD get() = panelBottom.toDouble()

    private var dragStart: Vector2d? = null
    private var dragFromLeft = 0f
    private var dragFromTop = 0f
    private var resizeStart: Vector2d? = null
    private var resizeFromScale = 1f

    private val previewTitle = text.literal("不要做挑战 · 队伍共享")
        .formatted(Formatting.GOLD, Formatting.BOLD)
    private val previewLines = listOf(
        text.literal("我方 橙队：❤×3  3/3  ⏱ 60s").formatted(Formatting.RED),
        text.literal("◆ 蓝队：").formatted(Formatting.BLUE)
            .append(text.literal("挖掘钻石  ❤×3").formatted(Formatting.AQUA)),
        text.literal("◆ 红队：").formatted(Formatting.RED)
            .append(text.literal("攻击玩家  ❤×2").formatted(Formatting.AQUA)),
    )

    private val cancelButton = buttonFactory.createDefaultButton(
        message = text.translatable("gui.cancel", "Cancel"),
        onClick = { onCloseCallback(Result.Cancel) },
    )
    private val saveButton = buttonFactory.createDefaultButton(
        message = text.translatable("gui.ok", "Ok"),
        onClick = {
            onCloseCallback(Result.Ok(scale, alignment, offsetX, offsetY))
        },
    )

    private fun maximumScale(): Float {
        val widthScale = width.toFloat() / DDIHudStyle.PANEL_WIDTH
        val heightScale = (height - 48).coerceAtLeast(1).toFloat() / basePanelHeight
        return min(2.5f, min(widthScale, heightScale)).coerceAtLeast(0.5f)
    }

    private fun initPanelPosition() {
        scale = scale.coerceIn(0.5f, maximumScale())
        val signedOffsetX = offsetX * if (alignment.x > 0) -1 else 1
        val signedOffsetY = offsetY * if (alignment.y > 0) -1 else 1
        panelLeft = (alignment.x * (width - panelWidth) + signedOffsetX)
            .coerceIn(0f, (width - panelWidth).coerceAtLeast(0f))
        panelTop = (alignment.y * (height - panelHeight) + signedOffsetY)
            .coerceIn(0f, (height - panelHeight).coerceAtLeast(0f))
    }

    private fun applyPanelPosition() {
        panelLeft = panelLeft.coerceIn(0f, (width - panelWidth).coerceAtLeast(0f))
        panelTop = panelTop.coerceIn(0f, (height - panelHeight).coerceAtLeast(0f))
        val isRightEdge = width - panelRight < panelLeft
        val isBottomEdge = height - panelBottom < panelTop
        alignment = when {
            isRightEdge && isBottomEdge -> CardAlignment.BOTTOM_RIGHT
            isRightEdge -> CardAlignment.TOP_RIGHT
            isBottomEdge -> CardAlignment.BOTTOM_LEFT
            else -> CardAlignment.TOP_LEFT
        }
        offsetX = (if (isRightEdge) width - panelRight.roundToInt() else panelLeft.roundToInt())
            .coerceAtLeast(0)
        offsetY = (if (isBottomEdge) height - panelBottom.roundToInt() else panelTop.roundToInt())
            .coerceAtLeast(0)
    }

    private fun initButtons() {
        helper.clearChildren()
        helper.addDrawable(PanelDrawable())
        helper.addButton(cancelButton)
        helper.addButton(saveButton)
        cancelButton.position = Vector2i(width / 2 - cancelButton.size.x - 4, height - cancelButton.size.y - 8)
        saveButton.position = Vector2i(width / 2 + 4, height - saveButton.size.y - 8)
    }

    override fun init() {
        initButtons()
        initPanelPosition()
    }

    override fun resize(width: Int, height: Int) {
        initButtons()
        initPanelPosition()
    }

    private val resizeHandleSize = 8.0

    private fun isOnRightBorder(mouseX: Double, mouseY: Double): Boolean =
        mouseX in panelRightD - resizeHandleSize..panelRightD + resizeHandleSize &&
            mouseY in panelTopD..panelBottomD

    private fun isOnBottomBorder(mouseX: Double, mouseY: Double): Boolean =
        mouseX in panelLeftD..panelRightD &&
            mouseY in panelBottomD - resizeHandleSize..panelBottomD + resizeHandleSize

    private fun isOnPanel(mouseX: Double, mouseY: Double): Boolean =
        mouseX in panelLeftD..panelRightD && mouseY in panelTopD..panelBottomD

    override fun mouseDragged(mouseX: Double, mouseY: Double): Boolean {
        val position = Vector2d(mouseX, mouseY)
        val onBorder = isOnRightBorder(mouseX, mouseY) || isOnBottomBorder(mouseX, mouseY)
        if (resizeStart != null || (onBorder && dragStart == null)) {
            val start = resizeStart ?: position.also {
                resizeStart = it
                resizeFromScale = scale
            }
            val origin = Vector2d(panelLeft.toDouble(), panelTop.toDouble())
            val startDistance = origin.distance(start).coerceAtLeast(1.0)
            scale = (resizeFromScale * (origin.distance(position) / startDistance).toFloat())
                .coerceIn(0.5f, maximumScale())
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).firstOrNull {
                (it - scale).absoluteValue < 0.04f
            }?.let { scale = it.coerceAtMost(maximumScale()) }
            return true
        }

        if (dragStart != null || isOnPanel(mouseX, mouseY)) {
            val start = dragStart ?: position.also {
                dragStart = it
                dragFromLeft = panelLeft
                dragFromTop = panelTop
            }
            panelLeft = dragFromLeft + (position.x - start.x).toFloat()
            panelTop = dragFromTop + (position.y - start.y).toFloat()
            return true
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double): Boolean {
        if (resizeStart != null || dragStart != null) {
            resizeStart = null
            dragStart = null
            applyPanelPosition()
            initPanelPosition()
            return true
        }
        return false
    }

    private val moveHelp = text.string(StringKey.ConfigDdiHudPositionHelpMove)
    private val resizeHelp = text.string(StringKey.ConfigDdiHudPositionHelpResize)

    private inner class PanelDrawable : IDrawable {
        override fun render(drawService: IDrawService) {
            drawService.matrices.use {
                drawService.matrices.translate(panelLeft, panelTop, 0f)
                drawService.matrices.scale(scale, scale, 1f)
                DDIHudStyle.draw(
                    drawService = drawService,
                    title = previewTitle,
                    lines = previewLines,
                    backgroundOpacity = config.client.ddiHudBackgroundOpacity,
                )
            }

            val mouseX = drawService.mouse.x.toDouble()
            val mouseY = drawService.mouse.y.toDouble()
            when {
                isOnRightBorder(mouseX, mouseY) -> drawService.setCursor(CursorType.RESIZE_HORIZONTAL)
                isOnBottomBorder(mouseX, mouseY) -> drawService.setCursor(CursorType.RESIZE_VERTICAL)
                isOnPanel(mouseX, mouseY) || dragStart != null || resizeStart != null ->
                    drawService.setCursor(CursorType.RESIZE_ALL)
            }
        }
    }

    override fun render(drawService: IDrawService, mouseX: Int, mouseY: Int, delta: Float) {
        val moveWidth = drawService.font.getTextWidth(moveHelp)
        val resizeWidth = drawService.font.getTextWidth(resizeHelp)
        drawService.drawText(moveHelp, width / 2 - moveWidth / 2, 8, 0xFFA3F5A3.toInt(), true)
        drawService.drawText(
            resizeHelp,
            width / 2 - resizeWidth / 2,
            16 + drawService.font.getTextHeight(),
            0xFFA3F5A3.toInt(),
            true,
        )
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
