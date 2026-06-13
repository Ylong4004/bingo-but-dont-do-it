package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.systems.RenderSystem
import me.jfenn.bingo.client.impl.NativeImageImpl
import me.jfenn.bingo.client.platform.INativeImage
import me.jfenn.bingo.client.platform.renderer.*
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.impl.ItemStackFactory
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.BufferBuilderStorage
import net.minecraft.client.render.RenderLayer
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import org.joml.Vector2i

class DrawService(
    override val context: DrawContext,
) : IDrawService {

    override val textRenderer: TextRenderer = MinecraftClient.getInstance().textRenderer
    override val font: IFont = FontImpl(textRenderer)
    override val matrices: IMatrixStack = MatrixStackImpl(context.matrices)
    override val window: IWindow = Companion

    override var delta: Float = 0.5f
    override val mouse: Vector2i
        get() {
            val client = MinecraftClient.getInstance()
            val mouse = client.mouse
            return Vector2i(
                (mouse.x * client.window.scaledWidth.toDouble() / client.window.width.toDouble()).toInt(),
                (mouse.y * client.window.scaledHeight.toDouble() / client.window.height.toDouble()).toInt(),
            )
        }

    companion object : IDrawServiceFactory, IWindow {
        override val window: IWindow = this

        override val scaleFactor: Double get() = MinecraftClient.getInstance().window.scaleFactor
        override val scaledWindowWidth: Int
            get() = MinecraftClient.getInstance().window.scaledWidth
        override val scaledWindowHeight: Int
            get() = MinecraftClient.getInstance().window.scaledHeight

        override fun create(drawContext: DrawContext): IDrawService = DrawService(drawContext)

        private val bufferBuilderStorage by lazy {
            BufferBuilderStorage(Runtime.getRuntime().availableProcessors())
        }

        override val isBufferSupported: Boolean = true

        override fun newBuffer(width: Int, height: Int): IFramebuffer {
            val client = MinecraftClient.getInstance()
            val buffer = SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC)
            val context = DrawContext(client, bufferBuilderStorage.entityVertexConsumers)

            return FramebufferImpl(
                buffer, DrawService(context)
            )
        }
    }

    override fun draw() {
        context.draw()
    }

    override fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        context.fill(x1, y1, x2, y2, color)
    }

    override fun overlayFill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        context.fill(RenderLayer.getGuiOverlay(), x1, y1, x2, y2, color)
    }

    override fun drawHorizontalLine(x1: Int, x2: Int, y: Int, color: Int) {
        context.drawHorizontalLine(x1, x2, y, color)
    }

    override fun drawVerticalLine(x: Int, y1: Int, y2: Int, color: Int) {
        context.drawVerticalLine(x, y1, y2, color)
    }

    override fun enableBlend() {
        RenderSystem.enableBlend()
    }

    override fun disableBlend() {
        RenderSystem.disableBlend()
    }

    override fun setShaderColor(r: Float, g: Float, b: Float, a: Float) {
        context.setShaderColor(r, g, b, a)
    }

    override fun drawText(text: IText, x: Int, y: Int, color: Int, shadow: Boolean) {
        context.drawText(textRenderer, text.value, x, y, color, shadow)
    }

    override fun drawItemStack(stack: IItemStack, x: Int, y: Int, seed: Int) {
        require(stack is ItemStackFactory.ItemStackImpl)
        val itemStack: ItemStack = stack.stack
        context.drawItem(itemStack, x, y, seed)

        // draws the item count + durability bar
        val textRenderer = MinecraftClient.getInstance().textRenderer
        context.drawItemInSlot(textRenderer, stack.stack, x, y)
    }

    override fun drawTooltip(text: List<IText>, x: Int, y: Int) {
        val textRenderer = MinecraftClient.getInstance().textRenderer
        val textList = text.map { it.value }.toMutableList()
        context.drawTooltip(textRenderer, textList, x, y)
    }

    override fun drawDynamicTexture(texture: INativeImage, x: Int, y: Int, width: Int, height: Int) {
        require(texture is NativeImageImpl)
        context.drawTexture(texture.textureId, x, y, 0, 0f, 0f, width, height, texture.width, texture.height)
    }

    override fun drawGuiTexture(
        texture: Identifier,
        x: Int,
        y: Int,
        z: Int,
        u: Float,
        v: Float,
        width: Int,
        height: Int,
        textureWidth: Int,
        textureHeight: Int
    ) {
        context.drawTexture(
            with(texture) { Identifier(namespace, "textures/gui/sprites/$path.png") },
            x, y, z,
            u, v,
            width, height,
            textureWidth, textureHeight
        )
    }

}