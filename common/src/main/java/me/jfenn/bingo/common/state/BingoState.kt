package me.jfenn.bingo.common.state

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.game.GameOverService
import me.jfenn.bingo.common.map.BingoMap
import me.jfenn.bingo.common.menu.MenuPage
import me.jfenn.bingo.common.menu.MenuState
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.BingoWinCondition
import me.jfenn.bingo.common.options.RestoreOption
import me.jfenn.bingo.common.scoring.GameMessage
import me.jfenn.bingo.common.spawn.PlayerState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.*
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IMapService
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.utils.UuidAsString
import net.minecraft.util.Formatting
import java.time.Duration
import java.util.*

@Serializable
@ConsistentCopyVisibility
data class BingoState internal constructor(
    var isLobbyMode: Boolean = false,
    var gameId: UuidAsString = UUID.randomUUID(),
    var state: GameState = GameState.UNINITIALIZED,
    val menu: MenuState = MenuState(),
    var startedAt: InstantType? = null,
    var updatedAt: InstantType? = null,
    var endedAt: InstantType? = null,
    var lobbySpawnPos: BlockPositionType = BlockPosition(0, 0, 0),
    var lobbySpawnYaw: Float = 0f,
    var timeOffline: DurationType = Duration.ZERO,
    var timeAdjustment: DurationType = Duration.ZERO,
    val options: BingoOptions,
    val restoreOptions: MutableList<RestoreOption> = mutableListOf(),
    val teams: MutableMap<BingoTeamKey, BingoTeam> = mutableMapOf(),
    var previewMap: BingoMap? = null,
    // This is a temporary list that should be overwritten by `CardService.generate()`
    val cards: MutableList<BingoCard> = mutableListOf(),
    internal var gameOverInfo: GameOverService.GameOverInfo? = null,
    var gameMessages: MutableList<GameMessage> = mutableListOf(),
    /**
     * Stores a set of recently-joined player UUIDs for PlayerController
     */
    val playersJoinedIds: MutableSet<UuidAsString> = mutableSetOf(),
    /**
     * Stores player data tracked to determine when respawning/clearing inventory is needed
     * (in PlayerController)
     */
    val players: MutableMap<UuidAsString, PlayerState> = mutableMapOf(),
    /**
     * Stores players that have opted into spectating with "/join spectators"
     * (will not be warned when starting the game, do not block ready-up)
     */
    val playersSpectatingIds: MutableSet<UuidAsString> = mutableSetOf(),
    /**
     * Stores if the game was ended manually (with "/bingo end")
     */
    var isForfeit: Boolean = false,
) {

    fun getActiveCard(): BingoCard {
        return cards.firstOrNull()
            ?: throw IllegalArgumentException("No cards in state")
    }

    /**
     * Replace any assigned team cards with the default behavior (first card)
     */
    fun resetTeamCards() {
        for (team in teams.values) {
            team.cardId = cards.first().id
        }
    }

    fun pushCard(card: BingoCard) {
        val nextCard = cards.firstOrNull()
        card.nextCardId = nextCard?.id
        cards.add(0, card)
    }

    fun pushCardTail(card: BingoCard) {
        cards.lastOrNull()?.nextCardId = card.id
        card.nextCardId = null
        cards.add(card)
    }

    fun popCard() {
        if (cards.size == 1) return

        val removedCard = cards.removeFirst()

        val firstCard = cards.first()
        for (team in teams.values) {
            if (team.cardId == removedCard.id)
                team.cardId = firstCard.id
        }
    }

    fun replaceCard(card: BingoCard) {
        val index = cards.indexOfFirst { it.id == card.id }
            .takeIf { it != -1 }
            ?: throw IllegalArgumentException("Unknown card provided in replaceCard")

        cards[index] = card
    }

    fun getCard(team: BingoTeam): BingoCard? {
        return if (team.isWinner() || state == GameState.POSTGAME) {
            team.completedCards.lastOrNull()?.card
        } else {
            team.cardId?.let { cardId -> cards.find { it.id == cardId } }
        }
    }

    fun getCard(id: UUID): BingoCard? {
        return cards.find { it.id == id }
    }

    /**
     * Returns true if there is only one player in the game
     */
    fun isSingleplayer() = teams.values.flatMap { it.players }
        .distinctBy { it.uuid }
        .count() == 1

    fun getRegisteredTeams(): List<BingoTeam> {
        return teams.values.filter { it.players.isNotEmpty() }
    }

    fun registerTeam(team: BingoTeam): BingoTeam {
        return teams.getOrPut(team.key) {
            team.newInstance().apply {
                cardId = cards.first().id
            }
        }
    }

    fun getPreviewMap(mapService: IMapService): BingoMap {
        return previewMap ?: run {
            val map = BingoMap(
                mapId = mapService.getNextMapId(),
            )
            previewMap = map
            return@run map
        }
    }

    fun changeState(eventBus: IEventBus, newState: GameState) {
        val prevState = this.state
        this.state = newState
        eventBus.emit(
            StateChangedEvent,
            StateChangedEvent(
                from = prevState,
                to = newState
            )
        )
    }

    fun ingameDuration(): Duration? {
        val started = startedAt ?: return null
        val updated = endedAt ?: updatedAt ?: return null
        val duration = Duration.between(started, updated)
        // subtract any offline server time from the game duration
        return duration + timeAdjustment - timeOffline
    }

    fun remainingDuration(): Duration? {
        val timeLimit = options.timeLimit ?: return null
        val duration = ingameDuration() ?: return null
        return timeLimit - duration
    }

    fun formatTimeElapsed(): String {
        val duration = ingameDuration() ?: return "0:00"
        return duration.formatHHMMSS()
    }

    fun formatTimeRemaining(
        textProvider: TextProvider,
        normalColor: Formatting = Formatting.WHITE,
        alertColor: Formatting = Formatting.RED,
    ): IText {
        val timeLimit = options.timeLimit
            ?: return textProvider.string(StringKey.TimerNoTimeLimit)

        if (startedAt == null) return textProvider.literal(timeLimit.formatString()).formatted(normalColor)
        val secondsRemaining = remainingDuration() ?: return textProvider.literal(timeLimit.formatString()).formatted(normalColor)

        if (secondsRemaining <= Duration.ZERO || state == GameState.POSTGAME)
            return textProvider.string(StringKey.TimerGameOver)

        return textProvider.literal(secondsRemaining.formatString())
            .formatted(if (secondsRemaining.toSeconds() <= 30 && secondsRemaining.toSeconds() % 2 == 0L) { alertColor } else { normalColor })
    }

    fun formatCardGoals(team: BingoTeam?, text: TextProvider): IText {
        val card = cards.find { it.id == team?.cardId } ?: cards.first()
        val supportsFullCard = options.winCondition !is BingoWinCondition.ReplaceGoals
        val remainingCards = (options.winCondition as? BingoWinCondition.Cards)
            ?.cards
            ?.minus(1)
            ?.minus(team?.countCards() ?: 0)
            ?.takeIf { it > 0 }

        return card.options.goal.format(text, supportsFullCard)
            .also {
                if (remainingCards != null)
                    it.append(text.literal(" +$remainingCards").formatted(Formatting.BOLD))
                if (options.winCondition is BingoWinCondition.Infinite)
                    it.append(text.literal(" + ∞"))
            }
    }

    fun formatCardGoalsDescriptive(team: BingoTeam?, text: TextProvider): IText {
        val card = cards.find { it.id == team?.cardId } ?: cards.first()
        val supportsFullCard = options.winCondition !is BingoWinCondition.ReplaceGoals
        val remainingCards = (options.winCondition as? BingoWinCondition.Cards)
            ?.cards
            ?.minus(1)
            ?.minus(team?.countCards() ?: 0)
            ?.takeIf { it > 0 }

        return card.options.goal.format(text, supportsFullCard = supportsFullCard, descriptive = true)
            .also {
                if (remainingCards != null)
                    it.append(text.literal(" + ")
                        .append(text.cardCount(remainingCards))
                        .formatted(Formatting.BOLD))
                if (options.winCondition is BingoWinCondition.Infinite)
                    it.append(text.literal(" + ∞"))
            }
    }

    /**
     * If a game is over & we want to reset this to its default state
     *
     * This must be called after each game, before transitioning back to [GameState.PREGAME]!
     */
    fun reset() {
        // set a new gameId
        gameId = UUID.randomUUID()
        startedAt = null
        updatedAt = null
        endedAt = null
        timeOffline = Duration.ZERO
        timeAdjustment = Duration.ZERO
        teams.clear()
        playersSpectatingIds.clear()
        previewMap = null
        // [ScoredItemCheck] calls createInitialCards when entering PREGAME, so this card should be immediately replaced
        cards.clear()
        gameOverInfo = null
        gameMessages.clear()
        isForfeit = false
        menu.page = MenuPage.ROOT
        // restore any temporary options to their initial state
        restoreOptions.forEach { it.apply(options) }
        restoreOptions.clear()
    }

}