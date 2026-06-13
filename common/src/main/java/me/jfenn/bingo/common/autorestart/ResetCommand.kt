package me.jfenn.bingo.common.autorestart

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasLobby
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.commands.hasState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IExecutors
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext

class ResetCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
) {
    fun IExecutionContext.reset() {
        val resetService = scope.get<ResetService>()
        val serverTaskExecutor = scope.get<IExecutors.IServerTaskExecutor>()
        sendFeedback(text.string(StringKey.CommandResetSuccess))
        serverTaskExecutor.execute {
            resetService.resetGame()
        }
    }

    init {
        commandManager.register("bingo") {
            literal("reset") {
                requires { hasPermission(Permission.COMMAND_RESET) && hasState(GameState.PLAYING, GameState.POSTGAME) && hasLobby() }
                executes {
                    reset()
                }
            }
        }
    }
}