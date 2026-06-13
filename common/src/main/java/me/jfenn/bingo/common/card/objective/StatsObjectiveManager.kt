package me.jfenn.bingo.common.card.objective

import java.time.Instant
import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.rootObjectives
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay.Companion.FORMAT_MAX
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay.Companion.FORMAT_MIN
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.OfflinePlayerCache
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatTitle
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IStatHandle
import me.jfenn.bingo.platform.IStatManager
import org.slf4j.Logger

internal class StatsObjectiveManager(
    private val log: Logger,
    private val statManager: IStatManager,
    private val state: BingoState,
    private val offlinePlayerCache: OfflinePlayerCache,
    private val data: ScopedData,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val objectiveService: ObjectiveService,
    private val text: TextProvider,
) : IObjectiveManager {
    override fun list(): Iterable<String> {
        return data.objectives.rootObjectives
            .filter { (_, it) -> it is ObjectiveData.Stats }
            .map { it.key }
            .toList()
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective? {
        val data = data.objectives[id] as? ObjectiveData.Stats
            ?: return null

        return BingoObjective.StatsEntry(
            id = id,
            data = data,
            valueMin = data.min.resolve(state),
            valueMax = data.max.resolve(state),
        ).takeIf { init(it) }
    }

    private fun init(objective: BingoObjective): Boolean {
        if (objective !is BingoObjective.StatsEntry) return false

        val stat = statManager.getById(objective.data.statType, objective.data.statName)
            ?: return false

        objective.stat = stat

        val statNamespace = (objective.data.statName ?: objective.data.statType).substringBefore(':')
        val statTypePath = objective.data.statType.substringAfter(':')
        val statNamePath = objective.data.statName?.substringAfter(':')
        val statNameFallback = statTypePath.formatTitle() + (statNamePath?.let { " ${it.formatTitle()}" } ?: "")

        val statName = statNamePath
            // This translation key usually only works for 'minecraft:custom' stats
            ?.let { text.translatable("stat.$statNamespace.$it", statNameFallback) }
            ?: text.literal(statNameFallback)

        val display = ObjectiveDisplay.Resolved(
            name = statName.append(": $FORMAT_MIN"),
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
        for (goal in card.objectivesByInstance<BingoObjective.StatsEntry>()) {
            if (!init(goal)) {
                log.error("[StatsObjectiveManager] Unable to find statistic ${goal.data.statType}/${goal.data.statName} for ${goal.id}")
            }
        }
    }

    private fun getStatValue(objective: BingoObjective.StatsEntry, stat: IStatHandle, player: IPlayerHandle): Int {
        val base = when {
            objective.data.relative -> {
                objective.baseStats.getOrPut(player.uuid) { stat.getForPlayer(player) }
            }
            else -> 0
        }
        return stat.getForPlayer(player) - base
    }

    override fun tick(card: BingoCard) {
        val now = state.updatedAt ?: Instant.MIN

        for (objective in card.objectivesByInstance<BingoObjective.StatsEntry>()) {
            val stat = objective.stat ?: continue

            objectiveService.updateTeamsOnceAchieved(objective)

            val teamPlayers = state.getRegisteredTeams()
                .associate { it.key to it.players }
                .mapValues { (_, players) ->
                    players.map { offlinePlayerCache.getOfflinePlayer(it) }
                }

            val teamsProgress = teamPlayers.map { (team, players) ->
                val statProgress = players.maxOf { getStatValue(objective, stat, it) }
                    .takeIf { it <= objective.valueMax }
                    ?: 0
                val progress = statProgress / objective.valueMin.coerceAtLeast(1).toFloat()
                team to progress
            }.toMap()

            objectiveService.updateTeamsSeen(card, objective) { team ->
                teamPlayers[team]
                    ?.any { getStatValue(objective, stat, it) > 0 }
                    ?: false
            }

            val playersHolding = teamPlayers.asSequence()
                .flatMap { (team, players) -> players.map { team to it } } // List<(TeamKey, Player)>
                .filter { (_, player) -> getStatValue(objective, stat, player) in objective.valueRange }
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