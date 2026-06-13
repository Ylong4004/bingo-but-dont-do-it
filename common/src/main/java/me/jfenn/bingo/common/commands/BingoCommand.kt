package me.jfenn.bingo.common.commands

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.TextAction
import me.jfenn.bingo.common.URL_WIKI
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.BINGO_VERSION
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import net.minecraft.util.Formatting

class BingoCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
) : BingoComponent() {
    private fun IExecutionContext.getOptionsText(): List<IText> = buildList {
        add(text.empty())

        text.string(StringKey.FullName)
            .append(" v$BINGO_VERSION")
            .formatted(Formatting.DARK_GREEN, Formatting.UNDERLINE).also {
                it.setClickEvent(TextAction.OpenUrl(URL_WIKI))
            }
            .let { add(it) }

        add(text.empty())

        addAll(scope.get<OptionsService>().getOptionsSummary(player))

        add(text.empty())
    }

    init {
        commandManager.register("bingo") {
            executes {
                getOptionsText().forEach { sendMessage(it) }
            }
        }
    }
}