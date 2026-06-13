package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.OfflinePlayerCache
import me.jfenn.bingo.common.utils.toMap
import me.jfenn.bingo.platform.IPlayerHandle
import java.time.Instant
import java.util.*

internal class ObjectiveService(
    private val state: BingoState,
    private val offlinePlayerCache: OfflinePlayerCache,
) {

    fun getAllTeams(): Sequence<BingoTeam> {
        return state.getRegisteredTeams()
            .asSequence()
            .filter { it.isPlaying() }
    }

    fun getAllTeamPlayers(): Sequence<Pair<BingoTeam, IPlayerHandle>> = sequence {
        for (team in getAllTeams()) {
            for (player in team.players) {
                yield(Pair(team, offlinePlayerCache.getOfflinePlayer(player)))
            }
        }
    }

    fun getTeams(card: BingoCard): Sequence<BingoTeam> {
        return getAllTeams()
            .filter { it.cardId == card.id }
    }

    fun getTeamPlayers(card: BingoCard): Sequence<Pair<BingoTeam, IPlayerHandle>> = sequence {
        for (team in getTeams(card)) {
            for (player in team.players) {
                yield(Pair(team, offlinePlayerCache.getOfflinePlayer(player)))
            }
        }
    }

    fun getPlayersByTeam(card: BingoCard): Map<BingoTeam, List<IPlayerHandle>> {
        return getTeams(card)
            .associateWith { team ->
                team.players.map { offlinePlayerCache.getOfflinePlayer(it) }
            }
    }

    /**
     * Updates components of the objective state, but ensures that
     * values do not change for any teams that should not be updated
     */
    fun update(
        objective: BingoObjective,
        playersHolding: Map<UUID, BingoObjectiveCapture>? = null,
        players: Map<UUID, BingoObjectiveCapture>? = null,
        teamsProgress: Map<BingoTeamKey, Float>? = null,
        teams: Set<BingoTeamKey>? = null,
    ) {
        if (playersHolding != null) {
            val newPlayersHolding = playersHolding.toMutableMap()
            objective.playersHolding = newPlayersHolding
        }

        if (players != null) {
            val newPlayers = players.toMutableMap()
            objective.players = newPlayers
        }

        if (teamsProgress != null) {
            val newTeamsProgress = teamsProgress.toMutableMap()
            objective.teamsProgress = newTeamsProgress
        }

        if (teams != null) {
            val now = state.updatedAt ?: Instant.MIN
            val newTeamsAchieved = teams
                .associateWith { objective.teamsAchieved[it] ?: now }
                .toMutableMap()

            val newTeamsLost = (objective.teamsAchieved.keys + objective.teamsLost.keys)
                .filter { !newTeamsAchieved.containsKey(it) }
                .associateWith { objective.teamsLost[it] ?: now }
                .toMutableMap()

            objective.teamsAchieved = newTeamsAchieved
            objective.teamsLost = newTeamsLost
        }
    }

    fun getPlayers(
        card: BingoCard,
        playersHolding: Map<UUID, BingoObjectiveCapture>,
        players: Map<UUID, BingoObjectiveCapture>,
        shouldRetainCaptures: Boolean = !card.options.isInventoryMode,
    ): Map<UUID, BingoObjectiveCapture> {
        // Once captured, most objectives can only be lost if isInventoryMode=true
        // therefore, combine both players & playersHolding before updating the objective
        val combinedPlayersHolding = when {
            shouldRetainCaptures -> playersHolding + players
            else -> playersHolding
        }

        return when {
            card.options.isLockoutMode -> {
                // oldest capture first
                val teamHolding = combinedPlayersHolding.minByOrNull { it.value.instant }
                    ?.value?.team
                    ?.let { state.teams[it] }

                if (teamHolding != null)
                    combinedPlayersHolding.filter { teamHolding.players.contains(it.value.player) }.toMutableMap()
                else mutableMapOf()
            }
            else -> combinedPlayersHolding.entries
                .sortedBy { it.value.instant } // oldest captures first
                .distinctBy { it.value.team }
                .toMap()
        }
    }

    fun getTeams(
        players: Map<UUID, BingoObjectiveCapture>,
    ): Set<BingoTeamKey> {
        return players.values.map { it.team }.toSet()
    }

    fun updateTeamsOnceAchieved(objective: BingoObjective) {
        objective.teamsOnceAchieved.addAll(objective.teamsAchieved.keys)
    }

    fun updateTeamsSeen(
        card: BingoCard,
        objective: BingoObjective,
        condition: (BingoTeamKey) -> Boolean
    ) {
        // This can optimize condition checks a little, because
        // teamsSeen is only ever additive
        // (we don't need to check teams again once they're in the set)

        val teamsNotSeen = getTeams(card).map { it.key }.toSet() - objective.teamsSeen
        objective.teamsSeen.addAll(teamsNotSeen.filter(condition))
    }

}