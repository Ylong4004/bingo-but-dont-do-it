package me.jfenn.bingo.common.commands

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.card.objective.BingoObjectiveManager
import me.jfenn.bingo.common.card.objective.ObjectiveListService
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.model.CardShuffledEvent
import me.jfenn.bingo.common.event.model.OptionsChangedEvent
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.options.optionsContext
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.CommandArgument
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.commands.IExecutionSource
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.text.Texts
import net.minecraft.util.Formatting

class BingoCardCommand(
    commandManager: ICommandManager,
    private val config: BingoConfig,
    private val text: TextProvider,
    private val eventBus: IEventBus,
) : BingoComponent() {

    companion object {
        private val CARD_KEYS = "bingo".toCharArray()
            .flatMap { col -> (1..5).map { "$col$it" } }
            .plus("random")

        private val CARD_POSITIONS = buildMap {
            for (x in 0..4) for (y in 0..4) {
                this[tileName(x, y)] = Pair(x, y)
            }
        }

        private fun tileName(x: Int, y: Int) : String {
            val text = "bingo".toCharArray()
            val row = y+1
            return "${text[x]}$row"
        }
    }

    private fun IExecutionSource.hasConfigureGame() = hasState(GameState.PREGAME, GameState.PLAYING) && hasPermission(Permission.CONFIGURE_GAME)
    private val IExecutionContext.state get() = scope.get<BingoState>()
    private val IExecutionContext.cardService get() = scope.get<CardService>()
    private val IExecutionContext.cardViewService get() = scope.get<CardViewService>()

    private fun getCardPosition(tile: String): Pair<Int, Int> = when (tile) {
        "random" -> CARD_POSITIONS.values.random()
        else -> CARD_POSITIONS[tile] ?:
            throw IllegalArgumentException("'$tile' is not a valid bingo tile position: ${CARD_POSITIONS.keys}")
    }

    private fun IExecutionContext.setObjective(tile: String, objectiveId: String) {
        val (x, y) = getCardPosition(tile)
        val originalCard = cardViewService.getPlayerCard(player)
        cardService.replaceEntry(originalCard, x, y, objectiveId)
        sendFeedback(text.string(StringKey.CommandCardSetSuccess, tileName(x, y), objectiveId))
    }

    private fun IExecutionContext.setFreeSpace(tile: String) {
        val (x, y) = getCardPosition(tile)
        val originalCard = cardViewService.getPlayerCard(player)
        cardService.replaceEntry(originalCard, x, y, BingoObjective.FreeSpace())
        sendFeedback(text.string(StringKey.CommandCardSetSuccess, tileName(x, y), StringKey.CardFreeSpace))
    }

    private fun IExecutionContext.setRandom(tile: String) {
        val (x, y) = getCardPosition(tile)
        val card = cardViewService.getPlayerCard(player)
        val entryToReplace = card.entry(x, y)

        val newCard = cardService.replaceEntries(
            card = card,
            replaceEntries = listOf(entryToReplace),
            excludeObjectives = scope.get<BingoObjectiveManager>().listExcludedIds().toSet(),
        )

        val newEntry = newCard.entry(x, y)
        sendFeedback(text.string(StringKey.CommandCardSetSuccess, tileName(x, y), newEntry.objectiveId))
    }

    private fun IExecutionContext.getCardSeed() {
        val card = cardViewService.getPlayerCard(player)
        sendFeedback(
            text.string(
                StringKey.CommandCardSeedGet,
                Texts.bracketedCopyable(card.seed.toString()),
            )
        )
    }

    private fun IExecutionContext.setCardSeed(seed: Long) {
        val card = cardViewService.getPlayerCard(player)
        val newCard = cardService.generateCard(card, seed = seed)
        sendFeedback(
            text.string(
                StringKey.CommandCardSeedSet,
                Texts.bracketedCopyable(newCard.seed.toString()),
            )
        )
    }

    private fun IExecutionContext.setItemDist(itemDistInput: List<Int>) {
        scope.get<OptionsService>().setCardDifficulty(
            ctx = optionsContext,
            itemDistInput = itemDistInput,
        )
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    private fun IExecutionContext.rerollCard() {
        val card = cardViewService.getPlayerCard(player)
        val excludedObjectives = if (hasState(GameState.PLAYING)) {
            scope.get<BingoObjectiveManager>().listExcludedIds().toSet()
        } else {
            emptySet()
        }
        cardService.shuffleCard(card, excludedObjectives)
        sendFeedback(text.string(StringKey.OptionsRerollCardSuccess))
        eventBus.emit(CardShuffledEvent, CardShuffledEvent(card.id))
    }

    private fun IExecutionContext.getCardTags() = buildMap {
        for ((i, card) in state.cards.withIndex()) {
            this["#${i+1}"] = card
        }
    }

    private fun IExecutionContext.getCardStack() {
        sendMessage(text.literal("Bingo Cards:").formatted(Formatting.GRAY))

        for ((i, card) in state.cards.withIndex()) {
            val teams = state.getRegisteredTeams()
                .filter { it.cardId == card.id }
                .map { it.getName(text, symbol = false, teamNameKey = null) }

            val nextCardIndex = state.cards.indexOfFirst { it.id == card.nextCardId }
                .takeIf { it != -1 }

            val mode = card.options.formatGameMode()
                .map { text.string(it) }
                .let { text.joinText(it) }

            val goal = card.options.goal.format(text)

            val itemDist = card.options.formatItemDist(text)

            buildList {
                add(text.literal("  #${i+1}"))
                add(itemDist)
                add(mode)
                add(goal)
                if (teams.isNotEmpty()) {
                    add(text.joinText(teams, text.literal(" ")))
                }
                if (nextCardIndex != null && nextCardIndex != i+1) {
                    add(text.literal(" -> #${nextCardIndex+1}"))
                }
            }
                .let {
                    text.joinText(it, text.literal(" | ").formatted(Formatting.GRAY))
                }
                .also { sendMessage(it) }
        }
    }

    private fun IExecutionContext.assignTeamCard(teamLabel: String, cardTag: String) {
        val team = state.teams.values
            .find { it.key.id == teamLabel || it.key.label == teamLabel }
            ?: throw IllegalArgumentException("Could not find team '$teamLabel'")

        val card = getCardTags()[cardTag]
            ?: throw IllegalArgumentException("Could not find card '$cardTag'")

        team.cardId = card.id
        sendFeedback(
            text.empty()
                .append("Team ")
                .append(team.getName(text))
                .append(" assigned to card $cardTag")
        )
    }

    init {
        commandManager.register("bingo") {
            literal("card") {
                executes {
                    cardViewService.openCard(playerOrThrow)
                }

                literal("set") {
                    requires { hasConfigureGame() }
                    string("tile", { CARD_KEYS }) { tileArg ->
                        string(
                            name = "objective",
                            suggestions = { scope.get<ObjectiveListService>().getAllObjectives() },
                            greedy = true,
                        ) { objectiveArg ->
                            executes {
                                val tile = getArgument(tileArg).lowercase()
                                val objectiveId = getArgument(objectiveArg)
                                setObjective(tile, objectiveId)
                            }
                        }
                        literal("random") {
                            executes {
                                setRandom(getArgument(tileArg).lowercase())
                            }
                        }
                        literal("free_space") {
                            executes {
                                setFreeSpace(getArgument(tileArg).lowercase())
                            }
                        }
                    }
                }

                literal("seed") {
                    requires { hasConfigureGame() }
                    executes { getCardSeed() }

                    long("seed") { seedArg ->
                        executes { setCardSeed(getArgument(seedArg)) }
                    }
                }

                literal("assign") {
                    requires { hasConfigureGame() }
                    string(
                        "team",
                        { state.teams.keys.map { it.id } + state.teams.keys.map { it.label } }
                    ) { teamArg ->
                        string("card", { getCardTags().keys }, greedy = true) { cardArg ->
                            executes {
                                val teamLabel = getArgument(teamArg)
                                val cardTag = getArgument(cardArg)
                                assignTeamCard(teamLabel, cardTag)
                            }
                        }
                    }
                }

                literal("stack") {
                    requires { hasConfigureGame() }
                    executes { getCardStack() }

                    literal("push") {
                        requires { hasConfigureGame() && hasState(GameState.PREGAME) }
                        executes {
                            val prevCard = state.getActiveCard()
                            val newCard = cardService.newCard(prevCard.options.copy())
                                .also { state.pushCard(it) }

                            state.teams.values
                                .filter { it.cardId == null || it.cardId == prevCard.id }
                                .forEach { it.cardId = newCard.id }
                        }
                    }

                    literal("pop") {
                        requires { hasConfigureGame() && hasState(GameState.PREGAME) }
                        executes { state.popCard() }
                    }
                }
            }

            literal("difficulty") {
                requires { hasConfigureGame() }

                var builder = this@literal
                val args = mutableListOf<CommandArgument<Int>>()
                for (tier in TierLabel.entries) {
                    builder.integer(tier.name, 0, 25) {
                        args.add(it)
                        builder = this@integer
                    }
                }

                builder.executes {
                    setItemDist(args.map { getArgument(it) })
                }

                for ((name, difficulty) in config.difficultyPresets) {
                    literal(name) {
                        executes { setItemDist(difficulty) }
                    }
                }
            }

            literal("reroll") {
                requires { hasConfigureGame() }
                executes { rerollCard() }
            }
        }
    }

}