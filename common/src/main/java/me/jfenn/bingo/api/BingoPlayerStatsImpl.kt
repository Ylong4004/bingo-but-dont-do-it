package me.jfenn.bingo.api

import me.jfenn.bingo.api.data.IBingoPlayerStats
import me.jfenn.bingo.common.stats.data.PlayerStatsSummary
import java.time.Duration

class BingoPlayerStatsImpl(
    val stats: PlayerStatsSummary,
): IBingoPlayerStats {
    override val totalPlaytime: Duration
        get() = stats.totalPlaytime
    override val totalItems by stats::totalItems
    override val totalGames by stats::totalGames
    override val totalWins by stats::totalWins
    override val totalLosses by stats::totalLosses
    override val monthlyPlaytime: Duration
        get() = stats.monthlyPlaytime
    override val monthlyItems by stats::monthlyItems
    override val monthlyGames by stats::monthlyGames
    override val monthlyWins by stats::monthlyWins
    override val monthlyLosses by stats::monthlyLosses
    override val favoriteTeam: String?
        get() = stats.favoriteTeam?.id
    override val favoriteTeamPercentage by stats::favoriteTeamPercentage
    override val winStreak by stats::winStreak
    override val winStreakBest by stats::winStreakBest
}