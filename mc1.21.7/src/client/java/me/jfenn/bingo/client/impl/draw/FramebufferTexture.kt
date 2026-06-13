package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.texture.AbstractTexture

class FramebufferTexture(private val fb: SimpleFramebuffer) : AbstractTexture() {
    override fun getGlTexture(): GpuTexture {
        return fb.colorAttachment ?: throw IllegalStateException("Color attachment is null!")
    }

    override fun getGlTextureView(): GpuTextureView? {
        return fb.colorAttachmentView ?: throw IllegalStateException("Color attachment view is null!")
    }

    override fun setFilter(bilinear: Boolean, mipmap: Boolean) {
        // no-op
    }

    override fun close() {
        // no-op
    }
}