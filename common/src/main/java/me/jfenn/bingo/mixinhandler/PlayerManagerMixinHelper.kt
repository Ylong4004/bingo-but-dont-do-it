package me.jfenn.bingo.mixinhandler

import me.jfenn.bingo.common.LOBBY_WORLD_ID
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.spawn.SpawnData
import me.jfenn.bingo.common.spawn.SpawnService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStopped
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

class PlayerManagerMixinHelper internal constructor(
    private val config: BingoConfig,
    private val state: BingoState,
    private val spawnService: SpawnService,
    private val teamService: TeamService,
    private val playerManager: IPlayerManager,
    eventBus: IEventBus,
) {

    fun shouldPreventLobbyChaos() = config.preventLobbyChaos && state.state == GameState.PREGAME

    fun shouldSpawnInLobby() = state.isLobbyMode && (state.state == GameState.PREGAME || state.state == GameState.PRELOADING)

    fun getPregameSpawnData(): SpawnData {
        return SpawnData(
            dimension = LOBBY_WORLD_ID.toString(),
            position = state.lobbySpawnPos,
            yaw = state.lobbySpawnYaw,
        )
    }

    fun shouldOverrideEndRespawn(): Boolean {
        val dimension = spawnService.getSpawnDimension()
        return state.isLobbyMode && dimension.identifier.substringAfter(':') == "the_end"
    }

    fun setPlayerSpawnpoint(serverPlayerEntity: ServerPlayerEntity) {
        if (!state.isLobbyMode) {
            // if isLobbyMode=false, mixins should never overwrite the player spawnpoint
            return
        }

        // find the player's bingo team spawnpoint, if possible
        val player = playerManager.forPlayer(serverPlayerEntity)
        val team: BingoTeam = teamService.getPlayerTeam(player) ?: return
        val spawn: BlockPos = spawnService.getTeamSpawnpoint(team)?.toBlockPos() ?: return

        // change the player's spawnpoint back to the team spawn location
        val dimension = spawnService.getSpawnDimension()
        player.setSpawnPoint(
            world = dimension,
            spawn = BlockPosition.fromBlockPos(spawn),
            angle = 0f,
            forced = true,
            sendMessage = false,
        )
    }

    init {
        instance = this
        eventBus.register(ScopeStopped) {
            instance = null
        }
    }

    companion object {
        var instance: PlayerManagerMixinHelper? = null

        fun exists() = instance != null

        fun shouldPreventLobbyChaos() = instance?.shouldPreventLobbyChaos() ?: false

        fun shouldSpawnInLobby() = instance?.shouldSpawnInLobby() ?: false

        fun getPregameSpawnData() = instance?.getPregameSpawnData()

        fun shouldOverrideEndRespawn() = instance?.shouldOverrideEndRespawn() ?: false

        fun setPlayerSpawnpoint(player: ServerPlayerEntity) {
            instance?.setPlayerSpawnpoint(player)
        }
    }
}