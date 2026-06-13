package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.rootObjectives
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay.Companion.FORMAT_MAX
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay.Companion.FORMAT_MIN
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatTitle
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.scoreboard.IObjectiveHandle
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import org.slf4j.Logger
import java.time.Instant

internal class ScoreboardObjectiveManager(
    private val log: Logger,
    private val scoreboardManager: IScoreboardManager,
    private val state: BingoState,
    private val data: ScopedData,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val objectiveService: ObjectiveService,
    private val text: TextProvider,
) : IObjectiveManager {
    override fun list(): Iterable<String> {
        return data.objectives.rootObjectives
            .filter { (_, it) -> it is ObjectiveData.Scoreboard }
            .map { it.key }
            .toList()
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective? {
        val data = data.objectives[id] as? ObjectiveData.Scoreboard
            ?: return null

        return BingoObjective.ScoreboardEntry(
            id = id,
            data = data,
            valueMin = data.min.resolve(state),
            valueMax = data.max.resolve(state),
        ).takeIf { init(it) }
    }

    private fun init(objective: BingoObjective): Boolean {
        if (objective !is BingoObjective.ScoreboardEntry) return false

        val scoreboard = scoreboardManager.getByName(objective.data.scoreboardName)

        if (scoreboard == null) {
            log.objectiveError(objective.id, "Could not find scoreboard ${objective.data.scoreboardName} - is it not created yet?")
            return false
        }

        objective.scoreboard = scoreboard

        val display = ObjectiveDisplay.Resolved(
            name = text.literal(objective.data.scoreboardName.formatTitle())
                .append(": $FORMAT_MIN"),
            decoration = CardTile.Decoration.ADVANCEMENT,
        )
        objective.display = objectiveDisplayService.resolve(
            id = objective.id,
            data = objective.data.display,
            fallback = display,
            substitutions = mapOf(
                FORMAT_MIN to objective.valueMin.toString(),
                FORMAT_MAX to objective.valueMax.toString(),
            )
        )

        return true
    }

    override fun init(card: BingoCard) {
        for (goal in card.objectivesByInstance<BingoObjective.ScoreboardEntry>()) {
            init(goal)
        }
    }

    private fun getScoreboardValue(objective: BingoObjective.ScoreboardEntry, scoreboard: IObjectiveHandle, player: IPlayerHandle): Int {
        val base = when {
            objective.data.relative -> {
                objective.baseScores.getOrPut(player.uuid) { scoreboard.getForPlayer(player) ?: 0 }
            }
            else -> 0
        }

        val score = scoreboard.getForPlayer(player) ?: 0
        return score - base
    }

    override fun tick(card: BingoCard) {
        val now = state.updatedAt ?: Instant.MIN

        for (objective in card.objectivesByInstance<BingoObjective.ScoreboardEntry>()) {
            val scoreboard = objective.scoreboard ?: continue

            objectiveService.updateTeamsOnceAchieved(objective)

            val teamPlayers = objectiveService.getPlayersByTeam(card)
                .mapKeys { it.key.key }

            val teamsProgress = teamPlayers.map { (team, players) ->
                val scoreboardProgress = players.maxOf { getScoreboardValue(objective, scoreboard, it) }
                    .takeIf { it <= objective.valueMax }
                    ?: 0
                val progress = scoreboardProgress / objective.valueMin.coerceAtLeast(1).toFloat()
                team to progress
            }.toMap()

            objectiveService.updateTeamsSeen(card, objective) { team ->
                teamPlayers[team]
                    ?.any { getScoreboardValue(objective, scoreboard, it) > 0 }
                    ?: false
            }

            val playersHolding = teamPlayers.asSequence()
                .flatMap { (team, players) -> players.map { team to it } } // List<(TeamKey, Player)>
                .filter { (_, player) -> getScoreboardValue(objective, scoreboard, player) in objective.valueRange }
                .associate { (team, player) ->
                    val capture = objective.playersHolding[player.uuid] ?: BingoObjectiveCapture(
                        team = team,
                        player = player.profile,
                        instant = now,
                    )
                    player.uuid to capture
                }
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
                shouldRetainCaptures = false,
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