package me.jfenn.bingo.impl

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.impl.block.BlockStateImpl
import me.jfenn.bingo.impl.world.ChunkImpl
import me.jfenn.bingo.mixin.LevelPropertiesAccessor
import me.jfenn.bingo.mixin.ServerChunkLoadingManagerAccessor
import me.jfenn.bingo.mixin.ServerChunkManagerAccessor
import me.jfenn.bingo.mixinhelper.ServerChunkManagerMixinHelper
import me.jfenn.bingo.platform.IRegistryEntry
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.IWorldBorder
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IBlockState
import me.jfenn.bingo.platform.world.IChunk
import net.minecraft.entity.boss.dragon.EnderDragonFight
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ChunkTicketType
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraft.world.border.WorldBorder
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.chunk.ChunkStatus
import net.minecraft.world.level.LevelProperties
import org.slf4j.Logger
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture

internal object ChunkTickets {
    lateinit var TICKET_HELD: ChunkTicketType
    lateinit var TICKET_ASYNC: ChunkTicketType

    fun init() {
        TICKET_HELD = Registry.register(Registries.TICKET_TYPE, "$MOD_ID_BINGO-held", ChunkTicketType(0, ChunkTicketType.SERIALIZE or ChunkTicketType.FOR_LOADING))
        TICKET_ASYNC = Registry.register(Registries.TICKET_TYPE, "$MOD_ID_BINGO-async", ChunkTicketType(0, ChunkTicketType.SERIALIZE or ChunkTicketType.FOR_LOADING))
    }
}

class ServerWorldFactory(
    private val log: Logger,
    private val server: MinecraftServer,
) : IServerWorldFactory {
    override val overworld: IServerWorld
        get() = forWorld(server.overworld)

    override fun forWorld(world: ServerWorld): IServerWorld = ServerWorldImpl(world)
    override fun listWorlds(): List<IServerWorld> {
        return server.worlds.map { forWorld(it) }
    }

    private fun tickKeepAlive() {
        server.networkIo?.tick()
    }

    override fun recreateWorlds(
        seed: Long,
        callback: () -> Unit
    ) {
        log.debug("[Reset] Started recreateWorlds")
        tickKeepAlive()

        // Remove scoreboard objectives to avoid a crash when data is reloaded in loadWorld()
        server.scoreboard.objectives
            .toList()
            .forEach {
                try {
                    server.scoreboard.removeObjective(it)
                } catch (e: Throwable) {
                    log.error("[Reset] Error removing objective:", e)
                }
            }

        // Clear any ongoing raids
        server.worlds.forEach { world ->
            world.raidManager.raidManagerAccessor.raids.values
                .forEach { it.invalidate() }
        }

        server.accessor.setSaving(true)
        try {
            // First, ensure the world/data is saved
            log.measureTime("[Reset] Saving...") {
                // MinecraftServer::saveAll
                server.playerManager.saveAllPlayerData()

                for (world in listWorlds()) {
                    // ServerWorld::save -> ServerWorld::saveLevel
                    val serverWorld: ServerWorld = world.world
                    serverWorld.persistentStateManager.save()
                }
            }

            tickKeepAlive()

            server.threadExecutorAccessor.invokeCancelTasks()

            // Use mixins to skip save() calls within world.close(), so that it
            // *only* closes resources without waiting to write to disk
            ServerChunkManagerMixinHelper.shouldCancelSaving = true

            for (world in listWorlds()) {
                log.measureTime("[Reset] Closing ${world.identifier}...") {
                    world.close()
                }

                tickKeepAlive()

                arrayOf("region", "poi", "entities")
                    .map { world.directory.resolve(it).toFile() }
                    .filter { it.exists() }
                    .onEach { it.deleteRecursively() }

                tickKeepAlive()
            }

            callback()
            tickKeepAlive()

            log.measureTime("[Reset] Loading new worlds...") {
                val levelPropertiesAccessor = (server.saveProperties as LevelProperties) as LevelPropertiesAccessor
                levelPropertiesAccessor.setDragonFight(EnderDragonFight.Data.DEFAULT)
                levelPropertiesAccessor.generatorOptions = levelPropertiesAccessor.generatorOptions.withSeed(OptionalLong.of(seed))
                server.accessor.invokeLoadWorld()
            }
            tickKeepAlive()

            // invoke "#minecraft:load" again, as scoreboards will be reset
            with(server.commandFunctionManager) {
                getTag(Identifier.of("minecraft:load"))
                    .forEach { execute(it, scheduledCommandSource) }
            }
        } finally {
            server.accessor.setSaving(false)
            ServerChunkManagerMixinHelper.shouldCancelSaving = false
        }
    }
}

class ServerWorldImpl(
    override val world: ServerWorld
): IServerWorld {

    private val serverOrThrow get() = requireNotNull(world.server) { "ServerWorldImpl: world.server reference is null!" }

    override val identifier: String
        get() = world.registryKey.value.toString()

    override val directory: Path
        get() = serverOrThrow.accessor.session.getWorldDirectory(world.registryKey)

    override val worldBorder: IWorldBorder
        get() = WorldBorderImpl(world.worldBorder)
    override val coordinateScale: Double
        get() = world.dimension.coordinateScale
    override val logicalHeight: Int
        get() = world.logicalHeight
    override val bottomY: Int
        get() = world.bottomY
    override val seaLevel: Int
        get() = world.seaLevel
    override val spawnPos: BlockPosition
        get() = BlockPosition.fromBlockPos(world.spawnPoint.pos)
    override val hasCeiling: Boolean
        get() = world.dimension.hasCeiling
    override val isOverworld: Boolean
        get() = world.registryKey == World.OVERWORLD

    override var timeOfDay by world::timeOfDay

    override fun getBlockState(pos: BlockPosition): IBlockState {
        return world.getBlockState(pos.toBlockPos())
            .let { BlockStateImpl.fromBlockState(it) }
    }

    override fun getBiome(pos: BlockPosition): IRegistryEntry.Biome {
        return BiomeRegistryEntry(world.getBiome(pos.toBlockPos()))
    }

    private val taskExecutor = Executors.createServerTaskExecutor(serverOrThrow)

    override fun addTicket(chunk: Pair<Int, Int>): IServerWorld.IChunkTicketHandle {
        world.chunkManager.addTicket(ChunkTickets.TICKET_HELD, ChunkPos(chunk.first, chunk.second), 0)
        return ChunkTicketHandle(chunk)
    }

    inner class ChunkTicketHandle(
        private val chunk: Pair<Int, Int>
    ) : IServerWorld.IChunkTicketHandle {
        override fun close() {
            world.chunkManager.removeTicket(ChunkTickets.TICKET_HELD, ChunkPos(chunk.first, chunk.second), 0)
        }
    }

    override fun getChunkSync(chunk: Pair<Int, Int>): IChunk {
        return ChunkImpl(world.getChunk(chunk.first, chunk.second))
    }

    override fun getChunkAsync(chunk: Pair<Int, Int>): CompletableFuture<IChunk?> {
        if (!serverOrThrow.isOnThread) {
            return CompletableFuture.supplyAsync({ getChunkAsync(chunk) }, serverOrThrow)
                .thenCompose({ it })
        }

        // Add a ticket for the chunk
        val chunkManager = world.chunkManager
        chunkManager.addTicket(ChunkTickets.TICKET_ASYNC, ChunkPos(chunk.first, chunk.second), 0)

        (chunkManager as ServerChunkManagerAccessor).invokeUpdateChunks()

        val chunkLoadingManager = world.chunkManager.chunkLoadingManager
        val chunkHolder = (chunkLoadingManager as ServerChunkLoadingManagerAccessor)
            .invokeGetChunkHolder(ChunkPos(chunk.first, chunk.second).toLong())

        val chunkFuture = chunkHolder?.load(ChunkStatus.FULL, chunkLoadingManager)
            ?.thenApply { it?.orElse(null) }
            ?: CompletableFuture.completedFuture<Chunk?>(null)

        // Remove the chunk ticket once loaded
        chunkFuture.whenCompleteAsync({ _, _ ->
            chunkManager.removeTicket(ChunkTickets.TICKET_ASYNC, ChunkPos(chunk.first, chunk.second), 0)
        }, taskExecutor)

        return chunkFuture.thenApply { if (it != null) ChunkImpl(it) else null }
    }

    override fun close() {
        world.close()
    }
}

class WorldBorderImpl(
    val worldBorder: WorldBorder
): IWorldBorder {
    override val centerX: Double by worldBorder::centerX
    override val centerZ: Double by worldBorder::centerZ
    override val maxRadius: Int by worldBorder::maxRadius
    override fun contains(blockPos: BlockPosition): Boolean = worldBorder.contains(blockPos.toBlockPos())
}
