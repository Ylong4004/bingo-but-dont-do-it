package me.jfenn.bingo.common.data

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.card.filter.ObjectiveFilterPreset
import me.jfenn.bingo.common.card.objective.ObjectiveListService
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.ICommandManager

class DataCommands(
    commandManager: ICommandManager,
    private val text: TextProvider,
) {
    private fun matchesWildcardString(pattern: String, value: String) = pattern.split("*")
        .joinToString(".*") { Regex.escape(it) }
        .let { Regex("^$it$") }
        .matches(value)

    init {
        commandManager.register("bingodata") {
            requires {
                hasPermission(Permission.COMMAND_DATA)
            }

            literal("tag") {
                literal("add") {
                    string(
                        name = "tag",
                        suggestions = { scope.get<ScopedData>().tags.keys },
                    ) { tagArg ->
                        string(
                            name = "objective",
                            suggestions = {
                                val tag = getArgument(tagArg)
                                val tagValues = scope.get<ScopedData>().tags[tag]?.values.orEmpty()
                                scope.get<ObjectiveListService>().getAllObjectives()
                                    .filterNot { tagValues.contains(it) }
                            },
                            greedy = true,
                        ) { objectiveArg ->
                            executes {
                                val tag = getArgument(tagArg)
                                val objectivePattern = getArgument(objectiveArg)
                                val matchedObjectives = scope.get<ObjectiveListService>().getAllObjectives()
                                    .filter { matchesWildcardString(objectivePattern, it) }

                                for (objective in matchedObjectives) {
                                    scope.get<DataCommandService>().addToTag(
                                        tagName = tag,
                                        objective = objective,
                                    )
                                }

                                sendFeedback(
                                    text.string(
                                        StringKey.CommandDataTagAddSuccess,
                                        matchedObjectives.toString(),
                                        ObjectiveFilterPreset.formatName(text, tag),
                                    )
                                )
                            }
                        }
                    }
                }

                literal("remove") {
                    string(
                        name = "tag",
                        suggestions = { scope.get<ScopedData>().tags.keys },
                    ) { tagArg ->
                        string(
                            name = "objective",
                            suggestions = {
                                val tag = getArgument(tagArg)
                                scope.get<ScopedData>().tags[tag]?.values.orEmpty()
                            },
                            greedy = true,
                        ) { objectiveArg ->
                            executes {
                                val tag = getArgument(tagArg)
                                val objectivePattern = getArgument(objectiveArg)
                                val matchedObjectives = scope.get<ScopedData>().tags[tag]?.values.orEmpty()
                                    .filter { matchesWildcardString(objectivePattern, it) }

                                for (objective in matchedObjectives) {
                                    scope.get<DataCommandService>().removeFromTag(
                                        tagName = tag,
                                        objective = objective,
                                    )
                                }

                                sendFeedback(
                                    text.string(
                                        StringKey.CommandDataTagRemoveSuccess,
                                        matchedObjectives.toString(),
                                        ObjectiveFilterPreset.formatName(text, tag),
                                    )
                                )
                            }
                        }
                    }
                }
            }

            literal("tierlist") {
                literal("add") {
                    string(
                        name = "list",
                        suggestions = { scope.get<ScopedData>().tierLists.keys },
                    ) { listArg ->
                        for (tier in TierLabel.entries + null) {
                            literal(tier?.name?.lowercase() ?: "uncategorized") {
                                string(
                                    name = "objective",
                                    suggestions = {
                                        val list = getArgument(listArg)
                                        val items = scope.get<ScopedData>().tierLists[list]
                                            ?.let {
                                                if (tier != null) it.getTier(tier)
                                                else it.values
                                            }
                                            .orEmpty()
                                            .map { it.item }
                                            .toSet()
                                        scope.get<ObjectiveListService>().getAllObjectives()
                                            .filterNot { items.contains(it) }
                                    },
                                    greedy = true,
                                ) { objectiveArg ->
                                    executes {
                                        val list = getArgument(listArg)
                                        val objectivePattern = getArgument(objectiveArg)
                                        val matchedObjectives = scope.get<ObjectiveListService>()
                                            .getAllObjectives()
                                            .filter { matchesWildcardString(objectivePattern, it) }

                                        for (objective in matchedObjectives) {
                                            scope.get<DataCommandService>().addToTierList(
                                                listName = list,
                                                objective = objective,
                                                tier = tier,
                                            )
                                        }

                                        sendFeedback(
                                            text.string(
                                                StringKey.CommandDataTierlistAddSuccess,
                                                matchedObjectives.toString(),
                                                tier?.text(text) ?: StringKey.ListUncategorized,
                                                ObjectiveFilterPreset.formatName(text, list),
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                literal("remove") {
                    string(
                        name = "list",
                        suggestions = { scope.get<ScopedData>().tierLists.keys },
                    ) { listArg ->
                        string(
                            name = "objective",
                            suggestions = {
                                val list = getArgument(listArg)
                                scope.get<ScopedData>().tierLists[list]?.allEntries().orEmpty()
                                    .map { it.item }
                            },
                            greedy = true,
                        ) { objectiveArg ->
                            executes {
                                val list = getArgument(listArg)
                                val objectivePattern = getArgument(objectiveArg)
                                val matchedObjectives = scope.get<ScopedData>().tierLists[list]?.allEntries().orEmpty()
                                    .map { it.item }
                                    .filter { matchesWildcardString(objectivePattern, it) }

                                for (objective in matchedObjectives) {
                                    scope.get<DataCommandService>().removeFromTierList(
                                        listName = list,
                                        objective = objective,
                                    )
                                }

                                sendFeedback(
                                    text.string(
                                        StringKey.CommandDataTierlistRemoveSuccess,
                                        matchedObjectives.toString(),
                                        ObjectiveFilterPreset.formatName(text, list),
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}