package me.jfenn.bingo.common.options

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.card.filter.ObjectiveFilterService
import me.jfenn.bingo.common.card.tierlist.TierLabel
import me.jfenn.bingo.common.commands.BingoOptionsCommands
import me.jfenn.bingo.common.controller.GameRuleController
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.spawn.ElytraService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatTitle
import me.jfenn.bingo.common.utils.minutes
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextFactory
import me.jfenn.bingo.platform.text.TextAction
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.time.Duration
import java.util.*
import kotlin.reflect.KMutableProperty1

internal val IExecutionContext.optionsContext
    get() = OptionsService.Context(
        player = player,
        receiveFeedback = ::sendFeedback,
        receiveError = ::error,
    )

internal class OptionsService(
    private val state: BingoState,
    private val options: BingoOptions,
    private val cardService: CardService,
    private val cardViewService: CardViewService,
    private val elytraService: ElytraService,
    private val objectiveFilterService: ObjectiveFilterService,
    private val gameRuleController: GameRuleController,
    private val teamService: TeamService,
    private val playerManager: IPlayerManager,
    private val text: TextProvider,
    private val textFactory: ITextFactory,
) {
    data class Context(
        val player: IPlayerHandle?,
        val receiveFeedback: ((IText) -> Unit)? = null,
        val receiveError: ((IText) -> Nothing)? = null,
    )

    fun broadcastHotbarMessage(player: IPlayerHandle, message: IText) {
        playerManager.getPlayers()
            .filterNot { it.uuid == player.uuid }
            .forEach {
                it.sendHotbarMessage(
                    textFactory.translatable("chat.type.admin", null, player.playerName, message)
                        .formatted(Formatting.GRAY, Formatting.ITALIC)
                )
            }
    }

    fun Context.sendFeedback(message: IText) {
        receiveFeedback?.invoke(message)
            ?: player?.let { player ->
                broadcastHotbarMessage(player, message)
            }
    }

    fun Context.error(message: IText): Nothing {
        receiveError?.invoke(message)
        player?.sendMessage(message)
        throw IllegalArgumentException(message.toString())
    }

    fun setGoal(
        ctx: Context,
        card: BingoCard = cardViewService.getPlayerCard(ctx.player),
        goal: BingoGoal,
    ) {
        // If ReplaceGoals is enabled, we don't need to limit max items/lines...
        val supportsFullCard = options.winCondition !is BingoWinCondition.ReplaceGoals
        val coercedGoal = if (supportsFullCard) {
            when (goal) {
                is BingoGoal.Lines -> BingoGoal.Lines(goal.lines.coerceAtMost(BingoGoal.MAX_LINES))
                is BingoGoal.Items -> BingoGoal.Items(goal.items.coerceAtMost(BingoGoal.MAX_ITEMS))
            }
        } else goal

        card.options.goal = coercedGoal
        // Send a message that the goal was changed
        val goalName = coercedGoal.format(
            textProvider = text,
            supportsFullCard = supportsFullCard,
            descriptive = true,
        )
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsGoal, goalName.formatted(Formatting.WHITE))
        )
    }

    fun setCardDifficulty(
        ctx: Context,
        card: BingoCard = cardViewService.getPlayerCard(ctx.player),
        itemDistInput: List<Int>,
        allowInvalid: Boolean = false,
    ) {
        val itemDist = List(5) { itemDistInput.getOrNull(it) ?: 0 }

        if (itemDist.sum() > 25 && !allowInvalid) {
            ctx.error(text.string(StringKey.CommandStartInvalidDistribution))
        }

        card.options.itemDistribution = itemDist
        // when changed, update the bingo card
        if (card.options.isValid()) {
            cardService.generateCard(card)
        }
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsCardDifficulty, card.options.formatItemDist(text))
        )
    }

    fun setCardFilter(
        ctx: Context,
        card: BingoCard = cardViewService.getPlayerCard(ctx.player),
        filter: ObjectiveFilterList,
        includePresetDetails: Boolean = false
    ) {
        card.options.itemFilter = filter
        // when changed, update the bingo card
        if (card.options.isValid()) {
            cardService.generateCard(card)
        }
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsFilter, objectiveFilterService.formatFilter(filter, includePresetDetails).formatted(Formatting.WHITE))
        )
    }

    fun toggleCardMode(
        ctx: Context,
        card: BingoCard = cardViewService.getPlayerCard(ctx.player),
        prop: KMutableProperty1<BingoCardOptions, Boolean>,
        value: Boolean? = null,
    ) {
        val newValue = value ?: (!prop.get(card.options))
        prop.set(card.options, newValue)

        // inventory mode & consume items mode are not compatible
        if (prop == BingoCardOptions::isConsumeItemsMode) {
            card.options.isInventoryMode = false
        }
        if (prop == BingoCardOptions::isInventoryMode) {
            card.options.isConsumeItemsMode = false
        }

        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.OptionsGameMode,
                textFactory.joinText(
                    options.formatGameMode(card)
                        .map { text.string(it) }
                ).formatted(Formatting.WHITE),
            )
        )
    }

    fun setWinCondition(
        ctx: Context,
        winCondition: BingoWinCondition,
    ) {
        options.winCondition = winCondition

        // Send a message that the goal was changed
        val describe = when (winCondition) {
            is BingoWinCondition.Cards -> text.cardCount(winCondition.cards)
            is BingoWinCondition.Infinite -> text.string(StringKey.OptionsWinConditionInfinite)
            is BingoWinCondition.ReplaceGoals -> text.string(StringKey.OptionsWinConditionReplaceGoals)
        }
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsWinCondition, describe.formatted(Formatting.WHITE))
        )
    }

    fun setStalemateBehavior(
        ctx: Context,
        stalemateBehavior: StalemateBehavior,
    ) {
        options.stalemateBehavior = stalemateBehavior

        // Send a message that the goal was changed
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsWinBehaviorWhenStalemate, text.string(stalemateBehavior.string).formatted(Formatting.WHITE))
        )
    }

    fun setEndWhen(
        ctx: Context,
        endWhen: EndWhen,
    ) {
        options.endGameWhen = endWhen
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsWinBehaviorEndWhen, endWhen.string(text).formatted(Formatting.WHITE))
        )
    }

    fun toggleKeepInventory(
        ctx: Context,
        isKeepInventory: Boolean = !options.isKeepInventory,
    ) {
        options.isKeepInventory = isKeepInventory
        gameRuleController.writeToServer()
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsKeepInventory, text.boolean(isKeepInventory))
        )
    }

    fun togglePreviewCard(
        ctx: Context,
        showPreviewCard: Boolean? = null,
    ) {
        options.showPreviewCard = showPreviewCard ?: !options.showPreviewCard
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsPreviewCard, text.boolean(options.showPreviewCard))
        )
    }

    fun toggleElytra(
        ctx: Context,
        isElytra: Boolean? = null,
    ) {
        options.isElytra = isElytra ?: !options.isElytra

        // if Elytra mode is turned off during a game, remove all players' elytra
        if (!options.isElytra && state.isLobbyMode) {
            for (player in playerManager.getPlayers()) {
                elytraService.takeElytra(player)
            }
        }

        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsElytra, text.boolean(options.isElytra))
        )
    }

    fun toggleNightVision(
        ctx: Context,
        isNightVision: Boolean? = null,
    ) {
        options.isNightVision = isNightVision ?: !options.isNightVision

        // if night vision is changed, update all players
        if (state.state == GameState.PLAYING) {
            playerManager.getPlayers().forEach { player ->
                state.playersJoinedIds.add(player.uuid)
            }
        }

        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsNightVis, text.boolean(options.isNightVision))
        )
    }

    fun togglePvp(
        ctx: Context,
        isPvpEnabled: Boolean? = null,
    ) {
        options.isPvpEnabled = isPvpEnabled ?: !options.isPvpEnabled
        gameRuleController.writeToServer()
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsAllowPvp, text.boolean(options.isPvpEnabled))
        )
    }

    fun toggleUnlockRecipes(
        ctx: Context,
        isUnlockRecipes: Boolean = !options.isUnlockRecipes,
    ) {
        options.isUnlockRecipes = isUnlockRecipes
        ctx.sendFeedback(
            text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsUnlockRecipes, text.boolean(isUnlockRecipes))
        )
    }

    fun setDDIEnabled(ctx: Context, enabled: Boolean) {
        options.enableDDI = enabled
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.DdiOptionEnable,
                text.boolean(enabled),
            )
        )
    }

    fun setDDIObjectiveMode(ctx: Context, mode: DDIObjectiveMode) {
        options.ddiObjectiveMode = mode
        val modeText = text.string(
            when (mode) {
                DDIObjectiveMode.INDIVIDUAL -> StringKey.DdiOptionModeIndividual
                DDIObjectiveMode.TEAM_SHARED -> StringKey.DdiOptionModeTeam
            }
        )
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.DdiOptionMode,
                modeText.formatted(Formatting.WHITE),
            )
        )
    }

    fun setDDIMaxHearts(ctx: Context, hearts: Int) {
        options.ddiMaxHearts = hearts.coerceIn(1, 20)
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.DdiOptionHearts,
                text.string(StringKey.DdiOptionHeartsValue, options.ddiMaxHearts),
            )
        )
    }

    fun setDDIWordTimerSeconds(ctx: Context, seconds: Int) {
        options.ddiWordTimerSeconds = seconds.coerceIn(10, 600)
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.DdiOptionTimer,
                text.string(StringKey.DdiOptionTimerValue, options.ddiWordTimerSeconds),
            )
        )
    }

    fun setDDISpecialEventsEnabled(ctx: Context, enabled: Boolean) {
        if (enabled && options.ddiSpecialEventTypes.isEmpty()) {
            ctx.error(text.string(StringKey.DdiCommandSpecialEventsEmpty))
        }
        options.ddiSpecialEventsEnabled = enabled
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.DdiOptionSpecialEvents,
                text.boolean(enabled),
            )
        )
    }

    fun setDDISpecialEventIntervalSeconds(ctx: Context, seconds: Int) {
        if (seconds !in DDI_SPECIAL_EVENT_INTERVAL_RANGE) {
            ctx.error(
                text.string(
                    StringKey.DdiCommandSpecialEventIntervalInvalid,
                    DDI_SPECIAL_EVENT_INTERVAL_RANGE.first,
                    DDI_SPECIAL_EVENT_INTERVAL_RANGE.last,
                )
            )
        }
        options.ddiSpecialEventIntervalSeconds = seconds
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.DdiOptionSpecialEventInterval,
                text.string(StringKey.DdiOptionSpecialEventIntervalValue, seconds),
            )
        )
    }

    fun setDDISpecialEventPreset(ctx: Context, preset: DDISpecialEventPreset) {
        options.ddiSpecialEventTypes = preset.eventTypes.toSet()
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.DdiOptionSpecialEventTypes,
                text.translatable(
                    "bingo.ddi.special_event.preset.${preset.name.lowercase()}",
                    null,
                ).formatted(Formatting.WHITE),
            )
        )
    }

    fun setDDISpecialEventEnabled(
        ctx: Context,
        eventType: DDISpecialEventType,
        enabled: Boolean,
    ) {
        val newTypes = when {
            enabled -> options.ddiSpecialEventTypes + eventType
            else -> options.ddiSpecialEventTypes - eventType
        }.toSet()
        if (options.ddiSpecialEventsEnabled && newTypes.isEmpty()) {
            ctx.error(text.string(StringKey.DdiCommandSpecialEventsEmpty))
        }
        options.ddiSpecialEventTypes = newTypes
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                text.translatable("bingo.ddi.special_event.${eventType.id}", null),
                text.boolean(enabled),
            )
        )
    }

    fun setDDIVoiceKeywordsEnabled(ctx: Context, enabled: Boolean) {
        options.ddiVoiceKeywordsEnabled = enabled
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.DdiOptionVoiceKeywords,
                text.boolean(enabled),
            )
        )
    }

    fun addDDIVoiceKeyword(ctx: Context, rawKeyword: String) {
        val keyword = DDIVoiceKeywordOptions.validate(rawKeyword)
            ?: ctx.error(
                text.string(
                    StringKey.DdiCommandVoiceKeywordInvalid,
                    DDIVoiceKeywordOptions.MAX_KEYWORD_CODE_POINTS,
                )
            )
        val recognitionKey = DDIVoiceKeywordOptions.recognitionKey(keyword)
        if (options.ddiVoiceCustomKeywords.any {
                DDIVoiceKeywordOptions.recognitionKey(it) == recognitionKey
            }
        ) {
            ctx.error(text.string(StringKey.DdiCommandVoiceKeywordDuplicate, keyword))
        }
        val updatedKeywords = options.ddiVoiceCustomKeywords + keyword
        if (!DDIVoiceKeywordOptions.isWithinTotalBudget(updatedKeywords)) {
            ctx.error(
                text.literal(
                    "自定义语音词的总长度不能超过 " +
                        "${DDIVoiceKeywordOptions.MAX_TOTAL_CUSTOM_KEYWORD_CODE_POINTS} 个字符。"
                )
            )
        }
        options.ddiVoiceCustomKeywords = updatedKeywords
        ctx.sendFeedback(text.string(StringKey.DdiCommandVoiceKeywordAdded, keyword))
    }

    fun removeDDIVoiceKeyword(ctx: Context, rawKeyword: String) {
        val recognitionKey = DDIVoiceKeywordOptions.recognitionKey(rawKeyword)
        val removedKeyword = options.ddiVoiceCustomKeywords.firstOrNull {
            DDIVoiceKeywordOptions.recognitionKey(it) == recognitionKey
        } ?: ctx.error(text.string(StringKey.DdiCommandVoiceKeywordNotFound, rawKeyword))
        options.ddiVoiceCustomKeywords = options.ddiVoiceCustomKeywords
            .filterNot { DDIVoiceKeywordOptions.recognitionKey(it) == recognitionKey }
            .toList()
        ctx.sendFeedback(text.string(StringKey.DdiCommandVoiceKeywordRemoved, removedKeyword))
    }

    fun resetDDIVoiceKeywords(ctx: Context) {
        val removedCount = options.ddiVoiceCustomKeywords.size
        options.ddiVoiceCustomKeywords = emptyList()
        ctx.sendFeedback(text.string(StringKey.DdiCommandVoiceKeywordReset, removedCount))
    }

    fun setTimeLimit(
        ctx: Context,
        minutes: Int?
    ) {
        // If the game is playing, the command should set the remaining time (add the current time before applying)
        val newTimeLimit = minutes?.takeIf { it > 0 }?.minutes
        val ingameDuration = state.ingameDuration()
        options.timeLimit = newTimeLimit?.plus(ingameDuration ?: Duration.ZERO)

        if (newTimeLimit != null && ingameDuration != null) {
            // When the game resets, apply the specified timeLimit (without the added time)
            state.restoreOptions.add(RestoreTimeLimit(newTimeLimit))
        }

        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.OptionsTimeLimit,
                when {
                    options.timeLimit != null -> state.formatTimeRemaining(text)
                    else -> text.string(StringKey.OptionsTimeLimitOff)
                }
            )
        )
    }

    fun createRestoreTimeLimit() {
        state.restoreOptions.add(
            RestoreTimeLimit(
                state.restoreOptions
                    .filterIsInstance<RestoreTimeLimit>()
                    .lastOrNull()
                    ?.timeLimit
                    ?: options.timeLimit
            )
        )
    }

    fun setSpawnDistance(ctx: Context, distance: Int) {
        options.spawnDistance = distance
        ctx.sendFeedback(
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.OptionsSpawnDistance,
                text.string(StringKey.OptionsSpawnDistanceChunks, distance)
            )
        )
    }

    fun setSpawnDimension(ctx: Context, dimension: String) {
        options.spawnDimension = dimension
        val label = Text.literal(dimension.substringAfterLast(':').formatTitle())
        ctx.sendFeedback(text.string(StringKey.OptionsNotifyChanged, StringKey.OptionsSpawnDimension, label))
    }

    fun getOptionsSummary(player: IPlayerHandle?): Sequence<IText> = sequence {
        val team = player?.let { teamService.getPlayerTeam(it) }
        val card = cardViewService.getPlayerCard(player)

        text.string(
            StringKey.ScoreboardMode,
            textFactory.joinText(
                options.formatGameMode(card)
                    .map { text.string(it) }
            ).formatted(Formatting.YELLOW)
        )
            .formatted(Formatting.GRAY)
            .apply {
                setClickEvent(TextAction.SuggestCommand("/bingo mode"))
            }
            .let { yield(it) }

        text.string(
            StringKey.ScoreboardFeatures,
            options.formatFeatures()
                .map { text.string(it) }
                .let { textFactory.joinText(it) }
                .formatted(Formatting.YELLOW)
        )
            .formatted(Formatting.GRAY)
            .let { yield(it) }

        text.string(StringKey.OptionsGoal)
            .append(": ")
            .append(state.formatCardGoalsDescriptive(team, text).formatted(Formatting.YELLOW))
            .formatted(Formatting.GRAY)
            .also {
                it.setClickEvent(TextAction.SuggestCommand("/bingo goal"))
            }
            .let { yield(it) }

        if (options.endGameWhen !is EndWhen.FirstWin) {
            text.string(
                StringKey.OptionsEndWhen,
                options.endGameWhen.string(text).formatted(Formatting.YELLOW)
            )
                .formatted(Formatting.GRAY)
                .also {
                    it.setClickEvent(TextAction.SuggestCommand(BingoOptionsCommands.END_WHEN))
                }
                .let { yield(it) }
        }

        options.timeLimit?.let { timeLimit ->
            text.string(StringKey.OptionsTimeLimit)
                .append(": ")
                .append(
                    timeLimit.toMinutes().let { minutes ->
                        String.format(Locale.US, "%dh %02dm", minutes / 60, minutes % 60)
                            .let { textFactory.literal(it).formatted(Formatting.YELLOW) }
                    }
                )
                .formatted(Formatting.GRAY)
                .also {
                    it.setClickEvent(TextAction.SuggestCommand("/bingo timelimit"))
                }
                .let { yield(it) }
        }

        text.string(StringKey.OptionsCardDifficulty)
            .append(": ")
            .append(
                textFactory.joinText(
                    TierLabel.entries
                        .mapIndexed { index, label ->
                            textFactory.literal(card.options.itemDistribution.getOrNull(index).toString())
                                .formatted(label.formatting)
                        }
                )
            )
            .formatted(Formatting.GRAY)
            .also {
                it.setClickEvent(TextAction.SuggestCommand("/bingo difficulty"))
            }
            .let { yield(it) }

        text.string(StringKey.OptionsFilter)
            .append(": ")
            .append(objectiveFilterService.formatFilter(card.options.itemFilter, false).formatted(Formatting.YELLOW))
            .formatted(Formatting.GRAY)
            .also {
                it.setClickEvent(TextAction.SuggestCommand("/bingo filter"))
            }
            .let { yield(it) }
    }
}
