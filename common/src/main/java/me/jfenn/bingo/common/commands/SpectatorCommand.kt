package me.jfenn.bingo.common.commands

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.integrations.vanish.IVanishApi
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.ICommandManager
import net.minecraft.world.GameMode
import org.slf4j.Logger

class SpectatorCommand(
    commandManager: ICommandManager,
    private val log: Logger,
) {

    private fun toggleSpectator(state: BingoState, player: IPlayerHandle) {
        val gameMode = if (player.isSpectator) {
            // This needs to match the game mode logic in [PlayerController]
            when (state.state) {
                GameState.PLAYING -> GameMode.ADVENTURE
                GameState.POSTGAME -> GameMode.CREATIVE
                else -> {
                    log.error("[PlayerController] BUG: toggleSpectator was called in an unsupported GameState!")
                    return
                }
            }
        } else {
            GameMode.SPECTATOR
        }

        player.player.changeGameMode(gameMode)
    }

    init {
        // Switch between adventure and spectator modes
        commandManager.register("spectator") {
            requires {
                val isGameState = when {
                    // If vanish is installed, allow when spectating a game
                    scope.get<IVanishApi>().isInstalled() -> hasState(GameState.PLAYING, GameState.POSTGAME)
                    // Otherwise, only allow in postgame
                    else -> hasState(GameState.POSTGAME)
                }
                val isPermission = hasPermission(Permission.COMMAND_SPECTATOR)

                val state = scope.get<BingoState>()
                val teamService = scope.get<TeamService>()

                // only available to players if:
                // - the server is in lobby mode
                // - AND EITHER:
                //   * the player is not on a team (spectating)
                //   * the game state is POSTGAME
                val isAvailable = state.isLobbyMode && (!teamService.isPlaying(playerOrThrow) || state.state == GameState.POSTGAME)

                isGameState && isPermission && isAvailable
            }

            executes {
                toggleSpectator(scope.get(), playerOrThrow)
            }
        }
    }
}