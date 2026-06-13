package me.jfenn.bingo.platform.block

import me.jfenn.bingo.platform.IRegistryEntry
import me.jfenn.bingo.platform.IServerWorld
import org.joml.Vector3i

interface IBlockState {
    val identifier: String
    val block: IRegistryEntry.Block
    fun isEmpty(world: IServerWorld, pos: BlockPosition): Boolean
    val isFluid: Boolean
}

interface IWallSignBlockState: IBlockState {
    val facing: Vector3i
}
