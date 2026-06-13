package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.TagExpansionService
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.rootObjectives
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay.Companion.FORMAT_MAX
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay.Companion.FORMAT_MIN
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import org.slf4j.Logger

internal class SomeOfObjectiveManager(
    private val log: Logger,
    private val data: ScopedData,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val objectiveService: ObjectiveService,
    private val tagExpansionService: TagExpansionService,
    private val text: TextProvider,
) : IObjectiveManager {
    override fun list(): Iterable<String> {
        return data.objectives.rootObjectives
            .filter { (_, it) -> it is ObjectiveData.SomeOfBase }
            .map { it.key }
            .toList()
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective? {
        val data = data.objectives[id]
        if (data !is ObjectiveData.SomeOfBase)
            return null

        val someOfObjectives = data.objectives
            .map { data.expandReference(id, it) }
            .let { tagExpansionService.expandItemTags(it) }

        if (someOfObjectives.isEmpty()) {
            log.error("[SomeOfObjectiveManager] $id 'objectives' cannot be empty!")
            return null
        }

        return BingoObjective.SomeOfEntry(
            id = id,
            data = data,
            someOfObjectives = someOfObjectives,
            valueMin = data.min.resolve(state).coerceIn(0, someOfObjectives.size),
            valueMax = data.max.resolve(state).coerceIn(0, someOfObjectives.size),
        )
    }

    override fun init(card: BingoCard) {
        for (objective in card.objectivesByInstance<BingoObjective.SomeOfEntry>()) {
            val objectives = objective.someOfObjectives.mapNotNull { card.objectives[it] }

            val isOneOf = objective.valueMin == 1 && objective.valueMax >= objective.someOfObjectives.size
            val isAllOf = objective.valueMin == objective.someOfObjectives.size
            val contentsName = text.empty().append(objectives.firstOrNull()?.display?.name ?: text.empty()).append(", …")
            val display = ObjectiveDisplay.Resolved(
                name = when {
                    isOneOf -> text.string(StringKey.ObjectiveOneOf, contentsName)
                    isAllOf -> text.string(StringKey.ObjectiveAllOf, contentsName)
                    else -> text.string(StringKey.ObjectiveSomeOf, FORMAT_MIN, contentsName)
                },
                item = objectives.firstNotNullOfOrNull { it.display.item },
                decoration = when {
                    isOneOf -> CardTile.Decoration.ONE_OF
                    else -> CardTile.Decoration.MANY_OF
                },
            )

            objective.display = objectiveDisplayService.resolve(
                id = objective.id,
                data = objective.data.display,
                fallback = display,
                substitutions = mapOf(
                    FORMAT_MIN to objective.valueMin.toString(),
                    FORMAT_MAX to objective.valueMax.toString(),
                ),
            )
        }
    }

    override fun tick(card: BingoCard) {
        for (objective in card.objectivesByInstance<BingoObjective.SomeOfEntry>()) {
            val objectives = objective.someOfObjectives.mapNotNull { card.objectives[it] }

            objectiveService.updateTeamsOnceAchieved(objective)

            objectiveService.updateTeamsSeen(card, objective) { team ->
                objectives.any { it.hasSeen(team) }
            }

            val capturesByPlayer = objectives
                .flatMap { it.playersHolding.entries }
                .groupBy { it.key }

            val progressTotal = objective.valueMin.coerceIn(1, objectives.size).toFloat()

            val teamsProgress = objectiveService.getPlayersByTeam(card)
                .mapKeys { it.key.key }
                .mapValues { (_, players) ->
                    val progress = players.maxOfOrNull { capturesByPlayer[it.uuid]?.size ?: 0 } ?: 0
                    progress / progressTotal
                }

            // ensure that the minimum value is at most the # of successfully loaded objectives
            val valueMin = objective.valueMin.coerceIn(0, objectives.size)
            val playersHolding = capturesByPlayer
                .filter { it.value.size in valueMin..objective.valueMax } // in order to capture, must be holding every objective
                .mapValues { (_, captures) -> captures.map { it.value }.minBy { it.instant } }
                .let {
                    when {
                        objective.data.permanent -> it + objective.playersHolding
                        else -> it
                    }
                }

            val players = objectiveService.getPlayers(
                card = card,
                playersHolding = playersHolding,
                players = objective.players,
            )

            val teams = objectiveService.getTeams(
                players = players
            )

            objectiveService.update(
                objective = objective,
                teamsProgress = teamsProgress,
                playersHolding = playersHolding,
                players = players,
                teams = teams
            )
        }
    }
}