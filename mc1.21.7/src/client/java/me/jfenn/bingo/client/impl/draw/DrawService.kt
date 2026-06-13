package me.jfenn.bingo.client.impl.draw

import me.jfenn.bingo.client.impl.NativeImageImpl
import me.jfenn.bingo.client.impl.accessor
import me.jfenn.bingo.client.platform.INativeImage
import me.jfenn.bingo.client.platform.renderer.*
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.impl.ItemStackFactory
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import org.joml.Vector2f
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

        override val scaleFactor: Double get() = MinecraftClient.getInstance().window.scaleFactor.toDouble()
        override val scaledWindowWidth: Int
            get() = MinecraftClient.getInstance().window.scaledWidth
        override val scaledWindowHeight: Int
            get() = MinecraftClient.getInstance().window.scaledHeight

        override fun create(drawContext: DrawContext): IDrawService = DrawService(drawContext)

        /*private val bufferBuilderStorage by lazy {
            BufferBuilderStorage(Runtime.getRuntime().availableProcessors())
        }*/

        /*private val guiRenderState by lazy {
            GuiRenderState()
        }*/

        override val isBufferSupported: Boolean = false

        override fun newBuffer(width: Int, height: Int): IFramebuffer {
            /*val client = MinecraftClient.getInstance()
            val buffer = SimpleFramebuffer(null, width, height, true)
            val context = DrawContext(client, guiRenderState)

            return FramebufferImpl(
                buffer, DrawService(context), guiRenderState
            )*/
            return FramebufferImplStub(width, height)
        }
    }

    override fun draw() {}

    override fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        context.fill(x1, y1, x2, y2, color)
    }

    override fun overlayFill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        context.fill(x1, y1, x2, y2, color)
    }

    override fun drawHorizontalLine(x1: Int, x2: Int, y: Int, color: Int) {
        context.drawHorizontalLine(x1, x2, y, color)
    }

    override fun drawVerticalLine(x: Int, y1: Int, y2: Int, color: Int) {
        context.drawVerticalLine(x, y1, y2, color)
    }

    override fun enableBlend() {}

    override fun disableBlend() {}

    override fun setShaderColor(r: Float, g: Float, b: Float, a: Float) {
        // context.draw()
        // TODO: RenderSystem.setShaderColor(r, g, b, a)
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
        context.drawStackOverlay(textRenderer, stack.stack, x, y)
    }

    override fun drawTooltip(text: List<IText>, x: Int, y: Int) {
        val textRenderer = MinecraftClient.getInstance().textRenderer
        val textList = text.map { it.value }.toMutableList()
        val realPosition = context.matrices.transformPosition(x.toFloat(), y.toFloat(), Vector2f())
        context.drawTooltip(textRenderer, textList, realPosition.x.toInt(), realPosition.y.toInt())
    }

    override fun drawTooltipAddon(callback: () -> Unit) {
        val tooltipRunnable = context.accessor.tooltipDrawer
        context.accessor.tooltipDrawer = Runnable {
            tooltipRunnable?.run()
            callback()
        }
    }

    override fun drawTooltipImmediate() {
        context.renderTooltip()
    }

    override fun drawDynamicTexture(texture: INativeImage, x: Int, y: Int, width: Int, height: Int) {
        require(texture is NativeImageImpl)
        context.accessor.invokeDrawTexturedQuad(
            RenderPipelines.GUI_TEXTURED,
            texture.glTextureView,
            x, y,
            x + width, y + height,
            0f, 1f,
            0f, 1f,
            -1
        )
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
            RenderPipelines.GUI_TEXTURED,
            texture.let { Identifier.of(it.namespace, "textures/gui/sprites/${it.path}.png")!! },
            x, y,
            u, v,
            width, height,
            textureWidth, textureHeight,
        )
    }

}