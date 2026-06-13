package me.jfenn.bingo.common.spawn

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.world.IChunk
import me.jfenn.bingo.platform.world.IChunkHeightmap
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

class SpreadPlayers(
    private val log: Logger,
    private val taskExecutor: IExecutors.IServerTaskExecutor,
    private val world: IServerWorld,
    private val invalidSpawnBiomes: ITagContents<IRegistryEntry.Biome>,
    private val invalidSpawnBlocks: ITagContents<IRegistryEntry.Block>,
) {

    class Factory(
        private val log: Logger,
        private val taskExecutor: IExecutors.IServerTaskExecutor,
        private val tagAccessor: ITagAccessor,
    ) {
        fun forWorld(world: IServerWorld) = SpreadPlayers(
            log = log,
            taskExecutor = taskExecutor,
            world = world,
            invalidSpawnBiomes = tagAccessor.getBiomeTag("$MOD_ID_BINGO:invalid_spawn"),
            invalidSpawnBlocks = tagAccessor.getBlockTag("$MOD_ID_BINGO:invalid_spawn"),
        )
    }

    init {
        require(invalidSpawnBlocks.list().isNotEmpty()) { "invalid_spawn block tag failed to load" }
        require(invalidSpawnBiomes.list().isNotEmpty()) { "invalid_spawn biome tag failed to load" }
    }

    // Clamp spawn positions to the overworld border
    // - yes, even if the spawn dimension isn't in the overworld...
    private val worldBorder = world.worldBorder
    private val coordinateScale = world.coordinateScale
    private val worldBorderCenterInWorld = BlockPosition((worldBorder.centerX / coordinateScale).toInt(), 0, (worldBorder.centerZ / coordinateScale).toInt())

    private val logicalHeight = world.logicalHeight
    private val bottomY = world.bottomY
    private val seaLevel = world.seaLevel
    private val worldSpawnPos = world.spawnPos

    init {
        log.info("[SpreadPlayers] Initialized in dimension ${world.identifier} with coordinateScale=$coordinateScale, logicalHeight=$logicalHeight, bottomY=$bottomY, seaLevel=$seaLevel")
    }

    private fun spreadGroups(count: Int, distance: Int): List<Pair<Int, Int>> {
        val center = worldBorderCenterInWorld.toChunkPos()
        val radius = ((distance * count) / (2 * Math.PI))
            .coerceAtMost((worldBorder.maxRadius / 16).toDouble() - 16.0)
            .coerceAtLeast(0.0)

        val theta = 2 * Math.PI * Math.random()

        return buildList {
            for (i in 0 until count) {
                val t0 = theta + (i * 2 * Math.PI / count)
                val x = sin(t0) * radius
                val z = cos(t0) * radius

                add(Pair(center.first + x.toInt(), center.second + z.toInt()))
            }
        }
    }

    private fun isValidSpawn(chunk: IChunk, groundPos: BlockPosition): Boolean {
        // scale the groundPos to match overworld positions for the worldBorder
        val groundPosInOverworld = BlockPosition(
            groundPos.x.times(coordinateScale).toInt(),
            groundPos.y,
            groundPos.z.times(coordinateScale).toInt(),
        )
        if (!worldBorder.contains(groundPosInOverworld)) {
            log.debug("Skipping block {} to avoid spawning outside the world border", groundPos)
            return false
        }

        if (world.isOverworld && groundPos.y <= seaLevel) {
            log.debug("Skipping block {} to avoid spawning underwater (y <= {})", groundPos, seaLevel)
            return false
        }

        val groundState = chunk.getBlockState(groundPos)
        if (groundState.isEmpty(world, groundPos))
            return false
        if (groundState.isFluid || invalidSpawnBlocks.contains(groundState.block)) {
            log.debug("Skipping block {} to avoid spawning on {}", groundPos, groundState)
            return false
        }

        for (y in 1..2) {
            val blockPos = groundPos.move(y = y)
            val blockState = chunk.getBlockState(blockPos)
            if (!blockState.isEmpty(world, blockPos) || blockState.isFluid) {
                log.debug("Skipping block {} to avoid spawning inside {}", groundPos, blockState)
                return false
            }
        }

        return true
    }
    private fun everyChunkIndex(margin: Int) = sequence {
        for (x in margin until 16 - margin) {
            for (z in margin until 16 - margin) {
                yield(x + z * 16)
            }
        }
    }

    private fun adjacentBlocks(pos: BlockPosition, range: Int) = sequence {
        for (x in -range..range) {
            for (z in -range..range) {
                if (x == 0 && z == 0) continue
                yield(BlockPosition(pos.x + x, pos.y, pos.z + z))
            }
        }
    }

    private fun selectChunkPosition(chunk: IChunk): BlockPosition? {
        // if world.dimension.hasCeiling, this needs to find a spawnpoint without using heightmaps
        // otherwise, choosing "the_nether" will spawn players on the nether roof!

        if (world.hasCeiling) {
            // choose random positions in the chunk until a valid one is found
            val spawnBlockPos = everyChunkIndex(2)
                .shuffled()
                .map {
                    chunk.getBlockPos(it % 16, logicalHeight - 2, it / 16)
                }
                .map { immutablePos ->
                    var pos = immutablePos
                    // iterate until these match (false, false, true)
                    // i.e. a solid block underneath two air blocks
                    var isEmptyAbove = false
                    var isEmpty = false
                    var isEmptyBelow = false
                    while (pos.y > bottomY && pos.y > seaLevel && !(isEmptyAbove && isEmpty && !isEmptyBelow)) {
                        isEmptyAbove = isEmpty
                        isEmpty = isEmptyBelow

                        pos = pos.move(y = -1)
                        isEmptyBelow = chunk.getBlockState(pos).isEmpty(world, pos)
                    }

                    if (!(isEmptyAbove && isEmpty && !isEmptyBelow))
                        return@map null

                    if (!isValidSpawn(chunk, pos))
                        return@map null

                    // ensure that there is space on each side of the spawn
                    val isSpace = adjacentBlocks(pos, 2)
                        .all { isValidSpawn(chunk, it) }

                    if (!isSpace)
                        return@map null

                    pos
                }
                .find { it != null }

            return spawnBlockPos
        } else {
            // otherwise, use heightmaps to spawn players near the world surface
            // this hopefully finds a reasonable actual spawn location without placing the player in a cave or on a tree
            val heightmap = chunk.getHeightmap(IChunkHeightmap.Type.MOTION_BLOCKING_NO_LEAVES)

            return everyChunkIndex(1)
                .shuffled()
                .map {
                    val x = it % 16
                    val z = it / 16
                    val height = heightmap.get(x, z)
                    chunk.getBlockPos(x, height - 1, z)
                }
                .filter {
                    // if the block is below sea level, skip it!
                    !(world.isOverworld && it.y <= seaLevel)
                }
                .find { pos ->
                    isValidSpawn(chunk, pos) && adjacentBlocks(pos, 1).all { isValidSpawn(chunk, it) }
                }
        }
    }

    sealed class Result<T> {
        data class NextChunk<T>(val nextChunk: Pair<Int, Int>) : Result<T>()
        data class Finish<T>(val block: BlockPosition) : Result<T>()
        data class Success<T>(val result: T) : Result<T>()
    }

    private fun processChunkCoords(chunk: Pair<Int, Int>): Result<Unit> {
        var (x, z) = chunk
        val xSign = x.sign.takeIf { it != 0 } ?: 1

        // test the rough chunk biome first, to make sure this isn't an ocean before generating the chunk
        val chunkCenter = BlockPosition(x*16 + 8, logicalHeight, z*16 + 8)
        val biomeTest = world.getBiome(chunkCenter)
        if (invalidSpawnBiomes.contains(biomeTest)) {
            x += 4 * xSign
            return Result.NextChunk(Pair(x, z))
        }

        // Scale the chunkCenter to match overworld positions for the worldBorder
        val chunkCenterInOverworld = BlockPosition(
            chunkCenter.x.times(coordinateScale).toInt(),
            chunkCenter.y,
            chunkCenter.z.times(coordinateScale).toInt(),
        )
        if (!worldBorder.contains(chunkCenterInOverworld)) {
            log.warn("Selected chunk is outside of the world border - attempting to find any valid position...")
            worldBorderCenterInWorld.toChunkPos()
            val centerChunkPos = worldBorderCenterInWorld.toChunkPos()
            val centerChunk = world.getChunkSync(centerChunkPos)
            val centerPos = selectChunkPosition(centerChunk)
            if (centerPos != null) {
                return Result.Finish(centerPos)
            }

            log.error("Could not find any valid spawn location due to the world border. Using the default world spawn, which may be buggy...")
            return Result.Finish(worldSpawnPos)
        }

        return Result.Success(Unit)
    }

    private fun processChunk(chunkPos: Pair<Int, Int>, chunk: IChunk): Result<BlockPosition> {
        var (x, z) = chunkPos
        val xSign = x.sign.takeIf { it != 0 } ?: 1

        val spawnBlockPos = selectChunkPosition(chunk)
        if (spawnBlockPos == null) {
            log.info("Skipping chunk ($x, $z) which contains no valid spawn locations")
            x += 1 * xSign
            return Result.NextChunk(Pair(x, z))
        }

        if (invalidSpawnBiomes.contains(world.getBiome(spawnBlockPos))) {
            log.info("Skipping chunk ($x, $z) for invalid spawn block biome")
            x += 4 * xSign
            return Result.NextChunk(Pair(x, z))
        }

        return Result.Success(BlockPosition(spawnBlockPos.x, spawnBlockPos.y + 1, spawnBlockPos.z))
    }

    /**
     * Searches for a valid spawn position, starting at the provided chunk and iterating
     * outwards on the X axis.
     */
    fun findSpawnPosAsync(startChunk: Pair<Int, Int>): CompletableFuture<BlockPosition> {
        var chunkPos = startChunk

        while (true) {
            when (val result = processChunkCoords(chunkPos)) {
                is Result.Success -> {}
                is Result.NextChunk -> {
                    chunkPos = result.nextChunk
                    continue
                }
                is Result.Finish -> {
                    return CompletableFuture.completedFuture(result.block)
                }
            }

            return world.getChunkAsync(startChunk).thenComposeAsync({ chunk ->
                if (chunk == null) {
                    log.error("Could not find any valid spawn location because getChunk returned null. Using the default world spawn, which may be buggy...")
                    return@thenComposeAsync CompletableFuture.completedFuture(worldSpawnPos)
                }

                when (val result = processChunk(chunkPos, chunk)) {
                    is Result.NextChunk -> findSpawnPosAsync(result.nextChunk)
                    is Result.Finish -> CompletableFuture.completedFuture(result.block)
                    is Result.Success -> CompletableFuture.completedFuture(result.result)
                }
            }, taskExecutor)
        }
    }

    /**
     * Searches for a valid spawn position, starting at the provided chunk and iterating
     * outwards on the X axis.
     */
    fun findSpawnPos(startChunk: Pair<Int, Int>): BlockPosition {
        var chunkPos = startChunk

        while (true) {
            when (val result = processChunkCoords(chunkPos)) {
                is Result.Success -> {}
                is Result.NextChunk -> {
                    chunkPos = result.nextChunk
                    continue
                }
                is Result.Finish -> {
                    return result.block
                }
            }

            val chunk = world.getChunkSync(chunkPos)

            return when (val result = processChunk(chunkPos, chunk)) {
                is Result.NextChunk -> findSpawnPos(result.nextChunk)
                is Result.Finish -> result.block
                is Result.Success -> result.result
            }
        }
    }

    fun spreadAsync(
        teams: List<BingoTeam>,
        distance: Int
    ) : CompletableFuture<Map<BingoTeamKey, BlockPosition>> {
        val groups = spreadGroups(teams.size, distance)

        val positions = teams.indices.map { i ->
            val team = teams[i]
            val startChunk = groups[i]

            log.info("Spawning team ${team.id} near chunk $startChunk")

            val future = findSpawnPosAsync(startChunk)
                // if this takes an entire minute, something is wrong
                .orTimeout(60L, TimeUnit.SECONDS)
                .exceptionally { e ->
                    log.error("findSpawnPosAsync for ${team.id} failed:", e)
                    // fall back to the world's spawnPos - this isn't ideal, but it should work in most dimensions
                    // (except the nether)
                    worldSpawnPos
                }
                .whenComplete { pos, _ ->
                    log.info("Using spawn position $pos for ${team.id}")
                }

            team.spawnpointFuture = future
            future
        }.toTypedArray()

        return CompletableFuture.allOf(*positions)
            .thenApply {
                teams.indices.associate { i ->
                    val team = teams[i]
                    val position = positions[i]
                    team.key to (position.getNow(null) ?: world.spawnPos)
                }
            }
    }

}