package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IBlockState
import me.jfenn.bingo.platform.world.IChunk
import net.minecraft.server.world.ServerWorld
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

interface IServerWorldFactory {
    val overworld: IServerWorld
    fun forWorld(world: ServerWorld): IServerWorld
    fun listWorlds(): List<IServerWorld>
    fun recreateWorlds(seed: Long, callback: () -> Unit)
}

interface IServerWorld {

    val identifier: String

    val directory: Path

    val world: ServerWorld

    val worldBorder: IWorldBorder
    val coordinateScale: Double
    val logicalHeight: Int
    val bottomY: Int
    val seaLevel: Int
    val spawnPos: BlockPosition
    val hasCeiling: Boolean
    val isOverworld: Boolean

    var timeOfDay: Long

    fun getBlockState(pos: BlockPosition): IBlockState
    fun getBiome(pos: BlockPosition): IRegistryEntry.Biome

    fun addTicket(chunk: Pair<Int, Int>): IChunkTicketHandle

    fun getChunkSync(chunk: Pair<Int, Int>): IChunk
    fun getChunkAsync(chunk: Pair<Int, Int>): CompletableFuture<IChunk?>

    fun close()

    interface IChunkTicketHandle : Closeable
}

interface IWorldBorder {
    val centerX: Double
    val centerZ: Double
    val maxRadius: Int
    fun contains(blockPos: BlockPosition): Boolean
}