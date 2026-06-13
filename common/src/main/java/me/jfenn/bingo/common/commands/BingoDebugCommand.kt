package me.jfenn.bingo.common.commands

import kotlinx.serialization.encodeToString
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.json
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.slf4j.Logger

class BingoDebugCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
    private val log: Logger,
) : BingoComponent() {

    init {
        commandManager.register("bingo") {
            literal("debug") {
                requires {
                    hasPermission(Permission.COMMAND_DEBUG)
                }
                executes {
                    val state = scope.get<BingoState>()
                    val stateJson = json.encodeToString(state)
                    log.info(stateJson)

                    sendMessage(text.literal("Debug info printed to server logs.").formatted(Formatting.GRAY))
                }
            }
        }
    }
}