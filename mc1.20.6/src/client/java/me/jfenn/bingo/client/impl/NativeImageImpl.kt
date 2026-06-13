package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.INativeImage
import me.jfenn.bingo.client.platform.INativeImageFactory
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.map.Color
import me.jfenn.bingo.common.map.rgba
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier

object NativeImageFactory : INativeImageFactory {
    private val textureManager get() = MinecraftClient.getInstance().textureManager
    private var id: Int = 0

    override fun create(width: Int, height: Int): INativeImage {
        val prefix = "$MOD_ID_BINGO/${id++}"
        val texture = NativeImageBackedTexture(width, height, true)
        val textureId = textureManager.registerDynamicTexture(prefix, texture)
        return NativeImageImpl(texture, textureId, width, height)
    }
}

class NativeImageImpl(
    private val texture: NativeImageBackedTexture,
    override val textureId: Identifier,
    override val width: Int,
    override val height: Int,
) : INativeImage {
    override fun setPixel(x: Int, y: Int, color: Int) {
        val (r, g, b, a) = Color.fromInt(color)
        val bgrInt = rgba(b, g, r, a)
        texture.image?.setColor(x, y, bgrInt)
    }

    override fun upload() {
        texture.upload()
    }

    override fun close() {
        texture.close()
    }
}
