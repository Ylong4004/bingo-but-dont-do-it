package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.options.BingoGoal
import me.jfenn.bingo.common.options.BingoWinCondition
import me.jfenn.bingo.common.options.EndWhen
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamScore

internal sealed interface StalemateReason {
    class WinByStalemate(val team: BingoTeam) : StalemateReason
    data object Stalemate : StalemateReason
    data object Lockout : StalemateReason
    data object ImpossibleGoal: StalemateReason
}

internal object ScoreService {

    fun isStalemate(state: BingoState, card: BingoCard): StalemateReason? {
        // select only teams on the card
        val teams = state.getRegisteredTeams()
            .filter { it.isPlaying() && it.cardId == card.id }

        // if there are no teams, then I have no idea what's going on
        if (teams.isEmpty()) return null

        // if the win condition is "ReplaceGoals", then any impossible goals could get replaced by another team
        // (and since the score is accumulated, this probably isn't a soft-lock anyway)
        // TODO: there probably _are_ stalemate conditions in ReplaceGoals, but they'll be much more specific
        if (state.options.winCondition is BingoWinCondition.ReplaceGoals)
            return null

        if (
            card.options.isLockoutMode && card.entries
                .mapNotNull { card.objectives[it.objectiveId] }
                .all { it.hasAnyAchieved() }
        ) {
            // if the entire card is full, then it is a lockout (regardless of the goal)
            return StalemateReason.Lockout
        }

        val cardGoal = card.options.goal
        val comparator = when (cardGoal) {
            is BingoGoal.Items -> compareBy { it.items }
            is BingoGoal.Lines -> compareBy<TeamScore> { it.lines }.thenBy { it.items }
        }

        val leadingTeamOrTie = teams.maxWithOrNull { a, b -> comparator.compare(a.score, b.score) }

        // TODO: isPossible only checks the top-level objective at the moment
        // - for max stat values, permanent one_of objectives, etc. this needs data from the objective manager
        // - could be stored in objective.canAchieveTeams on each tick
        // - also needs objective.canLose
        fun isPossible(team: BingoTeam, objective: BingoObjective): Boolean {
            if (objective.hasAchieved(team.key)) return true

            if (objective is BingoObjective.InverseEntry && objective.permanent)
                return false

            val canLoseTile = card.options.isInventoryMode
            val isPermanentlyLocked = card.options.isLockoutMode && !canLoseTile && objective.hasAnyAchieved()
            return !isPermanentlyLocked
        }

        // if noone can beat or tie with the leading team, then it is a lockout (the leading team has won)
        val isStalemate = leadingTeamOrTie != null && teams
            // for all teams other than the leading team...
            .filter { it.key != leadingTeamOrTie.key }
            .takeIf { it.isNotEmpty() }
            // at least one team can tie or beat the leading score
            ?.none { team ->
                val possibleLines = card.countLines { objective -> isPossible(team, objective) }
                val possibleItems = card.countItems { objective -> isPossible(team, objective) }
                val possibleScore = TeamScore(
                    cards = 0,
                    lines = possibleLines,
                    items = possibleItems,
                )
                // if a team can capture more tiles, and it would either tie or beat the current team
                comparator.compare(possibleScore, team.currentScore) > 0 && comparator.compare(
                    possibleScore,
                    leadingTeamOrTie.currentScore
                ) >= 0
            } == true

        fun isGoalPossible(team: BingoTeam): Boolean {
            return when (cardGoal) {
                is BingoGoal.Items -> {
                    val possibleItems = card.countItems { entry -> isPossible(team, entry) }
                    possibleItems >= cardGoal.items
                }
                is BingoGoal.Lines -> {
                    val possibleLines = card.countLines { entry -> isPossible(team, entry) }
                    // if no team can capture the amount of lines needed for the win condition
                    possibleLines >= cardGoal.lines
                }
            }
        }

        val endWhen = state.options.endGameWhen
        val isGoalImpossible = when {
            // if the goal is "full card" when isLockoutMode=true, disregard any unreachable goals
            // (otherwise the game would end instantly)
            cardGoal.isFullCard() && card.options.isLockoutMode && teams.size > 1 -> false
            // if no team can capture the amount of items/lines needed for the win condition
            else -> {
                // figure out the number of teams that are required to complete the game
                val requiredTeams = when (endWhen) {
                    is EndWhen.Never,
                    is EndWhen.FirstWin -> 1
                    is EndWhen.TeamsWin -> endWhen.teams
                    is EndWhen.AllWin -> teams.size
                }

                teams.count { isGoalPossible(it) } < requiredTeams
            }
        }

        val leadingTeam = getLeading(state)?.takeIf { it.cardId == card.id }

        return when {
            // If there is a leading team when a stalemate is triggered, that team should win the card
            isStalemate && !isGoalImpossible && leadingTeam != null -> StalemateReason.WinByStalemate(leadingTeam)
            isGoalImpossible -> StalemateReason.ImpossibleGoal
            isStalemate -> StalemateReason.Stalemate
            else -> null
        }
    }

    fun getComparator(state: BingoState): Comparator<BingoTeam> {
        val teams = state.getRegisteredTeams()
        val maxCards = teams.maxOfOrNull { it.countCards() } ?: 0
        val leadingTeamsByCards = teams.filter { it.countCards() >= maxCards }

        val isLinesGoal = leadingTeamsByCards
            .mapNotNull { team -> state.cards.find { it.id == team.cardId } }
            .all { it.options.goal is BingoGoal.Lines }

        return if (isLinesGoal) {
            compareBy<BingoTeam> { it.score.cards }
                .thenBy { it.score.lines }
                .thenBy { it.score.items }
        } else {
            compareBy<BingoTeam> { it.score.cards }
                .thenBy { it.score.items }
        }
    }

    fun getLeading(state: BingoState, allowTies: Boolean = false): BingoTeam? {
        return getLeadingByCards(state, false)
                ?: getLeadingByLines(state, false)
                    ?: getLeadingByItems(state, allowTies)
    }

    fun getLeadingByCards(state: BingoState, allowTies: Boolean = false): BingoTeam? {
        var max: BingoTeam? = null
        var maxCards = -1
        for (team in state.getRegisteredTeams()) {
            val teamCards = team.score.cards

            if (teamCards > maxCards) {
                max = team
                maxCards = teamCards
            } else if (teamCards == maxCards && !allowTies) {
                max = null
            }
        }

        return max
    }

    fun getLeadingByLines(state: BingoState, allowTies: Boolean = false): BingoTeam? {
        val teams = state.getRegisteredTeams()
        val maxCards = teams.maxOfOrNull { it.countCards() } ?: 0
        val leadingTeams = teams.filter { it.countCards() >= maxCards }

        val isLinesGoal = leadingTeams
            .mapNotNull { team -> state.cards.find { it.id == team.cardId } }
            .all { it.options.goal is BingoGoal.Lines }

        if (!isLinesGoal)
            return null

        var max: BingoTeam? = null
        var maxLines = -1
        for (team in leadingTeams) {
            val teamLines = team.score.lines

            if (teamLines > maxLines) {
                max = team
                maxLines = teamLines
            } else if (teamLines == maxLines && !allowTies) {
                max = null
            }
        }

        return max
    }

    fun getLeadingByItems(state: BingoState, allowTies: Boolean = false): BingoTeam? {
        val teams = state.getRegisteredTeams()
        val maxCards = teams.maxOfOrNull { it.countCards() } ?: 0
        val leadingTeamsByCards = teams.filter { it.countCards() >= maxCards }

        val isLinesGoal = leadingTeamsByCards
            .mapNotNull { team -> state.cards.find { it.id == team.cardId } }
            .all { it.options.goal is BingoGoal.Lines }

        // If the goal is lines, further restrict leading teams to include teams that are tied by max lines
        val maxLines = leadingTeamsByCards.maxOfOrNull { it.score.lines } ?: 0
        val leadingTeams = when {
            isLinesGoal -> leadingTeamsByCards.filter { it.score.lines == maxLines }
            else -> leadingTeamsByCards
        }

        var max: BingoTeam? = null
        var maxItems = -1
        for (team in leadingTeams) {
            val teamItems = team.score.items

            if (teamItems > maxItems) {
                max = team
                maxItems = teamItems
            } else if (teamItems == maxItems && !allowTies) {
                max = null
            }
        }

        return max
    }

}