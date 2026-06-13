package me.jfenn.bingo.common.lobby

import me.jfenn.bingo.common.commands.runByName
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.commands.IExecutionSource
import me.jfenn.bingo.platform.dialog.IDialogAction
import me.jfenn.bingo.platform.dialog.IDialogManager
import java.util.*

internal class BingoLobbyCommand(
    commandManager: ICommandManager,
    environment: IModEnvironment,
    config: BingoConfig,
    private val text: TextProvider,
) {
    private val IExecutionSource.lobbyModeService get() = scope.get<LobbyModeService>()

    private val emptyUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val IExecutionSource.executorUuid get() = when {
        isConsole -> emptyUuid
        else -> playerOrThrow.uuid
    }

    private fun IExecutionContext.createLobbyDialog(player: IPlayerHandle): Boolean {
        val dialogManager = scope.get<IDialogManager>()
        val builder = dialogManager.confirmationBuilder()
            ?: return false

        builder.title = text.string(StringKey.FullName)
        lobbyModeService.getWarnings().forEach { builder.addText(it) }

        builder.setYes(
            text.translatable("gui.yes", "Yes"),
            IDialogAction.RunCommand(CONFIRM_COMMAND)
        )
        builder.setNo(
            text.translatable("gui.no", "No"),
            IDialogAction.None
        )

        dialogManager.showDialog(player, builder.build())
        return true
    }

    init {
        if (environment.envType == IModEnvironment.EnvType.SERVER && !config.server.isLobbyMode) {
            commandManager.register("bingo") {
                literal("lobby") {
                    requires {
                        isConsole || lobbyModeService.canUseLobbyCommand(playerOrThrow)
                    }
                    executes {
                        val isDialog = player?.let { createLobbyDialog(it) }
                        if (isDialog != true) {
                            lobbyModeService.getCommandWarnings()
                                .forEach { sendMessage(it) }
                        }
                        lobbyModeService.onPlayerWarned(executorUuid)
                    }

                    literal("confirm") {
                        executes {
                            lobbyModeService
                                .acceptWarnings(executorUuid, runByName)
                                .forEach { sendMessage(it) }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val LOBBY_COMMAND = "/bingo lobby"
        const val CONFIRM_COMMAND = "/bingo lobby confirm"
    }
}