package me.jfenn.bingo.client.common.hud

import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Identifier

/** DDI 实时 HUD 及其位置预览共用的 Bingo 风格绘制组件。 */
object DDIHudStyle {
    const val PANEL_WIDTH = 200
    const val PANEL_PADDING = 7
    const val HEADER_HEIGHT = 27
    const val ICON_SIZE = 20
    const val LINE_GAP = 3

    private val FORBIDDEN_ICON = Identifier.of("minecraft", "bingo/image_frame_forbidden")!!

    fun lineHeight(drawService: IDrawService): Int = drawService.font.getTextHeight() + LINE_GAP

    fun panelHeight(drawService: IDrawService, lineCount: Int): Int =
        HEADER_HEIGHT + PANEL_PADDING + lineHeight(drawService) * lineCount + PANEL_PADDING - LINE_GAP

    fun draw(
        drawService: IDrawService,
        title: IText,
        lines: List<IText>,
        width: Int = PANEL_WIDTH,
        backgroundOpacity: Float = 0.30f,
    ) {
        val height = panelHeight(drawService, lines.size)
        val alpha = (backgroundOpacity.coerceIn(0f, 0.9f) * 255).toInt()
        drawService.fill(0, 0, width, height, (alpha shl 24) or 0x101814)
        val borderColor = ((alpha.coerceAtLeast(96)) shl 24) or 0xA3F5A3
        drawService.drawHorizontalLine(0, width, 0, borderColor)
        drawService.drawHorizontalLine(0, width, height, borderColor)
        drawService.drawVerticalLine(0, 0, height, borderColor)
        drawService.drawVerticalLine(width, 0, height, borderColor)

        drawService.drawGuiTexture(
            texture = FORBIDDEN_ICON,
            x = PANEL_PADDING,
            y = (HEADER_HEIGHT - ICON_SIZE) / 2,
            u = 0f,
            v = 0f,
            width = ICON_SIZE,
            height = ICON_SIZE,
            textureWidth = ICON_SIZE,
            textureHeight = ICON_SIZE,
        )
        val titleX = PANEL_PADDING + ICON_SIZE + 4
        val maxTitleWidth = (width - titleX - PANEL_PADDING).coerceAtLeast(1)
        val fittedTitle = if (drawService.font.getTextWidth(title) > maxTitleWidth) {
            drawService.font.truncate(title, maxTitleWidth)
        } else {
            title
        }
        drawService.drawText(
            fittedTitle,
            titleX,
            (HEADER_HEIGHT - drawService.font.getTextHeight()) / 2,
            0xFFFFFFFF.toInt(),
            true,
        )
        drawService.drawHorizontalLine(
            PANEL_PADDING,
            width - PANEL_PADDING,
            HEADER_HEIGHT - 1,
            ((alpha.coerceAtLeast(80)) shl 24) or 0xA3F5A3,
        )

        val maxTextWidth = (width - PANEL_PADDING * 2).coerceAtLeast(1)
        val lineHeight = lineHeight(drawService)
        lines.forEachIndexed { index, line ->
            val fitted = if (drawService.font.getTextWidth(line) > maxTextWidth) {
                drawService.font.truncate(line, maxTextWidth)
            } else {
                line
            }
            drawService.drawText(
                fitted,
                PANEL_PADDING,
                HEADER_HEIGHT + PANEL_PADDING + index * lineHeight,
                0xFFFFFFFF.toInt(),
                true,
            )
        }
    }
}
