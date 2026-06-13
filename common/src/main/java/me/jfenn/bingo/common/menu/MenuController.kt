package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.LOBBY_WORLD_ID
import me.jfenn.bingo.common.Sounds
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.datapack.LobbyWorldService
import me.jfenn.bingo.common.event.InteractionEntityEvents
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.lobbyWorld
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.scope.ScopeManager
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.BingoTeamPreset
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.measureTime
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IWallSignBlockState
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.EntityLoadEvent
import me.jfenn.bingo.platform.event.model.TickEvent
import net.minecraft.block.Blocks
import net.minecraft.block.PlayerSkullBlock
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.chunk.ChunkStatus
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector4d
import org.slf4j.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import kotlin.math.atan2
import kotlin.math.roundToInt

internal class MenuController(
    events: ScopedEvents,
    eventBus: IEventBus,
    scopeManager: ScopeManager,
    private val log: Logger,
    private val server: MinecraftServer,
    private val entityManager: IEntityManager,
    private val interactionEntityEvents: InteractionEntityEvents,
    private val state: BingoState,
    private val data: ScopedData,
    private val teamService: TeamService,
    private val text: TextProvider,
    private val levelStorage: ILevelStorage,
    private val particleFactory: me.jfenn.bingo.platform.particle.IParticleFactory,
    private val serverWorldFactory: IServerWorldFactory,
    private val taskExecutor: IExecutors.IServerTaskExecutor,
    private val lobbyWorldService: LobbyWorldService,
) : BingoComponent() {

    private var menu: MenuInstance? = null
    private val koinScope = scopeManager.getScope(server)!!

    private val instanceTag = "bingo-${UUID.randomUUID()}"

    private object Cache {
        var lobbyWorldModified: Instant = Instant.MIN
        var spawnPosition: Pair<BlockPosition, Float>? = null
        var menuPosition: Matrix4d? = null
        var teamPositions: Map<BingoTeamKey, BlockPosition>? = null
    }

    init {
        if (state.isLobbyMode) {
            prepareLobbyFiles()
        }

        events.onEnter(GameState.PREGAME)  {
            // this *shouldn't* happen... just being safe to prevent double-entities
            if (menu != null) {
                log.error("BUG: GameState.PREGAME onEnter handler has been invoked already!")
                return@onEnter
            }

            if (!state.isLobbyMode) {
                log.info("[MenuController] Not spawning lobby, as isLobbyMode=false")
                return@onEnter
            }

            val lobbyWorld = server.lobbyWorld
            if (lobbyWorld == null) {
                log.error("[MenuController] Not spawning lobby, as the dimension does not exist")
                return@onEnter
            }

            val (lobbySpawnPos, lobbySpawnYaw) = getLobbySpawnPosition(lobbyWorld)
            state.lobbySpawnPos = lobbySpawnPos
            state.lobbySpawnYaw = lobbySpawnYaw

            val menuPos = getMenuPosition(lobbyWorld)
            if (menuPos != null) {
                log.info("[MenuController] Spawning lobby menu at ${menuPos.getTranslation(Vector3d())}")
                val menuInstance = MenuInstance(
                    log = log,
                    koinScope = koinScope,
                    world = lobbyWorld,
                    entityManager = entityManager,
                    interactionEntityEvents = interactionEntityEvents,
                    matrix = menuPos,
                    instanceTag = instanceTag,
                )
                menu = menuInstance
            } else {
                log.warn("[MenuController] Not spawning menu entities as the lobby has no menu position.")
            }

            spawnTeamEntities(lobbyWorld)
        }

        eventBus.register(EntityLoadEvent) {
            val lobbyWorld = server.lobbyWorld ?: return@register
            val bingoTag = it.entity.commandTags.find { it.startsWith("bingo-") }
                ?: return@register

            if (it.world == lobbyWorld && !it.entity.isPlayer && bingoTag != instanceTag) {
                log.warn("Discarding an existing lobby world entity: ${it.entity.type}")
                it.entity.discard()
            }
        }

        eventBus.register(TickEvent.Start) {
            menu?.tick()
        }

        events.onStateChange { (_, to) ->
            if (to != GameState.PREGAME) {
                menu?.cleanup()
                menu = null

                // discard team picker entities which aren't part of the menu
                val lobbyWorld = server.lobbyWorld ?: return@onStateChange
                lobbyWorld.iterateEntities()
                    .filter { !it.isPlayer && it.commandTags.contains(instanceTag) }
                    .forEach { it.discard() }
            }
        }

        events.onChangeOptions {
            menu?.markDirty()
        }

        events.onPlayerJoin {
            menu?.markDirty()
        }

        events.onPlayerDisconnect {
            menu?.markDirty()
        }
    }

    fun prepareLobbyFiles() = log.measureTime("[MenuController] Preparing the BINGO lobby...") {
        val storageDir = levelStorage.getLevelSaveDir(LOBBY_WORLD_ID)
            ?: run {
                log.error("Unable to find world storage dir for $LOBBY_WORLD_ID")
                return@measureTime
            }

        // If the lobby has been modified, clear cached positions
        val lobbyWorldModified = lobbyWorldService.readLastModified()
        if (lobbyWorldModified.isAfter(Cache.lobbyWorldModified)) {
            Cache.spawnPosition = null
            Cache.menuPosition = null
            Cache.teamPositions = null
            Cache.lobbyWorldModified = lobbyWorldModified
        }

        lobbyWorldService.openLobbyZip().use { zipStream ->
            while (true) {
                val entry = zipStream.nextEntry ?: break
                if (entry.isDirectory) continue

                val entryName = entry.name.substringAfter('/')

                if (
                    entryName.startsWith("entities/") ||
                    entryName.startsWith("region/")
                ) {
                    val outPath = storageDir.resolve(entryName)
                    if (!outPath.startsWith(storageDir)) {
                        log.error("[MenuController] Tried to create a file outside of the world location: $outPath")
                        continue
                    }

                    Files.createDirectories(outPath.parent)

                    log.debug("Copying file -> {}", outPath)
                    try {
                        Files.copy(zipStream, outPath, StandardCopyOption.REPLACE_EXISTING)
                    } catch (e: IOException) {
                        log.error("[MenuController] Error copying lobby file $entryName", e)
                    }
                }

                zipStream.closeEntry()
            }
        }
    }

    private fun everyChunkIndex(margin: Int = 0) = sequence {
        for (x in margin until 16 - margin) {
            for (z in margin until 16 - margin) {
                yield(x + z * 16)
            }
        }
    }

    private fun searchLobbyBlocks() = sequence {
        val chunkIndices = arrayOf(0, 1, -1, 2, -2)
        val bottomY = server.lobbyWorld?.bottomY ?: 0

        for (chunkX in chunkIndices) for (chunkZ in chunkIndices) {
            val chunk = server.lobbyWorld?.getChunk(chunkX, chunkZ, ChunkStatus.FULL)
                ?: continue
            val heightmap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE)

            for (i in everyChunkIndex()) {
                val x = i % 16
                val z = i / 16
                val height = heightmap.get(x, z) + 3

                for (y in (bottomY..height).reversed()) {
                    val blockPos = chunk.pos.getBlockPos(x, y, z)
                    yield(BlockPosition.fromBlockPos(blockPos))
                }
            }
        }
    }

    private fun getMenuPosition(lobbyWorld: ServerWorld): Matrix4d? = run {
        Cache.menuPosition?.let { return@run it }

        log.measureTime("[MenuController] Locating menu position...") {
            val lobbyWorldImpl = serverWorldFactory.forWorld(lobbyWorld)

            // Find a column of three vertical wall_sign blocks for the menu position
            for (pos in searchLobbyBlocks()) {
                val blocks = listOf(
                    pos.move(y = 1).toBlockPos(),
                    pos.toBlockPos(),
                    pos.move(y = -1).toBlockPos()
                ).associateWith {
                    lobbyWorldImpl.getBlockState(BlockPosition.fromBlockPos(it))
                            as? IWallSignBlockState
                }

                val isMenuPos = blocks.values.all {
                    it?.identifier == "minecraft:oak_wall_sign"
                }
                val centerState = blocks.values.first()

                if (isMenuPos && centerState != null) {
                    val directionVec = centerState.facing
                    val yaw = atan2(directionVec.x.toDouble(), directionVec.z.toDouble())

                    val matrix4d = Matrix4d()
                    matrix4d.translate(pos.toVector3f())
                    matrix4d.rotateY(yaw)

                    return@measureTime matrix4d
                }
            }

            null
        }
    }.also { menuPosition ->
        Cache.menuPosition = menuPosition
        log.info("Set menu position to ${menuPosition?.transform(Vector4d())}")

        if (menuPosition != null) {
            val blockPos = Vector4d()
                .also { menuPosition.transform(it) }
                .let { BlockPosition(it.x.roundToInt(), it.y.roundToInt(), it.z.roundToInt()) }

            taskExecutor.execute {
                // schedule the blockstate change, as the chunk might not be loaded yet
                for (y in blockPos.y-1..blockPos.y+1) {
                    lobbyWorld.setBlockState(
                        blockPos.copy(y = y).toBlockPos(),
                        Blocks.AIR.defaultState,
                    )
                }
            }
        }
    }

    private fun getLobbySpawnPosition(lobbyWorld: ServerWorld): Pair<BlockPosition, Float> = run {
        Cache.spawnPosition?.let { return@run it }

        log.measureTime("[MenuController] Locating spawn position...") {
            // If a player head is in the lobby structure, use it as the player spawn
            for (pos in searchLobbyBlocks()) {
                val blockState = lobbyWorld.getBlockState(pos.toBlockPos())
                val isPlayerHead = blockState.block == Blocks.PLAYER_HEAD
                if (isPlayerHead) {
                    val rotation = blockState.get(PlayerSkullBlock.ROTATION)
                    val yaw = (rotation - 8).toFloat() * 360f / 16f
                    return@measureTime Pair(pos, yaw)
                }
            }

            // otherwise, find the highest block at 0,0
            val chunk = lobbyWorld.getChunk(0, 0, ChunkStatus.FULL)
            val heightmap = chunk.getHeightmap(Heightmap.Type.MOTION_BLOCKING)
            val spawnY = heightmap.get(0, 0)

            BlockPosition(0, spawnY, 0) to 180f
        }
    }.also {
        Cache.spawnPosition = it
        log.info("Set lobby spawnpoint to $it")

        taskExecutor.execute {
            // schedule the blockstate change, as the chunk might not be loaded yet
            lobbyWorld.setBlockState(it.first.toBlockPos(), Blocks.AIR.defaultState)
        }
    }

    private fun spawnTeamEntities(lobbyWorld: ServerWorld) {
        val lobbyWorldImpl: IServerWorld = serverWorldFactory.forWorld(lobbyWorld)

        val teamPositions = Cache.teamPositions ?: log.measureTime("[MenuController] Locating team entities...") {
            val ret = mutableMapOf<BingoTeamKey, BlockPosition>()
            // Search for team join entities
            for ((x, y, z) in searchLobbyBlocks()) {
                val isLodestone = lobbyWorld.getBlockState(BlockPos(x, y-1, z)).block == Blocks.LODESTONE
                if (!isLodestone) continue

                val blockPos = BlockPosition(x, y, z)
                val blockState = lobbyWorldImpl.getBlockState(blockPos)

                val (teamId, _) = data.teamPresets.entries
                    .find { it.value.blockId == blockState.identifier }
                    ?: continue

                ret[teamId] = blockPos
            }

            ret
        }

        for ((teamKey, blockPos) in teamPositions) {
            val team = data.teamPresets[teamKey] ?: continue
            spawnTeamEntity(lobbyWorld, Vector3d(blockPos.x + 0.5, blockPos.y.toDouble(), blockPos.z + 0.5), teamKey, team, instanceTag)

            taskExecutor.execute {
                // schedule the blockstate change, as the chunk might not be loaded yet
                lobbyWorld.setBlockState(blockPos.toBlockPos(), Blocks.AIR.defaultState)
            }
        }
    }

    private fun spawnTeamEntity(
        lobbyWorld: ServerWorld,
        position: Vector3d,
        teamKey: BingoTeamKey,
        teamPreset: BingoTeamPreset,
        instanceTag: String,
    ) {
        entityManager.createEntity(EntityType.TEXT_DISPLAY, lobbyWorld)
            .apply {
                pos = position + Vector3d(0.0, 1.5, 0.0)
                value = BingoTeam.fromPreset(teamKey, teamPreset)
                    .getName(
                        textProvider = text,
                        symbol = true,
                    )
                billboard = ITextDisplayEntity.Billboard.VERTICAL
                alignment = ITextDisplayEntity.TextAlignment.CENTER
                background = 0
                shadow = true
            }
            .also { it.commandTags += instanceTag }
            .also { entityManager.spawnEntity(lobbyWorld, it) }

        entityManager.createEntity(EntityType.TEXT_DISPLAY, lobbyWorld)
            .apply {
                pos = position + Vector3d(0.0, 1.0, 0.0)
                value = text.string(StringKey.LobbyClickToJoin)
                billboard = ITextDisplayEntity.Billboard.VERTICAL
                alignment = ITextDisplayEntity.TextAlignment.CENTER
            }
            .also { it.commandTags += instanceTag }
            .also { entityManager.spawnEntity(lobbyWorld, it) }

        val interactionListenerEntity = entityManager.createEntity(EntityType.INTERACTION, lobbyWorld)
            .apply {
                pos = position + Vector3d(0.0, -1.0, 0.0)
                width = 2f
                height = 4f
            }
            .also { it.commandTags += instanceTag }
            .also { entityManager.spawnEntity(lobbyWorld, it) }

        interactionEntityEvents.onInteract(interactionListenerEntity) { player ->
            val team = BingoTeam.fromPreset(teamKey, teamPreset)

            particleFactory.createDustParticle(team.textColor.colorValue ?: 0, 1f)
                .spawn(player, position, 10, Vector3d(1.0), 0.2)

            Sounds.playTeamChanged(player)
            teamService.joinTeam(player, team)
        }
    }

}