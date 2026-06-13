package me.jfenn.bingo.impl.world

import me.jfenn.bingo.impl.block.BlockStateImpl
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IBlockState
import me.jfenn.bingo.platform.world.IChunk
import me.jfenn.bingo.platform.world.IChunkHeightmap
import net.minecraft.world.Heightmap
import net.minecraft.world.chunk.Chunk

class ChunkImpl(
    private val chunk: Chunk,
) : IChunk {
    override fun getBlockPos(
        offsetX: Int,
        y: Int,
        offsetZ: Int
    ): BlockPosition {
        return BlockPosition.fromBlockPos(
            chunk.pos.getBlockPos(offsetX, y, offsetZ)
        )
    }

    override fun getBlockState(pos: BlockPosition): IBlockState {
        return BlockStateImpl(chunk.getBlockState(pos.toBlockPos()))
    }

    override fun getHeightmap(type: IChunkHeightmap.Type): IChunkHeightmap {
        return ChunkHeightmapImpl(
            chunk.getHeightmap(
                when (type) {
                    IChunkHeightmap.Type.MOTION_BLOCKING_NO_LEAVES -> Heightmap.Type.MOTION_BLOCKING_NO_LEAVES
                }
            )
        )
    }
}
