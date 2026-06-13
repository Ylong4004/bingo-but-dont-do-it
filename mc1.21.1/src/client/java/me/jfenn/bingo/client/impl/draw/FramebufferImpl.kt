package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.VertexSorter
import me.jfenn.bingo.client.mixinhelper.FramebufferOverride
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.IFramebuffer
import me.jfenn.bingo.common.MOD_ID_BINGO
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.*
import net.minecraft.util.Identifier
import org.joml.Matrix4f
import java.util.*

class FramebufferImpl(
    private val fb: SimpleFramebuffer,
    private val service: DrawService,
) : IFramebuffer {

    private val client = MinecraftClient.getInstance()
    private val fbTexture = FramebufferTexture(fb)
    private val fbTextureId = Identifier.of(MOD_ID_BINGO, "framebuffer/" + UUID.randomUUID().toString())

    override val width: Int
        get() = fb.textureWidth

    override val height: Int
        get() = fb.textureHeight

    override fun register() {
        client.textureManager.registerTexture(fbTextureId, fbTexture)
    }

    override fun resize(width: Int, height: Int) {
        fb.resize(width, height, MinecraftClient.IS_SYSTEM_MAC)
    }

    private var backupProjMat = Matrix4f()
    private var backupVertexSorter = VertexSorter.BY_Z

    override fun write(callback: (IDrawService) -> Unit) {
        val context: DrawContext = service.context
        context.draw()

        fb.setClearColor(0f, 0f, 0f, 0f)
        fb.clear(MinecraftClient.IS_SYSTEM_MAC)

        val window = MinecraftClient.getInstance().window

        val right = (fb.textureWidth.toDouble() / window.scaleFactor).toFloat()
        val bottom = (fb.textureHeight.toDouble() / window.scaleFactor).toFloat()
        val matrix4f = Matrix4f().setOrtho(
            0f,
            right,
            bottom,
            0f,
            1000f,
            21000f
        )

        backupProjMat = RenderSystem.getProjectionMatrix()
        backupVertexSorter = RenderSystem.getVertexSorting()
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorter.BY_Z)

        RenderSystem.getModelViewStack()
            .pushMatrix()
            .translation(0f, 0f, -11000.0f)

        DiffuseLighting.enableGuiDepthLighting()

        fb.beginWrite(true)
        FramebufferOverride.framebuffer = fb

        service.matrices.push()

        callback(service)

        context.draw()

        fb.endWrite()

        service.matrices.pop()

        FramebufferOverride.framebuffer = null
        client.framebuffer.beginWrite(true)

        RenderSystem.getModelViewStack().popMatrix()
        RenderSystem.setProjectionMatrix(backupProjMat, backupVertexSorter)
    }

    override fun draw(service: IDrawService, width: Int, height: Int) {
        val context: DrawContext = service.context
        context.matrices.push()

        val scaleFactor = fb.textureWidth.toFloat() / width
        context.matrices.scale(1f/scaleFactor, 1f/scaleFactor, 1f)

        RenderSystem.setShaderTexture(0, fbTextureId)
        RenderSystem.setShader { GameRenderer.getPositionTexProgram() }

        val matrix4f = context.matrices.peek().positionMatrix
        val bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE)

        // The framebuffer renders upside down, so we need to flip v1 and v2 here
        // otherwise, this could just be a context.drawTexture call...
        val x1 = 0f
        val x2 = width * scaleFactor
        val y1 = 0f
        val y2 = height * scaleFactor
        val u1 = 0f
        val u2 = 1f
        val v1 = 1f
        val v2 = 0f

        bufferBuilder.vertex(matrix4f, x1, y1, 0.0f).texture(u1, v1)
        bufferBuilder.vertex(matrix4f, x1, y2, 0.0f).texture(u1, v2)
        bufferBuilder.vertex(matrix4f, x2, y2, 0.0f).texture(u2, v2)
        bufferBuilder.vertex(matrix4f, x2, y1, 0.0f).texture(u2, v1)

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end())

        context.matrices.pop()
    }

    override fun close() {
        fb.delete()
        client.textureManager.destroyTexture(fbTextureId)
    }
}