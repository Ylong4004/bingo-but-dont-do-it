package me.jfenn.bingo.impl.world

import me.jfenn.bingo.platform.world.IChunkHeightmap
import net.minecraft.world.Heightmap

class ChunkHeightmapImpl(
    private val heightmap: Heightmap,
): IChunkHeightmap {
    override fun get(x: Int, z: Int): Int {
        return heightmap.get(x, z)
    }
}
