package me.jfenn.bingo.impl.block

import me.jfenn.bingo.platform.block.IWallSignBlockState
import net.minecraft.block.BlockState
import net.minecraft.block.WallSignBlock
import org.joml.Vector3i

class WallSignBlockStateImpl(
    private val blockState: BlockState,
) : BlockStateImpl(blockState), IWallSignBlockState {
    override val facing: Vector3i
        get() = blockState.get(WallSignBlock.FACING)
            ?.vector
            ?.let { Vector3i(it.x, it.y, it.z) }
            ?: Vector3i(1, 0, 0)
}