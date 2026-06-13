package me.jfenn.bingo.common.chat

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.platform.commands.ICommandManager

class ChatCommand(
    commandManager: ICommandManager,
    config: BingoConfig,
) {
    init {
        // send a message to all teams, outside of the team-local chat
        for (cmd in config.chat.globalCommandAliases) {
            commandManager.register(cmd) {
                signedMessage("message") { messageArg ->
                    executes {
                        val sender = playerOrThrow
                        getArgument(messageArg).thenAccept {
                            scope.get<ChatMessageService>().sendGlobalMessage(it, sender)
                        }
                    }
                }
            }
        }

        // send a message to the team-local chat
        for (cmd in config.chat.teamCommandAliases) {
            commandManager.register(cmd) {
                signedMessage("message") { messageArg ->
                    executes {
                        val sender = playerOrThrow
                        getArgument(messageArg).thenAccept {
                            scope.get<ChatMessageService>().sendTeamMessage(it, sender)
                        }
                    }
                }
            }
        }
    }
}