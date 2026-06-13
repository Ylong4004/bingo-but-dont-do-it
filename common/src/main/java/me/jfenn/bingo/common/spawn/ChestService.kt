package me.jfenn.bingo.common.spawn

import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.platform.block.BlockPosition
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.Heightmap
import org.slf4j.Logger

internal class ChestService(
    private val log: Logger,
    private val server: MinecraftServer,
    private val options: BingoOptions,
    private val config: BingoConfig,
    private val state: BingoState,
    private val spawnKitService: SpawnKitService,
) {

    private fun getSpawnDimension(): ServerWorld {
        return server.worlds.find {
            it.registryKey.value.toString() == options.spawnDimension
        } ?: run {
            log.error("[SpawnService] Could not find spawnDimension '${options.spawnDimension}'; defaulting to minecraft:overworld")
            server.overworld
        }
    }

    fun createChestSpawnpoints() {
        if (!options.isTeamKit) return

        for (team in state.getRegisteredTeams()) {
            if (team.chestSpawnpoint == null) {
                createChestSpawnpoint(team)
            }
        }
    }

    private fun createChestSpawnpoint(team: BingoTeam): BlockPosition? {
        if (!options.isTeamKit) return null
        if (!state.isLobbyMode) return null
        val world = getSpawnDimension()
        val spawnpoint = team.spawnpoint ?: run {
            log.error("[ChestService] createChestSpawnpoint - Cannot create a team chest, as the team does not have a spawn point!")
            return null
        }

        val chunk = world.getChunk(spawnpoint.toBlockPos())
        // if the dimension has a ceiling, we can't use heightmaps...
        val chestBlockPos: BlockPos? = if (world.dimension.hasCeiling) {
            // spawn the chest directly next to the player
            BlockPos(spawnpoint.x + 1, spawnpoint.y, spawnpoint.z)
        } else {
            val heightmap = chunk.getHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES)

            // Choose a random position in the chunk to spawn the team chest at
            buildList {
                for (x in 0..15) {
                    for (z in 0..15) {
                        add(Pair(x, z))
                    }
                }
            }
                .shuffled()
                .asSequence()
                .map { (x, z) ->
                    val y = heightmap.get(x, z)
                    chunk.pos.getBlockPos(x, y, z)
                }
                .find {  blockPos ->
                    world.isAir(blockPos) || world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty
                }
        }

        if (chestBlockPos == null) {
            log.error("[ChestService] createChestSpawnpoint - No valid locations to create a team kit chest!")
            return null
        }

        val position = BlockPosition.fromBlockPos(chestBlockPos)
        team.chestSpawnpoint = position
        return position
    }

    fun createChestBlock(team: BingoTeam) {
        if (!options.isTeamKit) return
        if (!state.isLobbyMode) return
        val world = getSpawnDimension()
        val chestSpawnpoint = team.chestSpawnpoint ?: createChestSpawnpoint(team) ?: run {
            log.error("[ChestService] createChestBlock - Cannot create a team chest, as there is no chestSpawnpoint.")
            return
        }

        val chestBlockPos = chestSpawnpoint.toBlockPos()

        // Create the team chest block/entity at the chosen position
        world.setBlockState(chestBlockPos, Blocks.CHEST.defaultState)
        val entity = world.getBlockEntity(chestBlockPos) as? ChestBlockEntity ?: run {
            log.error("[SpawnService] createTeamChest - Creating a team kit chest did not create a block entity. This should never happen!!")
            return
        }

        // Place torches around the chest in each direction
        val torchState = Blocks.TORCH.defaultState
        Direction.Type.HORIZONTAL
            .map { direction -> chestBlockPos.offset(direction) }
            .filter { blockPos -> torchState.canPlaceAt(world, blockPos) }
            .forEach { blockPos -> world.setBlockState(blockPos, torchState) }

        // Fill the chest with the configured item stacks
        spawnKitService.getTeamItems()
            .forEachIndexed { i, itemStack ->
                if (config.preventScoringSpawnKitItems) {
                    // set BINGO_IGNORE flag so that spawn items do not count on the bingo card
                    itemStack.addCustomTag(NBT_BINGO_IGNORE)
                }

                entity.setStack(i, itemStack.stack)
            }
    }

}