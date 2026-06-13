package me.jfenn.bingo.client.platform.renderer

import me.jfenn.bingo.client.platform.INativeImage
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import org.joml.Vector2i
import java.io.Closeable
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

interface IDrawService {
    val context: DrawContext
    val textRenderer: TextRenderer
    val font: IFont
    val matrices: IMatrixStack
    val window: IWindow
    fun setCursor(cursor: CursorType) = Unit

    val delta: Float
    val mouse: Vector2i

    fun draw()

    fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int)
    fun overlayFill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int)

    fun drawHorizontalLine(x1: Int, x2: Int, y: Int, color: Int)
    fun drawVerticalLine(x: Int, y1: Int, y2: Int, color: Int)

    fun enableBlend()
    fun disableBlend()

    fun setShaderColor(r: Float, g: Float, b: Float, a: Float)

    fun drawText(text: IText, x: Int, y: Int, color: Int, shadow: Boolean = false)
    fun drawItemStack(stack: IItemStack, x: Int, y: Int, seed: Int)
    fun drawTooltip(text: List<IText>, x: Int, y: Int)
    fun drawTooltipAddon(callback: () -> Unit) = callback()
    fun drawTooltipImmediate() {}

    fun drawDynamicTexture(
        texture: INativeImage,
        x: Int, y: Int,
        width: Int, height: Int
    )

    fun drawGuiTexture(
        texture: Identifier,
        x: Int, y: Int, z: Int = 0,
        u: Float, v: Float,
        width: Int, height: Int,
        textureWidth: Int, textureHeight: Int,
    )

    fun drawNinePatch(
        texture: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        sliceSize: Int,
        textureWidth: Int,
        textureHeight: Int,
    ) {
        val textureCenterWidth = textureWidth - sliceSize*2
        val textureCenterHeight = textureHeight - sliceSize*2
        val centerWidth = width - sliceSize*2
        val centerHeight = height - sliceSize*2
        val xParts = ceil(centerWidth.toFloat() / textureCenterWidth).roundToInt()
        val yParts = ceil(centerHeight.toFloat() / textureCenterHeight).roundToInt()

        // Draw center
        for (xPart in 0 until xParts) {
            val partWidth = min(centerWidth - (xPart * textureCenterWidth), textureCenterWidth)
            for (yPart in 0 until yParts) {
                val partHeight = min(centerHeight - (yPart * textureCenterHeight), textureCenterHeight)
                drawGuiTexture(
                    texture,
                    x = x + sliceSize + xPart*textureCenterWidth, y = y + sliceSize + yPart*textureCenterHeight,
                    u = sliceSize.toFloat(), v = sliceSize.toFloat(),
                    width = partWidth, height = partHeight,
                    textureWidth = textureWidth, textureHeight = textureHeight,
                )
            }
        }

        // Draw horizontal borders
        for (xPart in 0 until xParts) {
            val xPartWidth = min(centerWidth - (xPart * textureCenterWidth), textureCenterWidth)
            drawGuiTexture(
                texture,
                x = x + sliceSize + xPart*textureCenterWidth, y = y,
                u = sliceSize.toFloat(), v = 0f,
                width = xPartWidth, height = sliceSize,
                textureWidth = textureWidth, textureHeight = textureHeight,
            )
            drawGuiTexture(
                texture,
                x = x + sliceSize + xPart*textureCenterWidth, y = y + height - sliceSize,
                u = sliceSize.toFloat(), v = textureHeight.toFloat() - sliceSize,
                width = xPartWidth, height = sliceSize,
                textureWidth = textureWidth, textureHeight = textureHeight,
            )
        }

        // Draw vertical borders
        for (yPart in 0 until yParts) {
            val yPartHeight = min(centerHeight - (yPart * textureCenterHeight), textureCenterHeight)
            drawGuiTexture(
                texture,
                x = x, y = y + sliceSize + yPart*textureCenterHeight,
                u = 0f, v = sliceSize.toFloat(),
                width = sliceSize, height = yPartHeight,
                textureWidth = textureWidth, textureHeight = textureHeight,
            )
            drawGuiTexture(
                texture,
                x = x + width - sliceSize, y = y + sliceSize + yPart*textureCenterHeight,
                u = textureWidth.toFloat() - sliceSize, v = sliceSize.toFloat(),
                width = sliceSize, height = yPartHeight,
                textureWidth = textureWidth, textureHeight = textureHeight,
            )
        }

        // Draw corners
        drawGuiTexture(
            texture,
            x = x, y = y,
            u = 0f, v = 0f,
            width = sliceSize, height = sliceSize,
            textureWidth = textureWidth, textureHeight = textureHeight,
        )
        drawGuiTexture(
            texture,
            x = x + width - sliceSize, y = y,
            u = textureWidth.toFloat() - sliceSize, v = 0f,
            width = sliceSize, height = sliceSize,
            textureWidth = textureWidth, textureHeight = textureHeight,
        )
        drawGuiTexture(
            texture,
            x = x, y = y + height - sliceSize,
            u = 0f, v = textureHeight.toFloat() - sliceSize,
            width = sliceSize, height = sliceSize,
            textureWidth = textureWidth, textureHeight = textureHeight,
        )
        drawGuiTexture(
            texture,
            x = x + width - sliceSize, y = y + height - sliceSize,
            u = textureWidth.toFloat() - sliceSize, v = textureHeight.toFloat() - sliceSize,
            width = sliceSize, height = sliceSize,
            textureWidth = textureWidth, textureHeight = textureHeight,
        )
    }
}

interface IDrawServiceFactory {
    val window: IWindow

    fun create(drawContext: DrawContext): IDrawService

    val isBufferSupported: Boolean

    fun newBuffer(width: Int, height: Int): IFramebuffer
}

interface IFramebuffer : Closeable {
    val width: Int
    val height: Int
    fun register()
    fun resize(width: Int, height: Int)
    fun write(callback: (IDrawService) -> Unit)
    fun draw(service: IDrawService, width: Int, height: Int)
}

enum class CursorType {
    DEFAULT,
    POINTING_HAND,
    RESIZE_HORIZONTAL,
    RESIZE_VERTICAL,
    RESIZE_ALL,
}
