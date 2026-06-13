package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.systems.RenderSystem
import me.jfenn.bingo.client.mixinhelper.FramebufferOverride
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.IFramebuffer
import me.jfenn.bingo.client.platform.renderer.use
import me.jfenn.bingo.common.MOD_ID_BINGO
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.InGameHud
import net.minecraft.client.gui.render.state.GuiRenderState
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.util.Identifier
import java.util.*

class FramebufferImplStub(
    override val width: Int,
    override val height: Int
) : IFramebuffer {
    override fun register() {
    }

    override fun resize(width: Int, height: Int) {
    }

    override fun write(callback: (IDrawService) -> Unit) {
    }

    override fun draw(service: IDrawService, width: Int, height: Int) {
    }

    override fun close() {
    }
}