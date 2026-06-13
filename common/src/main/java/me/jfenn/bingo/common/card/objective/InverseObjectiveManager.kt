package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.rootObjectives
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import net.minecraft.text.Text
import java.time.Instant

internal class InverseObjectiveManager(
    private val state: BingoState,
    private val data: ScopedData,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val objectiveService: ObjectiveService,
    private val text: TextProvider,
) : IObjectiveManager {
    override fun list(): Iterable<String> {
        return data.objectives.rootObjectives
            .filter { (_, it) -> it is ObjectiveData.Inverse }
            .map { it.key }
            .toList()
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective? {
        val data = data.objectives[id] as? ObjectiveData.Inverse
            ?: return null

        return BingoObjective.InverseEntry(
            id = id,
            data = data,
            inverseObjective = data.expandReference(id, data.objective),
        )
    }

    override fun init(card: BingoCard) {
        for (objective in card.objectivesByInstance<BingoObjective.InverseEntry>()) {
            val display = card.objectives[objective.inverseObjective]?.let { inverse ->
                ObjectiveDisplay.Resolved(
                    name = text.string(StringKey.ObjectiveInverse, inverse.display.name ?: Text.empty()),
                    item = inverse.display.item,
                    decoration = CardTile.Decoration.FORBIDDEN,
                )
            } ?: ObjectiveDisplay.Resolved.EMPTY

            objective.display = objectiveDisplayService.resolve(objective.id, objective.data.display, display)
        }
    }

    override fun tick(card: BingoCard) {
        for (objective in card.objectivesByInstance<BingoObjective.InverseEntry>()) {
            val inverse = card.objectives[objective.inverseObjective] ?: continue

            objectiveService.updateTeamsOnceAchieved(objective)

            if (objective.permanent) {
                // once a player earns the inverse objective, they can never recapture this one
                objective.playersOnceAchieved.addAll(inverse.playersHolding.keys)
            }

            val teamsAchieved = objectiveService.getTeams(card)
                .filter { team ->
                    // A team should hold this tile as long as no players have achieved the objective
                    team.players.none { player ->
                        inverse.playersHolding.containsKey(player.uuid) ||
                                objective.playersOnceAchieved.contains(player.uuid)
                    }
                }

            val playersHolding = teamsAchieved.flatMap { team -> team.players.map { team to it } }
                .associateBy(
                    { (_, player) -> player.uuid },
                    { (team, player) ->
                        BingoObjectiveCapture(
                            team = team.key,
                            player = player,
                            instant = state.startedAt ?: Instant.MIN
                        )
                    }
                )

            objective.teamsSeen = inverse.teamsSeen

            val players = objective.playersHolding

            val teams = objective.playersHolding.map { it.value.team }.toMutableSet()

            objectiveService.update(
                objective = objective,
                playersHolding = playersHolding,
                players = players,
                teams = teams
            )
        }
    }
}