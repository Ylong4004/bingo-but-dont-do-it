package me.jfenn.bingo.common.team

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.commands.hasState
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.spawn.ChestService
import me.jfenn.bingo.common.spawn.PlayerController
import me.jfenn.bingo.common.spawn.SpawnService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import net.minecraft.util.Formatting
import java.util.concurrent.CompletableFuture

class JoinCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
) : BingoComponent() {

    private fun IExecutionContext.joinTeam(
        player: IPlayerHandle,
        teamLabelStr: String,
    ) {
        if (teamLabelStr == SPECTATORS) {
            joinSpectators(player)
            return
        }

        val state = scope.get<BingoState>()
        val teamService = scope.get<TeamService>()
        val spawnService = scope.get<SpawnService>()
        val playerController = scope.get<PlayerController>()
        val chestService = scope.get<ChestService>()
        val taskExecutor = scope.get<IExecutors.IServerTaskExecutor>()
        val data = scope.get<ScopedData>()

        val (teamKey, teamPreset) = data.teamPresets.entries
            .find { it.key.label == teamLabelStr }
            ?: error(text.string(StringKey.CommandJoinDoesNotExist, teamLabelStr))

        teamService.getPlayerTeam(player)
            ?.takeIf { it.key == teamKey }
            ?.let { existingTeam ->
                error(text.string(StringKey.CommandJoinAlreadyOnTeam, existingTeam.getName(text)))
            }

        val team = state.registerTeam(BingoTeam.fromPreset(teamKey, teamPreset))

        // If a player tries to /join during a game, and doesn't have COMMAND_JOIN_PLAYER
        if (state.state != GameState.PREGAME && !hasPermission(Permission.COMMAND_JOIN_PLAYER)) {
            sendMessage(
                text.literal("⚠  ")
                    .append("Cannot join a team - The game has already started!")
                    .formatted(Formatting.YELLOW)
            )
            return
        }

        val createSpawnpointFuture = when (state.state) {
            GameState.PREGAME -> CompletableFuture.completedFuture(null)
            else -> spawnService.getTeamSpawnpointAsync(team)
        }

        createSpawnpointFuture.whenCompleteAsync({ _, _ ->
            teamService.joinTeam(player, team)

            if (state.state == GameState.PLAYING) {
                // if a player's team is changed in-game, move them to their new spawnpoint
                playerController.updateGameMode(player, forceReset = true)
            }

            if (team.chestSpawnpoint == null) {
                // if the team does not have a chest yet (i.e. was just created)
                chestService.createChestBlock(team)
            }
        }, taskExecutor)

        if (player != this.player) {
            sendFeedback(
                text.string(
                    StringKey.CommandJoinSuccess,
                    player.playerName,
                    team.getName(text),
                )
            )
        }
    }

    private fun IExecutionContext.joinSpectators(
        player: IPlayerHandle,
    ) {
        val state = scope.get<BingoState>()
        val teamService = scope.get<TeamService>()
        val playerController = scope.get<PlayerController>()

        teamService.joinSpectators(player)

        if (state.state == GameState.PLAYING) {
            // if a player moves to spectator while in-game, update their gamemode/spawnpoint
            playerController.updateGameMode(player, forceReset = true)
        }

        if (player != this.player) {
            sendFeedback(
                text.string(
                    StringKey.CommandJoinSpectators,
                    player.playerName,
                )
            )
        }
    }

    private fun IExecutionContext.listTeams(): List<String> {
        return scope.get<ScopedData>().teamPresets.keys.map { it.label } + SPECTATORS
    }

    init {
        commandManager.register("join") {
            string("team", { listTeams() }) { teamArg ->
                requires { hasPermission(Permission.COMMAND_JOIN) && hasState(GameState.PREGAME, GameState.PLAYING) }
                executes {
                    joinTeam(playerOrThrow, getArgument(teamArg))
                }

                player("player") { playerArg ->
                    requires { hasPermission(Permission.COMMAND_JOIN_PLAYER) && hasState(GameState.PREGAME, GameState.PLAYING) }
                    executes {
                        val player = getArgument(playerArg)
                        joinTeam(player, getArgument(teamArg))
                    }
                }
            }
        }
    }

    companion object {
        const val JOIN_COMMAND = "/join"
        const val SPECTATORS = "spectators"
    }
}