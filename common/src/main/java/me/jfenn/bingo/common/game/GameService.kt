package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.card.objective.BingoObjectiveManager
import me.jfenn.bingo.common.commands.BingoOptionsCommands
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.options.BingoGoal
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.BingoWinCondition
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.spawn.ChestService
import me.jfenn.bingo.common.spawn.SpawnService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.stats.WriteStatsService
import me.jfenn.bingo.common.team.ShuffleTeamsCommand
import me.jfenn.bingo.common.team.TeamCompletedCard
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.MessageService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.seconds
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.TextAction
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Formatting
import org.slf4j.Logger
import java.time.Instant

internal class GameService(
    private val state: BingoState,
    private val options: BingoOptions,
    private val server: MinecraftServer,
    private val eventBus: IEventBus,
    private val spawnService: SpawnService,
    private val chestService: ChestService,
    private val teamService: TeamService,
    private val configService: ConfigService,
    private val cardService: CardService,
    private val objectiveManager: BingoObjectiveManager,
    private val playerManager: IPlayerManager,
    private val optionsService: OptionsService,
    private val gameOverService: GameOverService,
    private val writeStatsService: WriteStatsService,
    private val messageService: MessageService,
    private val text: TextProvider,
    private val log: Logger,
) {

    private val notifySpamCooldown = 2.seconds
    private var notifySpamTime = Instant.MIN

    private fun notifyPlayersMissingTeams(playersWithoutATeam: List<IPlayerHandle>) {
        if (!state.isLobbyMode) return

        // Prevent spamming titles (making them unreadable) if someone spams the start button
        val now = Instant.now()
        if (now - notifySpamTime < notifySpamCooldown)
            return

        notifySpamTime = now

        for (player in playersWithoutATeam) {
            teamService.notifyMissingTeam(player)
        }
    }

    fun start(
        warnings: MutableList<IText>,
        ignoreWarnings: Boolean = false,
        allowSpectators: Boolean = false,
    ): Boolean {
        val playersWithoutATeam = playerManager.getPlayers()
            // filter for players that have not chosen a team
            .filter { !teamService.isTeamChosen(it) }

        if (playersWithoutATeam.isNotEmpty() && !allowSpectators) {
            // If there's more than one player, teams need to be set up manually!
            if (playerManager.getPlayers().size > 1) {
                warnings += text.string(StringKey.CommandStartPlayersMissingTeams, playersWithoutATeam.joinToString { it.playerName })
                warnings += text.string(StringKey.CommandStartPlayersMissingTeamsHint, ShuffleTeamsCommand.SHUFFLE_TEAMS_COMMAND, GameCommands.IGNORE_WARNINGS_COMMAND)
                notifyPlayersMissingTeams(playersWithoutATeam)
                return false
            }

            // Otherwise, we can just auto-assign a team
            teamService.shuffleTeams(1)
        }

        // Ensure that all selected tier lists are supported
        var hasWarning = false
        if (!ignoreWarnings) {
            hasWarning = !cardService.isSupported { warnings.add(it) }
        }

        // Warn if Lockout mode & Lines goal are used together
        val activeCard = state.getActiveCard()
        if (!ignoreWarnings && activeCard.options.isLockoutMode && activeCard.options.goal is BingoGoal.Lines) {
            hasWarning = true

            val goalText = activeCard.options.goal.format(
                textProvider = text,
                supportsFullCard = options.winCondition !is BingoWinCondition.ReplaceGoals,
                descriptive = true,
            )

            warnings += text.string(StringKey.CommandStartLockoutWithLinesGoal, goalText)
            warnings += text.literal(BingoOptionsCommands.GOAL_FULL_CARD_ITEMS).formatted(Formatting.UNDERLINE)
                .also { it.setClickEvent(TextAction.SuggestCommand(BingoOptionsCommands.GOAL_FULL_CARD_ITEMS)) }
        }

        if (hasWarning) {
            return false
        }

        // if the game has already been started, ignore
        if (state.state != GameState.PREGAME) {
            warnings.add(text.string(StringKey.CommandStartAlreadyStarted))
            return false
        }

        if (!options.isValid()) {
            warnings.add(text.string(StringKey.CommandStartInvalidDistribution))
            return false
        }

        val initialTeamCards = state.getRegisteredTeams()
            .mapNotNull { state.getCard(it) }
            .distinctBy { it.id }

        for (card in initialTeamCards) {
            // Count any lines/items that will initially be achieved when the game starts
            val initialLines = card.countLines { it is BingoObjective.FreeSpace || it is BingoObjective.InverseEntry }
            val initialItems = card.countItems { it is BingoObjective.FreeSpace || it is BingoObjective.InverseEntry }

            // If these immediately satisfy the goal, then the config is invalid
            val goal = card.options.goal
            if (goal is BingoGoal.Lines && goal.lines <= initialLines) {
                val feedback = text.string(StringKey.CommandStartWillImmediatelyEnd, goal.format(text), text.lineCount(initialLines))
                warnings.add(feedback)
                return false
            }

            if (goal is BingoGoal.Items && goal.items <= initialItems) {
                val feedback = text.string(StringKey.CommandStartWillImmediatelyEnd, goal.format(text), text.itemCount(initialItems))
                warnings.add(feedback)
                return false
            }
        }

        // save the current config, if overwrite option is set
        configService.writeOptions(options)

        for (player in playerManager.getPlayers()) {
            val team = teamService.getPlayerTeam(player)
            player.sendTitle(
                title = text.string(StringKey.GameAnnounceBingo).formatted(team?.textColor ?: Formatting.WHITE),
                subtitle = text.string(StringKey.GameAnnounceStarting),
            )
        }

        // change to the starting state (ensures that start cannot be used again, if this is async)
        state.changeState(eventBus, GameState.STARTING)

        if (state.isLobbyMode) {
            // spread player teams to spawnpoint
            spawnService.createSpawnpoints()
                .thenRunAsync({
                    chestService.createChestSpawnpoints()

                    // change to the loading state (until all players have loaded terrain)
                    state.changeState(eventBus, GameState.PRELOADING)
                }, server)
        } else {
            // change to the loading state (until all players have loaded terrain)
            state.changeState(eventBus, GameState.PRELOADING)
        }

        broadcastStart()

        return true
    }

    fun end(reason: GameEndReason?) {
        if (state.endedAt != null) return

        // Create completion info for any teams that have not already completed the card
        val endedAt = Instant.now()
        for (team in state.getRegisteredTeams()) {
            val card = state.cards.find { it.id == team.cardId }
            val completedCard = team.completedCards.find { it.card.id == team.cardId }
            if (card != null && completedCard == null) {
                team.cardId = null
                team.completedCards += TeamCompletedCard(
                    card = card.copy().also { objectiveManager.init(it) },
                    completedAt = endedAt,
                    isWinner = false,
                    isAutoWin = false,
                    score = team.score
                )
            }
        }

        // Get the game over info first
        // (which needs to query the previous best time from stats.db)
        val info = gameOverService.getGameInfo(reason, endedAt = endedAt)
        state.gameOverInfo = info
        state.endedAt = endedAt

        // Then, write the game to stats.db
        writeStatsService.writeGame()

        // Finally, change state to POSTGAME (announces in GameOverController)
        state.changeState(eventBus, GameState.POSTGAME)

        // if lobbyMode=false, immediately change state back to PREGAME once the POSTGAME events are sent
        if (!state.isLobbyMode) {
            state.reset()
            state.changeState(eventBus, GameState.PREGAME)
        }
    }

    private fun broadcastStart() {
        run {
            val placeholders = mapOf(
                "%game_start%" to text.string(StringKey.GameStart)
                    .formatted(Formatting.GREEN)
                    .let { listOf(text.literal("  ").append(it)) },
                "%game_options%" to optionsService.getOptionsSummary(null)
                    .map { text.literal("  ").append(it) }
                    .toList(),
            )

            messageService.getLines(MessageService.MessageType.GAME_START, placeholders)
                .forEach { log.info(it.toString()) }
        }

        playerManager.getPlayers()
            .filter { state.isLobbyMode || teamService.isPlaying(it) }
            .forEach { player ->
                val placeholders = mapOf(
                    "%game_start%" to text.string(StringKey.GameStart)
                        .formatted(Formatting.GREEN)
                        .let { listOf(text.literal("  ").append(it)) },
                    "%game_options%" to optionsService.getOptionsSummary(player)
                        .map { text.literal("  ").append(it) }
                        .toList(),
                )

                messageService.getLines(MessageService.MessageType.GAME_START, placeholders)
                    .forEach { player.sendMessage(it) }
            }
    }

}