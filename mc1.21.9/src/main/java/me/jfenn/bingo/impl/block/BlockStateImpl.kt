package me.jfenn.bingo.impl.block

import me.jfenn.bingo.impl.BlockRegistryEntry
import me.jfenn.bingo.platform.IRegistryEntry
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IBlockState
import net.minecraft.block.BlockState
import net.minecraft.block.FluidBlock
import net.minecraft.block.WallSignBlock
import net.minecraft.registry.Registries

open class BlockStateImpl(
    private val blockState: BlockState,
) : IBlockState {
    override val block: IRegistryEntry.Block get() = BlockRegistryEntry(
        Registries.BLOCK.getEntry(blockState.block)
    )

    override val identifier: String get() = Registries.BLOCK.getId(blockState.block).toString()

    override fun isEmpty(world: IServerWorld, pos: BlockPosition): Boolean {
        return blockState.isAir || blockState.getCollisionShape(world.world, pos.toBlockPos()).isEmpty
    }

    override val isFluid: Boolean
        get() = blockState.block is FluidBlock

    companion object {
        fun fromBlockState(blockState: BlockState): IBlockState {
            return when (blockState.block) {
                is WallSignBlock -> WallSignBlockStateImpl(blockState)
                else -> BlockStateImpl(blockState)
            }
        }
    }
}
