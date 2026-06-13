package me.jfenn.bingo.common.scoreboard

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.config.PlayerSettingsService
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.options.BingoGoal
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.BingoWinCondition
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.scoring.ScoreRankingService
import me.jfenn.bingo.common.scoring.ScoreService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.MessageService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatHHMMSS
import me.jfenn.bingo.common.utils.formatString
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.text.IText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.time.Duration

/**
 * Displays scoreboard score/timer information for all players.
 */
internal class ScoreboardController(
    events: ScopedEvents,
    private val options: BingoOptions,
    private val state: BingoState,
    private val teamService: TeamService,
    private val scoreboardService: ScoreboardService,
    private val playerManager: IPlayerManager,
    private val playerSettingsService: PlayerSettingsService,
    private val messageService: MessageService,
    private val text: TextProvider,
) : BingoComponent() {

    val isMultipleTeams get() = state.state == GameState.PREGAME || state.getRegisteredTeams().size > 1

    val isMultiCardWinCondition get() = when (
        val winCondition = options.winCondition
    ) {
        is BingoWinCondition.Cards -> winCondition.cards > 1
        is BingoWinCondition.Infinite -> true
        is BingoWinCondition.ReplaceGoals -> false
    }

    private fun getTeamName(team: BingoTeam?) = team?.takeIf { isMultipleTeams }
        ?.getName(text, playerName = true, symbol = true, bracketed = false, teamNameKey = null)
        ?.let { text.string(StringKey.ScoreboardTeam, it) }

    private fun getTeamScore(team: BingoTeam?) = team?.score
        ?.let { score ->
            listOfNotNull(
                text.string(StringKey.ScoreboardItemsScored, Text.literal("${score.items}")
                    .formatted(team.textColor)),
                text.string(StringKey.ScoreboardLinesScored, Text.literal("${score.lines}")
                    .formatted(team.textColor)),
                text.string(StringKey.ScoreboardCardsScored, Text.literal("${score.cards}")
                    .formatted(team.textColor))
                    .takeIf { isMultiCardWinCondition },
            )
        }

    private fun getTeamScores(team: BingoTeam?, teamCard: BingoCard): List<IText> {
        val rankings = ScoreRankingService.getScoreRankings(state)
        return ScoreRankingService.getScoreRankings(state)
            .filter { options.showLeadingTeam || it.key == team?.key || team == null }
            .take(8)
            .let { list ->
                // Ensure that the current team is included in the scoreboard
                val teamRanking = rankings.find { it.key == team?.key }
                if (teamRanking == null || list.contains(teamRanking)) {
                    list
                } else {
                    list.dropLast(1) + teamRanking
                }
            }
            .map { ranking ->
                val isTeamVisible = ranking.key == team?.key || team == null
                val score = ranking.duration?.formatHHMMSS()
                    ?.let { text.literal(it) }
                    ?: ranking.score.cards
                        .takeIf { it > 0 && (options.showCompletedItems || options.showCompletedLines || isTeamVisible) }
                        ?.let { text.cardCount(it) }
                    ?: ranking.score.lines
                        .takeIf { it > 0 && teamCard.options.goal is BingoGoal.Lines && (options.showCompletedLines || isTeamVisible) }
                        ?.let { text.lineCount(it) }
                    ?: ranking.score.items
                        .takeIf { options.showCompletedItems || isTeamVisible }
                        ?.let { text.itemCount(it) }

                text.empty()
                    .append(
                        if (ranking.key == team?.key) {
                            text.literal("➤ ").formatted(team.textColor)
                        } else {
                            text.empty()
                        }
                    )
                    .append("${ranking.index+1}. ")
                    .append(
                        state.teams[ranking.key]
                            ?.getName(
                                textProvider = text,
                                playerName = true,
                                symbol = true,
                                bracketed = false,
                                teamNameKey = null,
                            )
                            ?: text.empty()
                    )
                    .let {
                        if (score != null) {
                            it.append(" · ").append(score)
                        } else {
                            it
                        }
                    }
                    .formatted(if (ranking.key == team?.key || team == null) Formatting.WHITE else Formatting.GRAY)
            }
    }

    private fun getLeadingName(leadingTeam: BingoTeam?): IText? {
        val leadingTeamName = leadingTeam?.getName(text, playerName = true, symbol = true, bracketed = false, teamNameKey = null)
            ?: text.string(StringKey.ScoreboardLeadingTeamTie).formatted(Formatting.WHITE)
        return leadingTeamName.takeIf { options.showLeadingTeam && isMultipleTeams }
            ?.let { text.string(StringKey.ScoreboardLeadingTeam, it) }
    }

    private fun getLeadingScore(team: BingoTeam?, leadingTeam: BingoTeam?): List<IText> {
        val leadingTeamFormatting = when {
            // if the leading team should be hidden, don't use its formatting
            !options.showLeadingTeam -> Formatting.WHITE
            // otherwise, default to WHITE if the team is tied
            else -> leadingTeam?.textColor ?: Formatting.WHITE
        }

        val leadingTeamScore = when {
            leadingTeam == null -> TeamScore(
                // if the leading team is a tie, find one of the tied teams to get the item/line/card count
                items = ScoreService.getLeadingByItems(state, allowTies = true)?.score?.items ?: 0,
                lines = ScoreService.getLeadingByLines(state, allowTies = true)?.score?.lines ?: 0,
                cards = ScoreService.getLeadingByCards(state, allowTies = true)?.score?.cards ?: 0,
            )
            else -> leadingTeam.score
        }

        val showLeadingScore = (options.showCompletedItems || options.showCompletedLines) && team != leadingTeam
        val showLeadingScoreCards = isMultiCardWinCondition && leadingTeamScore.cards != team?.score?.cards

        return leadingTeamScore
            .takeIf { showLeadingScore }
            ?.let { score ->
                listOfNotNull(
                    text.string(StringKey.ScoreboardItemsScored, Text.literal("${score.items}")
                        .formatted(leadingTeamFormatting))
                        .takeUnless { showLeadingScoreCards },
                    text.string(StringKey.ScoreboardLinesScored, Text.literal("${score.lines}")
                        .formatted(leadingTeamFormatting))
                        .takeUnless { showLeadingScoreCards },
                    text.string(StringKey.ScoreboardCardsScored, Text.literal("${score.cards}")
                        .formatted(leadingTeamFormatting))
                        .takeIf { showLeadingScoreCards },
                )
            }
            .orEmpty()
    }

    init {
        events.onEnter(GameState.PREGAME) {
            // reset the scoreboard views when entering a new game
            scoreboardService.clearScoreboards()
        }

        events.onUpdateTick {
            for (team in state.teams.values + null) {
                val teamCard = team?.let { state.getCard(it) } ?: state.getActiveCard()

                val placeholders: Map<String, List<IText>> = buildMap {
                    options.formatGameModeIcons(teamCard)
                        .joinToString(" ")
                        .let {
                            when {
                                it.isEmpty() -> text.string(StringKey.OptionsModeStandard)
                                else -> text.literal(it)
                            }
                        }
                        .let { text.string(StringKey.ScoreboardMode, it.formatted(Formatting.YELLOW)) }
                        .also { put("%mode%", listOf(it)) }

                    options.formatFeaturesIcons()
                        .joinToString(" ")
                        .let {
                            when {
                                it.isEmpty() -> text.string(StringKey.OptionsFeaturesNone)
                                else -> text.literal(it)
                            }
                        }
                        .let { text.string(StringKey.ScoreboardFeatures, it.formatted(Formatting.YELLOW)) }
                        .also { put("%features%", listOf(it)) }

                    text.string(StringKey.ScoreboardGoal, state.formatCardGoals(team, text).formatted(Formatting.YELLOW))
                        .also { put("%goal%", listOf(it)) }

                    if (options.showRemainingTime && options.timeLimit != null) {
                        text.string(StringKey.ScoreboardTimeLeft, state.formatTimeRemaining(text, normalColor = Formatting.YELLOW))
                            .also { put("%time%", listOf(it)) }
                    } else if (options.timeLimit == null) {
                        val duration = (state.ingameDuration() ?: Duration.ZERO).formatString()
                        text.string(StringKey.ScoreboardTime, Text.literal(duration).formatted(Formatting.YELLOW))
                            .also { put("%time%", listOf(it)) }
                    } else {
                        put("%time%", emptyList())
                    }

                    put("%team%", listOfNotNull(getTeamName(team)))
                    put("%team_score%", getTeamScore(team).orEmpty())

                    val leadingTeam = ScoreService.getLeading(state)
                    put("%leading%", listOfNotNull(getLeadingName(leadingTeam)))
                    put("%leading_score%", getLeadingScore(team, leadingTeam))

                    if (state.getRegisteredTeams().size <= 1) {
                        // if in singleplayer, fall back to the explicit "Team:" and "Lines:" score text
                        put(
                            "%scores_list%",
                            listOfNotNull(
                                get("%team%"),
                                get("%team_score%"),
                                listOf(text.empty()),
                                get("%leading%"),
                                get("%leading_score%"),
                            ).flatten()
                        )
                    } else {
                        put("%scores_list%", getTeamScores(team, teamCard))
                    }
                }

                val scoreboardText = messageService.getLines(MessageService.MessageType.SCOREBOARD, placeholders)
                    .let { lines ->
                        val reducedLines: List<IText> = buildList {
                            for (line in lines) {
                                if (line.toString().isEmpty() && lastOrNull()?.toString()?.isEmpty() == true)
                                    continue

                                add(line)
                            }
                        }
                        val emptyLines = reducedLines.reversed().indexOfFirst { it.toString().isNotEmpty() }
                        reducedLines.subList(0, reducedLines.size - emptyLines)
                    }
                    .mapIndexed { index, line ->
                        if (line.toString().isEmpty()) text.literal(" ".repeat(index)) else line
                    }

                // Determine which players should be shown the scoreboard
                val players = playerManager.getPlayers()
                    .filter { teamService.getPlayerTeam(it)?.key == team?.key }
                    .filter { playerSettingsService.getPlayer(it).scoreboard }
                    .filter { scoreboardService.shouldDisplayScoreboard(it) }

                val objective = scoreboardService.getScoreboardObjective(team)
                val displayName = scoreboardText.firstOrNull()
                    ?: text.string(StringKey.ScoreboardName)

                scoreboardService.setScoreboardTitle(objective, displayName, players)
                scoreboardService.setScoreboardContents(objective, scoreboardText.drop(1), players)
            }
        }
    }

}