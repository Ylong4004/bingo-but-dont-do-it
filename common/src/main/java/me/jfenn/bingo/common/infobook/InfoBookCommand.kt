package me.jfenn.bingo.common.infobook

import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.platform.commands.ICommandManager

class InfoBookCommand(
    commandManager: ICommandManager,
    private val config: BingoConfig,
) : BingoComponent() {
    init {
        commandManager.register("bingo") {
            literal("book") {
                requires {
                    val state = scope.get<BingoState>()
                    !config.lobbyTutorialBook || !state.isLobbyMode
                }
                executes {
                    val player = player ?: throw IllegalArgumentException("Could not resolve player")
                    val bookService = scope.get<InfoBookService>()
                    bookService.giveBookItem(player)
                }
            }
        }
    }
}