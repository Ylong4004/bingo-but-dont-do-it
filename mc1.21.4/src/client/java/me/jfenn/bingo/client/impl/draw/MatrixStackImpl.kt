package me.jfenn.bingo.client.impl.draw

import me.jfenn.bingo.client.platform.renderer.IMatrixStack
import net.minecraft.client.util.math.MatrixStack
import org.joml.AxisAngle4f
import org.joml.Quaternionf

class MatrixStackImpl(
    private val matrices: MatrixStack,
) : IMatrixStack {
    override fun translate(x: Float, y: Float, z: Float) = matrices.translate(x, y, z)
    override fun scale(x: Float, y: Float, z: Float) = matrices.scale(x, y, z)
    override fun rotate(angle: Float) = matrices.multiply(Quaternionf(AxisAngle4f(angle, 0f, 0f, 1f)))
    override fun push() = matrices.push()
    override fun pop() = matrices.pop()
}