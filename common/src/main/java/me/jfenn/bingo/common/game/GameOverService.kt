package me.jfenn.bingo.common.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.BingoWinCondition
import me.jfenn.bingo.common.options.EndWhen
import me.jfenn.bingo.common.scoring.ScoreRanking
import me.jfenn.bingo.common.scoring.ScoreRankingService
import me.jfenn.bingo.common.scoring.ScoreService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.stats.StatsService
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.DurationType
import me.jfenn.bingo.common.utils.InstantType
import me.jfenn.bingo.common.utils.Vector3dAsArray
import me.jfenn.bingo.common.utils.formatHHMMSS
import me.jfenn.bingo.common.utils.formatString
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.dialog.IDialogAction
import me.jfenn.bingo.platform.dialog.IDialogHandle
import me.jfenn.bingo.platform.dialog.IDialogManager
import me.jfenn.bingo.platform.item.IItemStackSerialized
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.utils.UuidAsString
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.time.Duration
import java.time.Instant

internal class GameOverService(
    private val state: BingoState,
    private val options: BingoOptions,
    private val textProvider: TextProvider,
    private val stats: StatsService,
    private val server: MinecraftServer,
    private val playerManager: IPlayerManager,
    private val text: TextProvider,
    private val permissions: IPermissionsApi,
    private val gameResumeService: GameResumeService,
    private val dialogManager: IDialogManager,
    private val ddiHistoryService: DDIGameHistoryService,
) {

    @Serializable
    class GameOverInfo(
        val reason: GameEndReason?,
        val endedAt: InstantType,
        val duration: DurationType,
        val winningTeamKey: BingoTeamKey?,
        val leadingTeamKey: LeadingTeam?,
        val playerInfo: Map<UuidAsString, GameOverPlayerInfo>,
        val playerStates: Map<UuidAsString, PlayerState>,
        val isBestTime: Boolean,
        val prevBestTime: DurationType?,
        val scoreRankings: List<ScoreRanking>,
        val ddiDamageHistory: List<DDITeamDamageHistory> = emptyList(),
    )

    @Serializable
    class GameOverPlayerInfo(
        val winStreak: Long,
        val bestWinStreak: Long?,
        val isBestWinStreak: Boolean,

        val capturedItems: Int,
        val bestCapturedItems: Int?,
        val isBestCapturedItems: Boolean,
    )

    @Serializable
    class PlayerState(
        val world: String,
        val position: Vector3dAsArray,
        val pitch: Float,
        val yaw: Float,
        val inventory: Map<Int, IItemStackSerialized>,
    )

    @Serializable
    sealed class LeadingTeam {
        abstract val leader: BingoTeamKey

        @Serializable
        @SerialName("cards")
        class Cards(override val leader: BingoTeamKey): LeadingTeam()
        @Serializable
        @SerialName("lines")
        class Lines(override val leader: BingoTeamKey): LeadingTeam()
        @Serializable
        @SerialName("items")
        class Items(override val leader: BingoTeamKey): LeadingTeam()
    }

    fun getTitle(info: GameOverInfo): IText {
        return info.reason?.format(textProvider)
            ?: textProvider.string(StringKey.GameEndGameOver)
    }

    fun getMessage(info: GameOverInfo): IText? {
        if (info.reason is GameEndReason.DDI) {
            return (
                info.winningTeamKey
                    ?.let { state.teams[it] }
                    ?.let { winner ->
                        textProvider.string(
                            StringKey.GameEndDdiWinner,
                            winner.getName(textProvider, playerName = true),
                        )
                    }
                    ?: textProvider.string(StringKey.GameEndDdiNoSurvivors)
                ).formatted(Formatting.YELLOW)
        }

        return (
            info.winningTeamKey?.let { state.teams[it] }
                ?.let { winner ->
                    val isAutoWin = winner.completedCards.lastOrNull()?.isAutoWin == true
                    val string = if (isAutoWin) StringKey.GameEndCompletedCardByStalemate else StringKey.GameEndCompletedCard
                    textProvider.string(string, winner.getName(textProvider, playerName = true))
                }
                ?: info.leadingTeamKey
                    .takeIf { info.scoreRankings.size > 1 }
                    ?.let { leadingTeamKey ->
                        val leader = state.teams[leadingTeamKey.leader]
                            ?.getName(textProvider, playerName = true)
                            ?: Text.literal("[unknown]")

                        when (leadingTeamKey) {
                            is LeadingTeam.Cards -> textProvider.string(StringKey.GameEndWonWithMoreCards, leader)
                            is LeadingTeam.Lines -> textProvider.string(StringKey.GameEndWonWithMoreLines, leader)
                            is LeadingTeam.Items -> textProvider.string(StringKey.GameEndWonWithMoreItems, leader)
                        }
                    }
                ?: textProvider.string(StringKey.GameEndDraw)
                    .takeIf { info.scoreRankings.size > 1 && options.endGameWhen is EndWhen.FirstWin }
        )?.formatted(Formatting.YELLOW)
    }

    private fun getTeamDuration(team: BingoTeam): Duration? {
        return team.winner?.instant?.let { completedAt ->
            Duration.between(state.startedAt ?: completedAt, completedAt)
        }
            ?.plus(state.timeAdjustment)
            ?.minus(state.timeOffline)
    }

    fun getGameInfo(reason: GameEndReason?, endedAt: Instant): GameOverInfo {
        val winningTeams = state.getRegisteredTeams()
            .filter { it.isWinner() }

        // The winning team, if there is only one winner
        val winningTeam = winningTeams
            .takeIf { it.size == 1 }
            ?.firstOrNull()

        val leadingTeam =
            ScoreService.getLeadingByCards(state)?.let { LeadingTeam.Cards(it.key) }
                ?: ScoreService.getLeadingByLines(state)?.let { LeadingTeam.Lines(it.key) }
                ?: ScoreService.getLeadingByItems(state)?.let { LeadingTeam.Items(it.key) }

        val playerInfo = buildMap {
            for (team in state.getRegisteredTeams()) {
                for (player in team.players) {
                    this[player.uuid] = getPlayerInfo(team, player)
                }
            }
        }

        // Record player positions/inventory when the game is ended
        // (for "Keep Playing" feature)
        val playerStates = buildMap {
            if (state.isLobbyMode) {
                playerInfo.keys
                    .mapNotNull { playerManager.getPlayer(it) }
                    .forEach { put(it.uuid, getPlayerState(it)) }
            }
        }

        val gameDuration = state.ingameDuration()
        val prevBestTime = stats.getBestTime(options, server.isSingleplayer && state.isSingleplayer())
        // if prevBestTime didn't exist, or if the new duration is less than the prev time
        // ... and the game was actually completed & won by a team
        val isBestTime = (prevBestTime == null || gameDuration?.let { it < prevBestTime } == true)
                && !state.isForfeit
                && winningTeams.isNotEmpty()

        val scoreRankings = ScoreRankingService.getScoreRankings(state)
            .let { rankings ->
                // If we're in infinite mode, then there isn't a "finish"
                // - use the game duration as each team's completion time, instead of showing DNF
                if (state.options.winCondition is BingoWinCondition.Infinite) {
                    rankings.map { it.copy(duration = gameDuration) }
                } else {
                    rankings
                }
            }

        return GameOverInfo(
            reason = reason,
            winningTeamKey = winningTeam?.key,
            leadingTeamKey = leadingTeam,
            playerInfo = playerInfo,
            playerStates = playerStates,
            duration = state.ingameDuration() ?: Duration.ZERO,
            endedAt = endedAt,
            isBestTime = isBestTime,
            prevBestTime = prevBestTime,
            scoreRankings = scoreRankings,
            ddiDamageHistory = ddiHistoryService.snapshot(),
        )
    }

    private fun getPlayerInfo(team: BingoTeam, player: PlayerProfile): GameOverPlayerInfo {
        val isWinner = team.isWinner()

        // must align with the logic in WriteStatsService
        // - a game is a draw if there is at least one opponent and no winning team
        val teams = state.getRegisteredTeams()
        val isDraw = teams.none { it.isWinner() } && teams.size > 1

        val winStreak = when {
            isWinner -> stats.getPlayerWinStreak(player.uuid) + 1
            // if the game is a forfeit or draw, the current win streak persists
            state.isForfeit || isDraw -> stats.getPlayerWinStreak(player.uuid)
            // otherwise, the game was lost
            else -> 0
        }
        val bestWinStreak = stats.getBestWinStreak(player.uuid)

        val capturedItems = team.completedCards.sumOf { completion ->
            completion.card.countItems { it.players.containsKey(player.uuid) }
        }
        val bestCapturedItems = stats.getBestCapturedItems(player.uuid, options)

        return GameOverPlayerInfo(
            winStreak = winStreak,
            bestWinStreak = bestWinStreak,
            isBestWinStreak = isWinner && bestWinStreak?.let { winStreak > it } == true,

            capturedItems = capturedItems,
            bestCapturedItems = bestCapturedItems,
            isBestCapturedItems = bestCapturedItems?.let { capturedItems > it } == true,
        )
    }

    private fun getPlayerState(player: IPlayerHandle): PlayerState {
        return PlayerState(
            world = player.world.identifier,
            position = player.pos,
            pitch = player.pitch,
            yaw = player.yaw,
            inventory = player.allInventorySlots()
                .map { (slot, stack) -> slot to stack.copy() }
                .toMap(),
        )
    }

    fun getScoreRankings(info: GameOverInfo) = info.scoreRankings.map {
        GameOverPacket.ScoreRanking(
            index = it.index,
            key = it.key,
            name = state.teams[it.key]?.getName(text, playerName = true)
                ?: text.literal("[unknown]"),
            score = it.score,
            duration = it.duration,
        )
    }

    fun createPacket(
        player: IPlayerHandle,
        info: GameOverInfo,
        isUpdate: Boolean,
        title: IText = getTitle(info),
        message: IText? = getMessage(info),
        scoreRankings: List<GameOverPacket.ScoreRanking> = getScoreRankings(info),
    ): GameOverPacket {
        val winningTeam = info.winningTeamKey?.let { state.teams[it] }
        val playerInfo = info.playerInfo[player.uuid]
        val isWinner = winningTeam?.includesPlayer(player.player) ?: false

        val defaultTab = when {
            // If there are multiple teams & either more than one team completed the game, or the game was a draw
            scoreRankings.size > 1 && scoreRankings.count { it.duration != null } != 1 -> GameOverPacket.EndScreenTab.SCORES
            else -> GameOverPacket.EndScreenTab.CARDS
        }

        return GameOverPacket(
            title = title,
            subtitle = message ?: text.empty(),
            winner = info.winningTeamKey,
            duration = state.ingameDuration() ?: Duration.ZERO,
            isReturnToLobbyAvailable = permissions.hasPermission(player, Permission.COMMAND_READY)
                    && state.isLobbyMode,
            isResumeAvailable = gameResumeService.isResumeAvailable(player),
            isWinner = isWinner,
            isUpdate = isUpdate,

            winStreak = playerInfo?.winStreak,
            bestWinStreak = playerInfo?.bestWinStreak,
            isBestWinStreak = playerInfo?.isBestWinStreak == true,

            capturedItems = playerInfo?.capturedItems,
            bestCapturedItems = playerInfo?.bestCapturedItems,
            isBestCapturedItems = playerInfo?.isBestCapturedItems == true,

            endedAt = info.endedAt,

            isBestTime = info.isBestTime,
            prevBestTime = info.prevBestTime,

            seed = server.overworld.seed,
            scores = scoreRankings,
            defaultTab = defaultTab,
            ddiDamageHistory = info.ddiDamageHistory,
        )
    }

    fun createDialog(
        packet: GameOverPacket
    ): IDialogHandle? {
        val builder = dialogManager.multiActionBuilder() ?: return null

        builder.title = text.string(StringKey.FullName)
        builder.addText(packet.title.copy().formatted(Formatting.BOLD))
        builder.addText(packet.subtitle)

        builder.addText(text.empty())

        val separator = text.literal(" — ").formatted(Formatting.GRAY)

        builder.addText(
            text.empty()
                .append(text.string(StringKey.StatsGameDuration))
                .append(separator)
                .append(
                    text.literal(packet.duration.formatString())
                        .also { it.setColor(0xFF_CCA3F5.toInt()) }
                )
        )

        if (packet.scores.size > 1) {
            builder.addText(text.empty())

            for (score in packet.scores) {
                builder.addText(
                    text.literal("#${score.index+1}. ")
                        .append(score.name)
                        .append(separator)
                        .append(score.score.formatText(text))
                        .append(separator)
                        .append(score.duration?.formatHHMMSS() ?: "DNF")
                )
            }
        } else {
            builder.addText(
                text.empty()
                    .append(text.string(StringKey.GameEndScoresScore))
                    .append(separator)
                    .append(
                        packet.scores.firstOrNull()?.score?.formatText(text)
                            ?.also { it.setColor(0xFF_CCA3F5.toInt()) }
                            ?: text.empty()
                    )
            )
        }

        if (packet.ddiDamageHistory.isNotEmpty()) {
            builder.addText(text.empty())
            builder.addText(text.string(StringKey.GameEndDdiHistory).formatted(Formatting.GOLD))
            for (team in packet.ddiDamageHistory) {
                val teamName = packet.scores.find { it.key == team.teamKey }?.name?.copy()
                    ?: text.literal(team.teamName)
                builder.addText(text.literal("◆ ").formatted(Formatting.GRAY).append(teamName))
                if (team.entries.isEmpty()) {
                    builder.addText(
                        text.literal("  ")
                            .append(text.string(StringKey.GameEndDdiNoDamage).formatted(Formatting.DARK_GRAY))
                    )
                } else {
                    team.entries.forEachIndexed { index, entry ->
                        val actor = entry.actorName
                            ?.let(text::literal)
                            ?: text.string(StringKey.GameEndDdiSystem)
                        builder.addText(
                            text.literal("  ${index + 1}. ")
                                .append(text.literal(entry.wordText).formatted(Formatting.AQUA))
                                .append(separator)
                                .append(actor)
                                .append(separator)
                                .append(
                                    text.string(
                                        StringKey.GameEndDdiRemaining,
                                        entry.heartsRemaining,
                                        entry.maxHearts,
                                    ).formatted(Formatting.RED)
                                )
                        )
                    }
                }
            }
        }

        builder.addText(text.empty())

        builder.addAction(
            label = text.string(StringKey.CardTitle),
            action = IDialogAction.RunCommand("bingo card"),
        )

        if (packet.isReturnToLobbyAvailable) {
            builder.addAction(
                label = text.string(StringKey.WorldReturnToLobby),
                action = IDialogAction.RunCommand("ready"),
            )
        }

        builder.setExitAction(
            label = text.string(StringKey.GuiClose),
            action = IDialogAction.None,
        )

        return builder.build()
    }
}
