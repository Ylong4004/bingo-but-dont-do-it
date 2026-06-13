package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.data.ObjectiveRequirements
import me.jfenn.bingo.common.card.filter.ObjectiveFilterPreset
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import net.minecraft.util.Formatting
import org.slf4j.Logger

class BingoObjectiveManager(
    private val providers: List<IObjectiveManager>,
    private val text: TextProvider,
    private val log: Logger,
) : IObjectiveManager {
    override fun list(): Iterable<String> {
        return providers.flatMap { it.list() }
    }

    override fun listExcludedIds(): Iterable<String> = sequence {
        for (provider in providers) {
            yieldAll(provider.listExcludedIds())
        }
    }.asIterable()

    private fun checkHardRequirements(
        reqs: ObjectiveRequirements,
    ): Boolean {
        val hasAllObjectives = reqs.objectives.all {
            find(it, CardGeneratorState.DEFAULT) != null
        }

        return hasAllObjectives
    }

    private fun findFromProviders(type: String?, identifier: String, state: CardGeneratorState): BingoObjective? {
        if (type == "advancement") {
            return providers.find { it is AdvancementObjectiveManager }
                ?.find(identifier, state)
        }

        if (type == "item") {
            return providers.find { it is ItemObjectiveManager }
                ?.find(identifier, state)
        }

        for (provider in providers) {
            val goal = provider.find(identifier, state) ?: continue
            return goal
        }

        return null
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective? {
        // remove an "advancement!" or "item!" prefix from the id
        val type = id.substringBefore('!')
        val identifier = id.substringAfter('!')

        return findFromProviders(type, identifier, state)
            ?.takeIf { objective ->
                objective.data?.validate
                    ?.let { checkHardRequirements(it) }
                    ?: true
            }
    }

    override fun init(card: BingoCard) {
        // Initialize objectives
        if (!card.isInitialized) {
            // No, this isn't a bug...
            // init() needs to be called multiple times to populate display info on dependent objectives
            initInternal(card)
            initInternal(card)
            initInternal(card)

            card.isInitialized = true
        }
    }

    private fun initInternal(card: BingoCard) {
        for (provider in providers)
            provider.init(card)

        for (entry in card.entries) {
            val objective = card.objectives[entry.objectiveId]

            if (objective == null) {
                log.error("[BingoObjectiveManager] Objective data for '${entry.objectiveId}' is missing!")
                continue
            }

            val display = when {
                objective is BingoObjective.FreeSpace -> ObjectiveDisplay.Resolved(
                    name = text.string(StringKey.CardFreeSpace)
                )
                else -> objective.display
            }

            val lore = buildList {
                display.lore?.let { addAll(it) }

                val desc = listOfNotNull(
                    entry.tier?.let { text.string(it.string).formatted(it.formatting) },
                    entry.source?.let { ObjectiveFilterPreset.formatName(text, it).formatted(Formatting.BLUE) },
                    entry.tileName?.let { text.literal(it).formatted(Formatting.GRAY) },
                )

                desc.takeIf { it.isNotEmpty() }
                    ?.let { text.joinText(it, text.literal(" • ").formatted(Formatting.GRAY)) }
                    ?.let { add(it) }
            }.takeIf { it.isNotEmpty() }

            val item = display.item?.copy()?.also { stack ->
                stack.setDisplay(
                    name = display.name
                        ?.takeIf { it != stack.displayName }
                        ?.let { text.empty().append(it).resetStyle() },
                    lore = lore
                        ?.map { text.empty().append(it).resetStyle() },
                )
                stack.setHideFlags(255)
            }

            entry.display = display.copy(lore = lore, item = item)
        }
    }

    override fun tick(card: BingoCard) {
        for (provider in providers) {
            provider.tick(card)
        }
    }
}