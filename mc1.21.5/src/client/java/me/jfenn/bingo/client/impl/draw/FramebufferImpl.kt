package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.systems.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import me.jfenn.bingo.client.impl.accessor
import me.jfenn.bingo.client.mixinhelper.FramebufferOverride
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.IFramebuffer
import me.jfenn.bingo.common.MOD_ID_BINGO
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.client.render.RenderLayer
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
        fb.resize(width, height)
    }

    private var backupProjMat = Matrix4f()
    private var backupProjType = ProjectionType.ORTHOGRAPHIC

    override fun write(callback: (IDrawService) -> Unit) {
        val context: DrawContext = service.context
        context.draw()

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            fb.colorAttachment,
            0,
            fb.depthAttachment,
            1.0
        )

        val right = (fb.textureWidth.toDouble() / client.window.scaleFactor).toFloat()
        val bottom = (fb.textureHeight.toDouble() / client.window.scaleFactor).toFloat()
        val matrix4f = Matrix4f().setOrtho(
            0f,
            right,
            bottom,
            0f,
            1000f,
            21000f
        )

        backupProjMat = RenderSystem.getProjectionMatrix()
        backupProjType = RenderSystem.getProjectionType()
        RenderSystem.setProjectionMatrix(matrix4f, ProjectionType.ORTHOGRAPHIC)

        RenderSystem.getModelViewStack()
            .pushMatrix()
            .translation(0f, 0f, -11000.0f)

        DiffuseLighting.enableGuiDepthLighting()

        FramebufferOverride.framebuffer = fb

        service.matrices.push()

        callback(service)

        context.draw()

        service.matrices.pop()

        FramebufferOverride.framebuffer = null

        RenderSystem.getModelViewStack().popMatrix()
        RenderSystem.setProjectionMatrix(backupProjMat, backupProjType)
    }

    override fun draw(service: IDrawService, width: Int, height: Int) {
        val context: DrawContext = service.context
        context.matrices.push()

        val scaleFactor = fb.textureWidth.toFloat() / width
        context.matrices.scale(1f/scaleFactor, 1f/scaleFactor, 1f)

        val matrix4f = context.matrices.peek().positionMatrix
        val vertexConsumer = context.accessor.vertexConsumers.getBuffer(RenderLayer.getGuiTextured(fbTextureId))

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
        val color = -1

        vertexConsumer.vertex(matrix4f, x1, y1, 0.0f).texture(u1, v1).color(color)
        vertexConsumer.vertex(matrix4f, x1, y2, 0.0f).texture(u1, v2).color(color)
        vertexConsumer.vertex(matrix4f, x2, y2, 0.0f).texture(u2, v2).color(color)
        vertexConsumer.vertex(matrix4f, x2, y1, 0.0f).texture(u2, v1).color(color)

        context.matrices.pop()
        context.draw()
    }

    override fun close() {
        fb.delete()
        client.textureManager.destroyTexture(fbTextureId)
    }
}