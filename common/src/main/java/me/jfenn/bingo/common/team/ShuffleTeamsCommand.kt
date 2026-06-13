package me.jfenn.bingo.common.team

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.commands.hasState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.ICommandManager

class ShuffleTeamsCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
) {

    init {
        commandManager.register("bingo") {
            literal("shuffleteams") {
                requires {
                    hasState(GameState.PREGAME) && hasPermission(Permission.CONFIGURE_GAME)
                }

                integer("number_of_teams", min = 1) { teamsArg ->
                    executes {
                        scope.get<TeamService>().shuffleTeams(getArgument(teamsArg))
                        sendFeedback(text.string(StringKey.CommandShuffleteamsSuccess))
                    }
                }
            }
        }
    }

    companion object {
        const val SHUFFLE_TEAMS_COMMAND ="/bingo shuffleteams"
    }
}