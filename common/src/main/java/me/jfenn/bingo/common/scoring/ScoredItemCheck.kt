package me.jfenn.bingo.common.scoring

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.BingoCardEntry
import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.card.objective.BingoObjectiveManager
import me.jfenn.bingo.common.card.objective.ItemObjectiveManager
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.CardShuffledEvent
import me.jfenn.bingo.common.event.model.BingoTileCapturedEvent
import me.jfenn.bingo.common.event.model.ScoreChangedEvent
import me.jfenn.bingo.common.event.model.TeamWinnerEvent
import me.jfenn.bingo.common.game.GameCommands
import me.jfenn.bingo.common.game.GameEndReason
import me.jfenn.bingo.common.game.GameService
import me.jfenn.bingo.common.options.*
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.*
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ReloadEvent
import net.minecraft.util.Formatting
import org.slf4j.Logger
import java.time.Instant
import java.util.*
import kotlin.random.Random

/**
 * Searches for items in each player's inventory that can
 * satisfy a tile on the card, and updates the score if found.
 */
internal class ScoredItemCheck(
    private val log: Logger,
    private val options: BingoOptions,
    private val state: BingoState,
    private val gameService: GameService,
    private val cardService: CardService,
    private val scoreUpdateService: ScoreUpdateService,
    private val objectiveManager: BingoObjectiveManager,
    private val itemObjectiveManager: ItemObjectiveManager,
    private val playerManager: IPlayerManager,
    private val text: TextProvider,
    private val permission: IPermissionsApi,
    events: ScopedEvents,
    private val eventBus: IEventBus,
) : BingoComponent() {

    // Card completed packets should only be sent if the game continues and does not end
    // - these are stored in a map temporarily and are sent/cleared at the end of each tick
    private val pendingCardCompletedPackets = mutableMapOf<BingoTeamKey, TeamCompletedCard>()

    private val pendingTeamWinners = mutableSetOf<BingoTeamKey>()

    private fun updateScores(
        initialCard: BingoCard,
        tickStart: Instant,
        sendUpdates: Boolean,
    ) {
        var card = initialCard // card is mutable, as WinCondition.ReplaceGoals can occur during score counting
        val rootObjectives = card.entries.mapIndexedNotNull { index, entry ->
            card.objectives[entry.objectiveId]?.let { objective -> Triple(index, entry, objective) }
        }

        val teams = state.getRegisteredTeams()
            // if the team is already a winner, their score should not change
            .filter { it.isPlaying() && it.cardId == card.id }

        for (team in teams) {
            // iterate through all updated objectives on the card
            rootObjectives
                .takeIf { sendUpdates }
                // if the objective has been updated in this tick
                ?.filter { (_, _, objective) -> objective.updatedAt(team.key) as Any == tickStart as Any }
                ?.forEach { (entryIndex, _, objective) ->
                    if (objective.hasAchieved(team.key)) {
                        // find the player that captured the objective
                        val player = objective.players
                            .mapNotNull { (uuid, instant) ->
                                team.players.find { it.uuid == uuid }?.let { it to instant }
                            }
                            .maxByOrNull { (_, capture) -> capture.instant }
                            ?.first

                        log.info("[ScoredItemCheck] ${player?.name ?: team.id} got ${objective.id}")

                        // If consume items is enabled, attempt to remove the scored item from the player
                        // (incompatible if inventory mode is enabled or if lobby mode is _not_ enabled)
                        if (card.options.isConsumeItemsMode && !card.options.isInventoryMode && state.isLobbyMode) {
                            player?.uuid
                                ?.let { playerManager.getPlayer(it) }
                                ?.let { confiscateScoredItem(it, card, objective) }
                                ?: log.error("[ScoredItemCheck] Could not find the player that captured {} ({})", objective.id, player?.name)
                        }

                        scoreUpdateService.sendItemCaptured(
                            scoredPlayer = player,
                            team = team,
                            objective = objective,
                            card = card,
                        )
                        eventBus.emit(
                            BingoTileCapturedEvent,
                            BingoTileCapturedEvent(
                                gameId = state.gameId,
                                cardId = card.id,
                                team = team.key,
                                playerId = player?.uuid,
                                objectiveId = objective.id,
                                x = entryIndex % 5,
                                y = entryIndex / 5,
                            ),
                        )
                    } else {
                        scoreUpdateService.sendItemLost(
                            team = team,
                            objective = objective,
                            card = card,
                        )
                    }
                }

            // update the score for each modified team
            val scoreBefore = team.currentScore
            val scoreAfter = card.getTeamScore(team)

            if (scoreAfter != scoreBefore) {
                team.currentScore = scoreAfter
                team.scoreUpdatedAt = tickStart
                log.info("[ScoredItemCheck] Updated score for team {}: {}", team.id, scoreAfter)
                eventBus.emit(ScoreChangedEvent, ScoreChangedEvent(team.key, scoreBefore, scoreAfter))
            }

            // if the team has scored a line, post a message
            if (scoreAfter.lines > scoreBefore.lines && sendUpdates && card.options.goal is BingoGoal.Lines) {
                scoreUpdateService.sendLineCaptured(team)
            }
        }

        // if ReplaceGoals is enabled, replace any newly-captured items/lines

        // Collect any entries to replace
        val entriesToReplace = mutableSetOf<BingoCardEntry>()
        if (options.winCondition is BingoWinCondition.ReplaceGoals) {
            for (team in teams) {
                val scoredLines = card.lines()
                    .filter { line ->
                        // Select lines where the team has achieved every item
                        line.map { card.objectives[it.objectiveId] }
                            .all { it != null && it.hasAchieved(team.key) }
                    }
                    .toList()

                val scoredEntries = when (card.options.goal) {
                    // If the goal is lines, replace items from a scored line all at once
                    is BingoGoal.Lines -> scoredLines
                        .flatten()
                        .toList()
                    // If the goal is items, replace individual items as soon as they're scored
                    is BingoGoal.Items -> card.entries
                        .filter {
                            val objective = card.objectives[it.objectiveId]
                            objective != null && objective.hasAchieved(team.key)
                        }
                }.filterNot { entry ->
                    // Don't replace free spaces or inverse objectives
                    // TODO: these could be nested in one_of/opponent which would create odd behavior
                    val objective = card.objectives[entry.objectiveId]
                    objective is BingoObjective.FreeSpace || objective is BingoObjective.InverseEntry
                }

                entriesToReplace.addAll(scoredEntries)

                if (scoredEntries.isNotEmpty()) {
                    val addItems = scoredEntries.size
                    val addLines = scoredLines.count { line -> line.any { scoredEntries.contains(it) } }
                    val addScore = TeamScore.ZERO.copy(items = addItems, lines = addLines)

                    // Increment the persistent team score & reset currentScore to ZERO
                    team.persistentScore += addScore
                    team.currentScore = TeamScore.ZERO
                }
            }
        }

        // check if a team has completed the card
        for (team in teams) {
            val isWinner = when (val goal = card.options.goal) {
                is BingoGoal.Items -> team.score.items >= goal.items
                is BingoGoal.Lines -> team.score.lines >= goal.lines
            }

            if (isWinner && team.cardId == card.id) {
                log.info("[ScoredItemCheck] Team {} has completed their card", team.id)
                onCardCompleted(team, card, true)

                // Count the number of teams that have now finished the card
                val completedTeams = state.getRegisteredTeams().count {
                    it.completedCards.any { completed -> completed.card.id == card.id }
                }

                // If the number of teams satisfies the endWhen setting, other teams should move to the next card
                val shouldSkipCard = when (val endWhen = options.endGameWhen) {
                    is EndWhen.AllWin -> false
                    is EndWhen.TeamsWin -> completedTeams >= endWhen.teams
                    is EndWhen.FirstWin -> true
                    is EndWhen.Never -> false
                }

                if (shouldSkipCard) {
                    teams.filter { it.cardId == card.id }
                        .forEach { onCardCompleted(it, card, false) }
                }

                break
            }
        }

        // If the game has just ended, don't check for stalemates
        if (state.endedAt != null)
            return

        // Replace any entries that need changing on the card
        if (entriesToReplace.isNotEmpty()) {
            log.info("[ScoredItemCheck] Replacing {} entries on the card", entriesToReplace.size)

            // Recreate the card, replacing the scored entries
            card = cardService.replaceEntries(
                card = card,
                replaceEntries = entriesToReplace.toList(),
                excludeObjectives = objectiveManager.listExcludedIds().toSet(),
            ).also {
                objectiveManager.init(it)
                it.ticks = card.ticks
            }

            // Re-count current scores
            for (team in teams) {
                team.currentScore = card.getTeamScore(team)
            }
        }

        // skip the card if it is in a stalemate
        val stalemate = ScoreService.isStalemate(state, card)
            .takeIf { state.state == GameState.PLAYING }
        when (stalemate) {
            is StalemateReason.WinByStalemate -> {
                log.info("[ScoredItemCheck] Completing the current card because {} has won by stalemate", stalemate.team.key)
                onCardCompleted(stalemate.team, card, isWinner = true, isAutoWin = true)
            }
            is StalemateReason.Stalemate,
            is StalemateReason.Lockout,
            is StalemateReason.ImpossibleGoal -> {
                if (options.stalemateBehavior == StalemateBehavior.END_GAME) {
                    log.info("[ScoredItemCheck] Ending the game because the card has reached a stalemate (${stalemate})")
                    // if there's only one card, end the game when there's a lockout
                    gameService.end(
                        when (stalemate) {
                            StalemateReason.Lockout -> GameEndReason.Lockout
                            StalemateReason.ImpossibleGoal -> GameEndReason.ImpossibleGoal
                            else -> GameEndReason.Stalemate
                        }
                    )
                } else if (options.stalemateBehavior == StalemateBehavior.REROLL_CARD) {
                    log.info("[ScoredItemCheck] Skipping the current card because it has reached a stalemate (${stalemate})")
                    // otherwise, skip to the next card when a lockout occurs
                    teams.filter { it.cardId == card.id }
                        .forEach { onCardCompleted(it, card, false) }

                    // Notify players that the game is continuing because of stalemateBehavior
                    val message = text.string(
                        StringKey.GameNotEndingStalemateNewCard,
                        StalemateBehavior.END_GAME.string(text),
                    )
                        .formatted(Formatting.AQUA, Formatting.BOLD)
                    playerManager.getPlayers()
                        .filter { player -> teams.any { it.includesPlayer(player) } }
                        .forEach { it.sendMessage(message) }
                }
            }
            null -> {}
        }

        // we want ticks to stop at Int.MAX_VALUE so that it does not overflow into a negative
        if (card.ticks < Int.MAX_VALUE)
            card.ticks++
    }

    /**
     * Find the innermost objective that was captured (descending into opponent, inverse, some_of, or all_of objective types)
     */
    private fun getInnerCapturedObjectives(
        player: IPlayerHandle,
        card: BingoCard,
        objective: BingoObjective,
        recursionLimit: Int = 10,
    ): Sequence<BingoObjective> = sequence {
        if (recursionLimit <= 0) {
            yield(objective)
            return@sequence
        }

        val capturedObjectives: Sequence<BingoObjective> = when (objective) {
            is BingoObjective.SomeOfEntry -> objective.someOfObjectives
                .asSequence()
                .mapNotNull { card.objectives[it] }
                .filter { it.playersHolding.containsKey(player.uuid) }
                .sortedByDescending { it.playersHolding[player.uuid]?.instant ?: Instant.MIN }
                // take only the most recently captured objectives that count towards the goal
                .take(objective.valueMin.coerceAtLeast(1))
            is BingoObjective.InverseEntry -> sequenceOf(card.objectives[objective.inverseObjective]).filterNotNull()
            is BingoObjective.OpponentEntry -> emptySequence()
            else -> sequenceOf(objective)
        }

        for (capturedObjective in capturedObjectives) {
            when (capturedObjective) {
                is BingoObjective.SomeOfEntry,
                is BingoObjective.InverseEntry,
                is BingoObjective.OpponentEntry -> {
                    yieldAll(getInnerCapturedObjectives(player, card, capturedObjective, recursionLimit-1))
                }
                else -> yield(capturedObjective)
            }
        }
    }

    private fun confiscateScoredItem(
        player: IPlayerHandle,
        card: BingoCard,
        objective: BingoObjective,
    ) {
        getInnerCapturedObjectives(player, card, objective)
            .filterIsInstance<BingoObjective.ItemEntry>()
            .forEach { itemObjectiveManager.confiscateScoredItem(player, it) }
    }

    private fun onCardCompleted(team: BingoTeam, card: BingoCard, isWinner: Boolean, isAutoWin: Boolean = false) {
        val now = state.updatedAt ?: Instant.now()

        val completedCard = team.completedCards.find { it.card.id == card.id } ?: run {
            TeamCompletedCard(
                // Copy the card in its winning state
                card = card.copy().also { objectiveManager.init(it) },
                completedAt = now,
                isWinner = isWinner,
                isAutoWin = isAutoWin,
                score = team.score,
            ).also {
                team.completedCards += it
            }
        }

        val requiredCards = when (
            val condition = options.winCondition
        ) {
            is BingoWinCondition.Cards -> condition.cards
            is BingoWinCondition.Infinite -> null
            is BingoWinCondition.ReplaceGoals -> 1
        }
        if (requiredCards != null && team.countCards() >= requiredCards) {
            // the team has won the game
            team.winner = TeamWinner(now)
            team.cardId = null
            // tells [PlayerController] to run updateGameMode which should place winning teams in spectator
            pendingTeamWinners.add(team.key)
        } else {
            // If the team is supposed to get a new card, check that it has >3 ticks
            // (this relates to init() calling the tick function 3 times)
            // - if the card is immediately achieved, we should end the game because this might be an infinite loop
            if (card.ticks <= 3) {
                val cardGoal = card.options.goal.format(text)
                val cardScore = when (card.options.goal) {
                    is BingoGoal.Items -> text.itemCount(team.score.items)
                    is BingoGoal.Lines -> text.lineCount(team.score.lines)
                }
                val message = text.string(StringKey.CommandStartWillImmediatelyEnd, cardGoal, cardScore).formatted(Formatting.RED)
                for (player in playerManager.getPlayers()) {
                    player.sendMessage(message)
                }

                log.error("[ScoredItemCheck] Ending the game because a card was completed immediately")
                log.error(" - This is done to avoid repeatedly creating new cards in a loop. You should check the 'Goal' settings to make sure that cards aren't completed as soon as the game starts.")

                state.isForfeit = true
                gameService.end(null)
                return
            }

            val nextCard = card.nextCardId?.let { state.getCard(it) }
            team.cardId = nextCard?.id

            if (nextCard != null) {
                onCardAssigned(nextCard, listOf(team))
            }
        }

        // announce that another team has completed their card
        pendingCardCompletedPackets[team.key] = completedCard
    }

    private fun tickCard(card: BingoCard) {
        if (state.endedAt != null) return
        val tickStart = state.updatedAt ?: return

        // Update all objectives
        objectiveManager.tick(card)

        updateScores(
            initialCard = card,
            tickStart = tickStart,
            sendUpdates = true,
        )
    }

    private fun postTickCards(
        prevLeadingTeam: BingoTeam?,
    ) {
        val cardCompletedPackets = pendingCardCompletedPackets.toList()
        pendingCardCompletedPackets.clear()

        val teamWinners = pendingTeamWinners.toList()
        pendingTeamWinners.clear()

        if (state.endedAt != null) return

        val teams = state.getRegisteredTeams()

        // check if the game should end
        val shouldEnd = when (
            val endWhen = options.endGameWhen
        ) {
            is EndWhen.Never -> false
            is EndWhen.FirstWin -> teams.any { it.isWinner() }
            is EndWhen.TeamsWin -> teams.count { it.isWinner() } >= endWhen.teams
            is EndWhen.AllWin -> teams.all { it.isWinner() }
        }

        if (shouldEnd) {
            log.info("[ScoredItemCheck] Ending the game because ${options.endGameWhen} was satisfied")
            gameService.end(GameEndReason.Bingo)
            return
        }

        val finishedTeams = cardCompletedPackets
            .mapNotNull { (teamKey, _) -> state.teams[teamKey] }
            .filter { it.isWinner() }

        // Notify players that the game is not ending because of endGameWhenComplete
        val message = text.string(StringKey.GameNotEndingComplete)
            .formatted(Formatting.AQUA, Formatting.BOLD)
        val messageOp = text.string(
            StringKey.GameNotEndingCompleteOperator,
            options.endGameWhen.string(text),
            GameCommands.END_COMMAND,
        ).formatted(Formatting.GRAY)
        playerManager.getPlayers()
            .filter { player -> finishedTeams.any { it.includesPlayer(player) } }
            .forEach { player ->
                player.sendMessage(message)
                if (permission.hasPermission(player, Permission.CONFIGURE_GAME)) {
                    player.sendMessage(messageOp)
                }
            }

        // otherwise, send any pending card completed packets
        for ((teamKey, completedCard) in cardCompletedPackets) {
            val team = state.teams[teamKey] ?: continue
            scoreUpdateService.sendCardCompleted(team, completedCard)
        }

        // check if the leading team has changed, and announce the leader
        val leadingTeam = ScoreService.getLeading(state)
        if (prevLeadingTeam != leadingTeam && options.showLeadingTeam && teams.size > 1) {
            // if the leading team should be shown, announce the new leader
            if (leadingTeam != null) {
                scoreUpdateService.sendTeamLeading(leadingTeam)
            } else {
                // Find the tied team that was most recently updated
                val tiedTeam = ScoreService.getLeading(state, allowTies = true)
                if (tiedTeam != null) {
                    val comparator = ScoreService.getComparator(state)
                    teams.filter { comparator.compare(it, tiedTeam) == 0 }
                        .maxByOrNull { it.scoreUpdatedAt }
                        ?.let { scoreUpdateService.sendTeamTied(it) }
                }
            }
        }

        // if a team overruns / is missing a card, assign one!
        val teamsWithoutCards = teams
            .filter { !it.isWinner() }
            .filter { team -> team.cardId == null || state.cards.none { it.id == team.cardId } }

        if (teamsWithoutCards.isNotEmpty()) {
            val newCard = cardService.generate(
                id = UUID.randomUUID(),
                seed = Random.nextLong(),
                cardOptions = state.cards.last().options.copy(),
                excludeObjectives = objectiveManager.listExcludedIds().toSet(),
            )

            state.pushCardTail(newCard)

            teamsWithoutCards.forEach {
                it.cardId = newCard.id
            }

            onCardAssigned(newCard, teamsWithoutCards)

            eventBus.emit(CardShuffledEvent, CardShuffledEvent(newCard.id))
        }

        // Only run onTeamWinner events after the card tick is complete
        // and we know that the game is still running
        teamWinners
            .mapNotNull { state.teams[it] }
            .forEach { eventBus.emit(TeamWinnerEvent, TeamWinnerEvent(it)) }
    }

    private fun onCardAssigned(card: BingoCard, removeTeams: List<BingoTeam>) {
        objectiveManager.init(card)

        for (team in removeTeams) {
            state.cards.forEach { it.removeTeam(team) }
        }

        // Run one tick when the game starts
        // - this prevents notification spam for tiles that the player has already captured at the start of the game
        objectiveManager.tick(card)
        objectiveManager.tick(card)
        objectiveManager.tick(card)

        updateScores(
            initialCard = card,
            tickStart = Instant.MIN,
            sendUpdates = false,
        )
    }

    init {
        // Re-initialize completed cards when loading from a saved state
        for (team in state.getRegisteredTeams()) {
            for (completion in team.completedCards) {
                objectiveManager.init(completion.card)
            }
        }

        eventBus.register(ReloadEvent.After) {
            cardService.validateLists()
        }

        events.onGameTick {
            // Team cards might have their own (copied) instances that don't get initialized right away
            state.getRegisteredTeams()
                .mapNotNull { state.getCard(it) }
                .map { objectiveManager.init(it) }

            for (card in state.cards) {
                objectiveManager.init(card)
            }

            if (state.state == GameState.PLAYING) {
                val prevLeadingTeam = ScoreService.getLeading(state)

                for (card in state.cards) {
                    val isActive = state.teams.values.any {
                        it.isPlaying() && it.cardId == card.id
                    }
                    if (isActive) {
                        tickCard(card)
                    }
                }

                postTickCards(prevLeadingTeam)
            }
        }

        events.onEnter(GameState.PREGAME) {
            pendingCardCompletedPackets.clear()
            pendingTeamWinners.clear()

            // When entering PREGAME, regenerate initial cards from the config
            cardService.createInitialCards()
        }

        events.onEnter(GameState.PLAYING) {
            for (card in state.cards) {
                onCardAssigned(
                    card = card,
                    removeTeams = emptyList(),
                )
            }
        }

        events.onChangeTeam { (player) ->
            // Remove any of the player's held/scored items from the card
            for (card in state.cards) {
                card.removePlayer(player.uuid)
            }
        }
    }

}
