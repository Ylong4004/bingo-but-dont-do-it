package me.jfenn.bingo.common.card.filter

import me.jfenn.bingo.platform.text.TextAction
import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.card.tag.TagService
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.commands.hasState
import me.jfenn.bingo.common.event.model.OptionsChangedEvent
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.options.optionsContext
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class ObjectiveFilterCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
    private val eventBus: IEventBus,
) {
    private fun IExecutionContext.listTags(): List<String> {
        return scope.get<TagService>().getTags().keys.toList()
    }

    private fun IExecutionContext.listPresets(): List<String> {
        return scope.get<ObjectiveFilterService>().getPresetFilters().keys.toList()
    }

    private fun IExecutionContext.suggestFilterList(partial: String): List<String> {
        val parts = partial.split(' ')
        val existingTags = parts
            .dropLast(1)
            .map {
                it.removePrefix("-")
                    .removePrefix("+")
                    .substringBeforeLast('#')
            }
            .toSet()

        val substr = partial.substringBeforeLast(' ', "")
            .takeIf { it.isNotEmpty() }

        val lastPart = parts.lastOrNull().orEmpty()

        return listTags()
            .minus(existingTags)
            .flatMap {
                buildList {
                    add("+$it")
                    add("-$it")
                    if (lastPart.isNotEmpty() && (it.startsWith(lastPart) || lastPart.startsWith(it))) {
                        for (i in 1..25) {
                            add("$it#$i")
                        }
                    } else {
                        add(it)
                    }
                }
            }
            .map {
                substr?.plus(" $it") ?: it
            }
    }

    init {
        commandManager.register("bingo") {
            literal("filter") {
                requires {
                    hasState(GameState.PREGAME, GameState.PLAYING) && hasPermission(Permission.CONFIGURE_GAME)
                }

                string("filter", { listPresets() + suggestFilterList(it) }, true) { filterArg ->
                    executes {
                        val filterStr = getArgument(filterArg)
                        val presets = scope.get<ObjectiveFilterService>().getPresetFilters()
                        val list = presets[filterStr]?.value ?: ObjectiveFilterList.fromString(filterStr)

                        val tags = listTags().toSet()
                        for (tag in list) {
                            if (!tags.contains(tag.tag)) {
                                error(Text.literal("Tag '${tag.tag}' does not exist!"))
                            }
                        }

                        scope.get<OptionsService>().setCardFilter(
                            ctx = optionsContext,
                            filter = list,
                            includePresetDetails = true,
                        )
                        eventBus.emit(OptionsChangedEvent, Unit)

                        if (!list.contains(ObjectiveFilter.Exclude(ObjectiveFilter.UNOBTAINABLE))) {
                            sendMessage(text.string(StringKey.CommandFilterIncludesUnobtainable).formatted(Formatting.YELLOW))

                            val listWithUnobtainable = ObjectiveFilterList.fromString("-${ObjectiveFilter.UNOBTAINABLE} $list")
                            val command = filter(listWithUnobtainable.toString())
                            text.literal("  ")
                                .append(text.literal(command).formatted(Formatting.UNDERLINE))
                                .also {
                                    it.setClickEvent(TextAction.SuggestCommand(command))
                                }
                                .let { sendMessage(it) }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun filter(filter: String): String {
            return "/bingo filter $filter"
        }
    }
}