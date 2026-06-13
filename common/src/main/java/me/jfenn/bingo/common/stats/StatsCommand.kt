package me.jfenn.bingo.common.stats

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatString
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class StatsCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
) {

    private val IExecutionContext.statsService get() = scope.get<StatsService>()

    private fun IExecutionContext.getStatsSummary(
        player: IPlayerHandle
    ) {
        val summary = statsService.getPlayerSummary(player.uuid)

        sendMessage(text.string(StringKey.CommandStatsPlayer, player.playerName))

        // All Time
        sendMessage(text.literal("★ ").append(text.string(StringKey.StatsAllTime)).formatted(Formatting.BOLD, Formatting.YELLOW))
        sendMessage(
            text.literal("  ")
                .append(text.string(StringKey.StatsGames).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.totalGames.formatLargeNumber()}, ")
                .append(text.string(StringKey.StatsWinLoss).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.totalWins.formatLargeNumber()}/${summary.totalLosses.formatLargeNumber()}, ")
                .append(text.string(StringKey.StatsItems).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.totalItems.formatLargeNumber()}")
        )
        sendMessage(
            text.literal("  ")
                .append(text.string(StringKey.StatsPlaytime).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.totalPlaytime.formatString()}")
        )

        // Monthly
        sendMessage(text.literal("\uD83D\uDCC6 ").append(text.string(StringKey.StatsMonthly)).formatted(Formatting.BOLD, Formatting.AQUA))
        sendMessage(
            text.literal("  ")
                .append(text.string(StringKey.StatsGames).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.monthlyGames.formatLargeNumber()}, ")
                .append(text.string(StringKey.StatsWinLoss).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.monthlyWins.formatLargeNumber()}/${summary.monthlyLosses.formatLargeNumber()}, ")
                .append(text.string(StringKey.StatsItems).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.monthlyItems.formatLargeNumber()}")
        )
        sendMessage(
            text.literal("  ")
                .append(text.string(StringKey.StatsPlaytime).formatted(Formatting.BOLD, Formatting.GRAY))
                .append(": ${summary.monthlyPlaytime.formatString()}")
        )
    }

    private fun IExecutionContext.clearPlayerStats(playerToReset: IPlayerHandle) {
        statsService.resetPlayerStats(playerToReset.uuid)

        text.string(StringKey.CommandStatsResetPlayer, playerToReset.playerName)
            .formatted(Formatting.YELLOW)
            .also { sendFeedback(it) }
    }

    private fun IExecutionContext.clearGameStats() {
        val options = scope.get<BingoOptions>()

        statsService.resetGameStats(options)

        text.string(StringKey.CommandStatsResetGame)
            .formatted(Formatting.YELLOW)
            .also { sendFeedback(it) }
    }

    private fun IExecutionContext.clearAllStats() {
        statsService.resetAllStats()

        text.string(StringKey.CommandStatsResetAll)
            .formatted(Formatting.YELLOW)
            .also { sendFeedback(it) }
    }

    init {
        commandManager.register("bingostats") {
            player("player") { playerArg ->
                requires {
                    hasPermission(Permission.STATS_VIEW_PLAYER)
                }
                executes {
                    val player = getArgument(playerArg)
                    getStatsSummary(player)
                }
            }

            literal("reset") {
                literal("player") {
                    requires { hasPermission(Permission.STATS_RESET_SELF) || hasPermission(Permission.STATS_RESET_GLOBAL) }
                    executes { clearPlayerStats(playerOrThrow) }

                    player("player") { playerArg ->
                        requires { hasPermission(Permission.STATS_RESET_GLOBAL) }
                        executes { clearPlayerStats(getArgument(playerArg)) }
                    }
                }

                literal("game") {
                    requires { hasPermission(Permission.STATS_RESET_GLOBAL) }
                    executes { clearGameStats() }
                }

                literal("all") {
                    requires { hasPermission(Permission.STATS_RESET_GLOBAL) }
                    executes { clearAllStats() }
                }
            }

            requires {
                hasPermission(Permission.STATS_VIEW_SELF)
            }
            executes {
                getStatsSummary(playerOrThrow)
            }
        }
    }

}