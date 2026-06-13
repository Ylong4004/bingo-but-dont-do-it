package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.rootObjectives
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.IAdvancementManager
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Formatting
import org.slf4j.Logger
import java.time.Instant

internal class AdvancementObjectiveManager(
    private val log: Logger,
    private val server: MinecraftServer,
    private val state: BingoState,
    private val advancementManager: IAdvancementManager,
    private val data: ScopedData,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val objectiveService: ObjectiveService,
    private val text: TextProvider,
) : IObjectiveManager {
    override fun list(): Iterable<String> {
        val dataKeys = data.objectives.rootObjectives
            .filter { (_, it) -> it is ObjectiveData.Advancement }
            .map { it.key }
        return advancementManager.listAdvancements(server) + dataKeys
    }

    override fun listExcludedIds(): Iterable<String> {
        val players = objectiveService.getAllTeamPlayers()
            .map { (_, player) -> player }
            .toList()

        return advancementManager.listAdvancements(server)
            .filter { id ->
                val advancement = advancementManager.getAdvancement(server, id)
                    ?: return@filter false

                players.any { player ->
                    advancementManager.isDone(player.player, advancement)
                }
            }
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective.AdvancementEntry? {
        // If the objective is a data ID
        (data.objectives[id] as? ObjectiveData.Advancement)
            ?.let { data ->
                BingoObjective.AdvancementEntry(
                    id = id,
                    data = data,
                    advancementId = data.advancement,
                )
            }
            ?.takeIf { init(it, isDataObjective = true) }
            ?.let { return it }

        // If the objective is a direct advancement identifier
        return BingoObjective.AdvancementEntry(
            id,
            data = ObjectiveData.Advancement(advancement = id),
            advancementId = id
        ).takeIf { init(it) }
    }

    private fun init(goal: BingoObjective, isDataObjective: Boolean = false): Boolean {
        if (goal !is BingoObjective.AdvancementEntry) return false

        val advancement = kotlin.runCatching {
            advancementManager.getAdvancement(server, goal.advancementId)
        }.getOrNull()

        if (advancement == null) {
            if (isDataObjective) {
                log.objectiveError(goal.id, "Advancement ${goal.advancementId} could not be found.")
            }
            return false
        }

        goal.advancement = advancement

        val display = ObjectiveDisplay.Resolved(
            name = advancement.name
                ?.let {
                    text.empty()
                        .append(it)
                        .resetStyle()
                        .formatted(Formatting.GREEN)
                },
            lore = advancement.description
                ?.let { listOf(it) }
                ?: emptyList(),
            item = advancement.displayIcon,
            decoration = CardTile.Decoration.ADVANCEMENT,
        )
        goal.display = objectiveDisplayService.resolve(goal.id, goal.data.display, display)

        return true
    }

    override fun init(card: BingoCard) {
        for (goal in card.objectivesByInstance<BingoObjective.AdvancementEntry>()) {
            init(goal)
        }
    }

    override fun tick(card: BingoCard) {
        val now = state.updatedAt ?: Instant.MIN

        for (objective in card.objectivesByInstance<BingoObjective.AdvancementEntry>()) {
            val advancement = objective.advancement ?: continue

            objectiveService.updateTeamsOnceAchieved(objective)

            val teamPlayers = objectiveService.getPlayersByTeam(card)
                .mapKeys { it.key.key }

            val teamsProgress = teamPlayers.map { (team, players) ->
                val progress = players.maxOf { advancementManager.getProgress(it.player, advancement) }
                team to progress
            }.toMap()

            objectiveService.updateTeamsSeen(card, objective) { team ->
                teamPlayers[team]
                    ?.any { advancementManager.isAnyObtained(it.player, advancement) }
                    ?: false
            }

            val playersHolding = teamPlayers.asSequence()
                .flatMap { (team, players) -> players.map { team to it } } // List<(TeamKey, Player)>
                .filter { (_, player) -> advancementManager.isDone(player.player, advancement) }
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