package me.jfenn.bingo.common.game

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.formatWarning
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.commands.hasState
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext

class GameCommands(
    commandManager: ICommandManager,
    private val text: TextProvider,
) : BingoComponent() {

    private fun IExecutionContext.start(ignoreWarnings: Boolean) {
        val gameService = scope.get<GameService>()

        scope.get<IExecutors.IServerTaskExecutor>().execute {
            // Start the game!
            val warnings = mutableListOf<IText>()
            gameService.start(
                warnings = warnings,
                ignoreWarnings = ignoreWarnings,
                allowSpectators = ignoreWarnings,
            )

            warnings.forEachIndexed { i, warning ->
                sendMessage(text.formatWarning(warning, i == 0))
            }
        }

        sendFeedback(text.string(StringKey.CommandStartSuccess))
    }

    init {
        commandManager.register("bingo") {
            literal("start") {
                requires {
                    hasState(GameState.PREGAME) && hasPermission(Permission.CONFIGURE_GAME)
                }

                executes { start(ignoreWarnings = false) }

                literal("ignore_warnings") {
                    executes { start(ignoreWarnings = true) }
                }
            }

            literal("end") {
                requires {
                    hasState(GameState.PLAYING) && hasPermission(Permission.CONFIGURE_GAME)
                }

                executes {
                    val gameService = scope.get<GameService>()
                    val state = scope.get<BingoState>()
                    state.isForfeit = true

                    val player = player
                    val playerName = when {
                        player != null -> player.playerName
                        isConsole -> "RCON"
                        else -> "[unknown]"
                    }

                    gameService.end(GameEndReason.Command(playerName))
                    sendFeedback(text.string(StringKey.CommandEndSuccess))
                }
            }

            literal("resume") {
                requires {
                    val resumeService = scope.get<GameResumeService>()
                    resumeService.isResumeAvailable(player)
                }
                executes {
                    val resumeService = scope.get<GameResumeService>()
                    resumeService.resume()
                    sendFeedback(text.string(StringKey.CommandResumeSuccess))
                }
            }
        }
    }

    companion object {
        const val START_COMMAND = "/bingo start"
        const val IGNORE_WARNINGS_COMMAND = "/bingo start ignore_warnings"
        const val END_COMMAND = "/bingo end"
        const val RESUME_COMMAND = "/bingo resume"
    }
}
