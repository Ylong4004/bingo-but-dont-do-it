package me.jfenn.bingo.client.impl.draw

import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.texture.AbstractTexture

class FramebufferTexture(private val fb: Framebuffer) : AbstractTexture() {
    override fun getGlId(): Int {
        return fb.colorAttachment
    }

    override fun clearGlId() {
        // no-op
    }

    override fun close() {
        // no-op
    }
}