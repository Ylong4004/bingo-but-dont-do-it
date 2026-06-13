package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.rootObjectives
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import net.minecraft.text.Text

internal class OpponentObjectiveManager(
    private val data: ScopedData,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val objectiveService: ObjectiveService,
    private val text: TextProvider,
) : IObjectiveManager {
    override fun list(): Iterable<String> {
        return data.objectives.rootObjectives
            .filter { (_, it) -> it is ObjectiveData.Opponent }
            .map { it.key }
            .toList()
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective? {
        val data = data.objectives[id] as? ObjectiveData.Opponent
            ?: return null

        return BingoObjective.OpponentEntry(
            id = id,
            data = data,
            opponentObjective = data.expandReference(id, data.objective),
        )
    }

    override fun init(card: BingoCard) {
        for (objective in card.objectivesByInstance<BingoObjective.OpponentEntry>()) {
            val display = card.objectives[objective.opponentObjective]?.let { opponent ->
                ObjectiveDisplay.Resolved(
                    name = text.string(StringKey.ObjectiveOpponent, opponent.display.name ?: Text.empty()),
                    item = opponent.display.item,
                    decoration = CardTile.Decoration.ADVANCEMENT,
                )
            } ?: ObjectiveDisplay.Resolved.EMPTY

            objective.display = objectiveDisplayService.resolve(objective.id, objective.data.display, display)
        }
    }

    override fun tick(card: BingoCard) {
        for (objective in card.objectivesByInstance<BingoObjective.OpponentEntry>()) {
            val opponent = card.objectives[objective.opponentObjective] ?: continue

            objectiveService.updateTeamsOnceAchieved(objective)

            objective.teamsSeen = opponent.teamsSeen

            if (objective.permanent) {
                // if permanent, only add new captures into opponentsAchieved (never remove)
                opponent.playersHolding.forEach { (uuid, capture) ->
                    objective.opponentsAchieved.putIfAbsent(uuid, capture)
                }
            } else {
                objective.opponentsAchieved = opponent.playersHolding
            }

            val opponentCaptures = objective.opponentsAchieved.values
                // oldest capture first
                .sortedBy { it.instant }
                // distinctBy keeps the first of each team; i.e. the oldest capture from each team
                .distinctBy { it.team }
                .let { captures ->
                    when {
                        // if in lockout mode, only the oldest capture is used
                        card.options.isLockoutMode -> captures.take(1)
                        else -> captures
                    }
                }

            val teams = objectiveService.getAllTeams()
                .filter { team -> opponentCaptures.any { it.team != team.key } }
                .map { it.key }
                .toSet()

            objectiveService.update(
                objective = objective,
                teams = teams
            )
        }
    }
}
