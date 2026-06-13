package me.jfenn.bingo.platform.block

import net.minecraft.util.math.BlockPos
import org.joml.Vector3d
import org.joml.Vector3f

data class BlockPosition(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    companion object {

        fun fromBlockPos(pos: BlockPos) = BlockPosition(pos.x, pos.y, pos.z)

    }

    fun move(x: Int = 0, y: Int = 0, z: Int = 0): BlockPosition {
        return this.copy(
            x = this.x + x,
            y = this.y + y,
            z = this.z + z,
        )
    }

    fun up() = move(y = 1)

    fun toBlockPos() = BlockPos(x, y, z)

    fun toChunkPos() = Pair(x / 16, y / 16)

    fun toVector3d() = Vector3d(x.toDouble(), y.toDouble(), z.toDouble())

    fun toVector3f() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())

    override fun toString(): String {
        return "[$x, $y, $z]"
    }

}
