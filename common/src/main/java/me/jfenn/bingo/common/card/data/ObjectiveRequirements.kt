package me.jfenn.bingo.common.card.data

import kotlinx.serialization.Serializable

@Serializable
class ObjectiveRequirements(
    // Number of teams in the game
    val minTeams: Int = 0,
    val maxTeams: Int = Int.MAX_VALUE,
    // Total number of players in the gae
    val minPlayers: Int = 0,
    val maxPlayers: Int = Int.MAX_VALUE,
    // Number of players on each team
    val minPlayersPerTeam: Int = 0,
    val maxPlayersPerTeam: Int = Int.MAX_VALUE,
    // Objective IDs that must successfully resolve
    val objectives: Set<String> = emptySet(),
    val onlyDefaultTeams: Boolean = false,
) {
    val numTeams get() = minTeams..maxTeams
    val numPlayers get() = minPlayers..maxPlayers
    val numPlayersPerTeam get() = minPlayersPerTeam..maxPlayersPerTeam
}
