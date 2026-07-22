package me.jfenn.bingo.common.commands

import me.jfenn.bingo.common.LOBBY_WORLD_ID
import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.OptionsChangedEvent
import me.jfenn.bingo.common.options.*
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.commands.CommandBuilder
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.commands.IExecutionSource
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Formatting
import kotlin.reflect.KMutableProperty1

class BingoOptionsCommands(
    commandManager: ICommandManager,
    private val eventBus: IEventBus,
    private val text: TextProvider,
) : BingoComponent() {

    private fun IExecutionSource.hasConfigureGame() = hasState(GameState.PREGAME, GameState.PLAYING) && hasPermission(Permission.CONFIGURE_GAME)

    private fun IExecutionContext.setGoal(goal: BingoGoal) {
        scope.get<OptionsService>().setGoal(
            ctx = optionsContext,
            goal = goal,
        )
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    private fun IExecutionContext.toggleCardMode(
        prop: KMutableProperty1<BingoCardOptions, Boolean>,
        value: Boolean? = null,
    ) {
        scope.get<OptionsService>().toggleCardMode(
            ctx = optionsContext,
            prop = prop,
            value = value,
        )
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    private fun IExecutionContext.setWinCondition(winCondition: BingoWinCondition) {
        scope.get<OptionsService>().setWinCondition(optionsContext, winCondition)
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    private fun IExecutionContext.setStalemateBehavior(stalemateBehavior: StalemateBehavior) {
        scope.get<OptionsService>().setStalemateBehavior(optionsContext, stalemateBehavior)
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    private fun IExecutionContext.setEndWhen(endWhen: EndWhen) {
        scope.get<OptionsService>().setEndWhen(optionsContext, endWhen)
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    private fun IExecutionContext.setTimeLimit(minutes: Int?) {
        scope.get<OptionsService>().setTimeLimit(optionsContext, minutes)
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    private fun ddiModeText(mode: DDIObjectiveMode): IText = text.string(
        when (mode) {
            DDIObjectiveMode.INDIVIDUAL -> StringKey.DdiOptionModeIndividual
            DDIObjectiveMode.TEAM_SHARED -> StringKey.DdiOptionModeTeam
        }
    )

    private fun formatDDIOptions(options: BingoOptions): IText = text.string(
        StringKey.DdiCommandOptionsSummary,
        text.boolean(options.enableDDI),
        ddiModeText(options.ddiObjectiveMode).formatted(Formatting.YELLOW),
        text.literal(options.ddiMaxHearts.toString()).formatted(Formatting.YELLOW),
        text.literal(options.ddiWordTimerSeconds.toString()).formatted(Formatting.YELLOW),
    ).append("\n").append(
        text.string(
            StringKey.DdiCommandSpecialEventsSummary,
            text.boolean(options.ddiSpecialEventsEnabled),
            options.ddiSpecialEventIntervalSeconds,
            options.ddiSpecialEventTypes.size,
        )
    ).append("\n").append(
        text.string(
            StringKey.DdiCommandVoiceKeywordsSummary,
            text.boolean(options.ddiVoiceKeywordsEnabled),
            options.ddiVoiceCustomKeywords.size,
        )
    )

    private fun IExecutionContext.updateDDIOptions(
        update: (OptionsService, OptionsService.Context) -> Unit,
    ) {
        update(scope.get(), optionsContext)
        eventBus.emit(OptionsChangedEvent, Unit)
    }

    private fun IExecutionContext.getDDISpecialEvent(
        arg: me.jfenn.bingo.platform.commands.CommandArgument<String>,
    ): DDISpecialEventType {
        val id = getArgument(arg)
        return DDISpecialEventType.fromId(id)
            ?: error(text.string(StringKey.DdiCommandSpecialEventUnknown, id))
    }

    private fun IExecutionContext.sendDDISpecialEventList(options: BingoOptions) {
        sendMessage(
            text.string(
                StringKey.DdiCommandSpecialEventListHeader,
                options.ddiSpecialEventTypes.size,
                DDISpecialEventType.entries.size,
            )
        )
        if (options.ddiSpecialEventTypes.isEmpty()) {
            sendMessage(text.string(StringKey.DdiCommandOptionsNone))
            return
        }
        options.ddiSpecialEventTypes
            .sortedBy(DDISpecialEventType::id)
            .forEach { eventType ->
                sendMessage(
                    text.literal("- ").append(
                        text.translatable("bingo.ddi.special_event.${eventType.id}", null)
                    )
                )
            }
    }

    private fun IExecutionContext.sendDDIVoiceKeywordList(options: BingoOptions) {
        sendMessage(
            text.string(
                StringKey.DdiCommandVoiceKeywordListHeader,
                options.ddiVoiceCustomKeywords.size,
            )
        )
        if (options.ddiVoiceCustomKeywords.isEmpty()) {
            sendMessage(text.string(StringKey.DdiCommandOptionsNone))
            return
        }
        options.ddiVoiceCustomKeywords.forEach { keyword ->
            val disabled = DDIVoiceKeywordOptions.customWordId(keyword) in options.ddiDisabledWordIds
            sendMessage(
                text.literal("- $keyword")
                    .append(text.literal(if (disabled) "（已停用）" else "（已启用）").formatted(
                        if (disabled) Formatting.DARK_GRAY else Formatting.GREEN,
                    ))
            )
        }
    }

    private fun IExecutionContext.sendDDIVoiceKeywordMatches(
        options: BingoOptions,
        rawQuery: String,
    ) {
        val query = DDIVoiceKeywordOptions.recognitionKey(rawQuery)
        val matches = options.ddiVoiceCustomKeywords.filter { keyword ->
            query.isNotBlank() && DDIVoiceKeywordOptions.recognitionKey(keyword).contains(query)
        }
        sendMessage(text.literal("§6[DDI 自定义语音词] §f“$rawQuery”匹配 ${matches.size} 条。"))
        matches.take(20).forEach { keyword -> sendMessage(text.literal("- $keyword")) }
        if (matches.size > 20) sendMessage(text.literal("§7仅显示前 20 条；请缩小关键词。"))
    }

    private fun CommandBuilder.executesToggle(callback: IExecutionContext.(Boolean?) -> Unit) {
        executes { callback(null) }
        boolean("enabled") { arg ->
            executes { callback(getArgument(arg)) }
        }
    }

    init {
        commandManager.register("bingo") {
            literal("goal") {
                requires { hasConfigureGame() }
                integer("count", min = 1) { countArg ->
                    literal("items") {
                        executes {
                            val count = getArgument(countArg)
                            setGoal(BingoGoal.Items(count))
                        }
                    }
                    literal("lines") {
                        executes {
                            val count = getArgument(countArg)
                            setGoal(BingoGoal.Lines(count))
                        }
                    }
                }

                literal("full_card") {
                    executes {
                        setGoal(BingoGoal.Items(BingoGoal.MAX_ITEMS))
                    }
                    literal("items") {
                        executes {
                            setGoal(BingoGoal.Items(BingoGoal.MAX_ITEMS))
                        }
                    }
                    literal("lines") {
                        executes {
                            setGoal(BingoGoal.Lines(BingoGoal.MAX_LINES))
                        }
                    }
                }
            }

            literal("mode") {
                requires { hasConfigureGame() }
                literal("lockout") {
                    executesToggle { toggleCardMode(BingoCardOptions::isLockoutMode, it) }
                }
                literal("inventory") {
                    executesToggle { toggleCardMode(BingoCardOptions::isInventoryMode, it) }
                }
                literal("hidden_items") {
                    executesToggle { toggleCardMode(BingoCardOptions::isHiddenItemsMode, it) }
                }
                literal("consume_items") {
                    requires {
                        hasState(GameState.PREGAME, GameState.PLAYING)
                                && hasPermission(Permission.CONFIGURE_GAME)
                                && hasLobby()
                    }
                    executesToggle { toggleCardMode(BingoCardOptions::isConsumeItemsMode, it) }
                }
            }

            literal("options") {
                requires { hasConfigureGame() }

                literal("play_to") {
                    literal("cards") {
                        integer("count", min = 1) { countArg ->
                            executes {
                                val count = getArgument(countArg).coerceAtLeast(1)
                                setWinCondition(BingoWinCondition.Cards(count))
                            }
                        }
                    }

                    literal("infinite_cards") {
                        executes {
                            setWinCondition(BingoWinCondition.Infinite)
                        }
                    }

                    literal("replace_goals") {
                        executes {
                            setWinCondition(BingoWinCondition.ReplaceGoals)
                        }
                    }
                }

                literal("stalemate") {
                    literal("end_game") {
                        executes { setStalemateBehavior(StalemateBehavior.END_GAME) }
                    }
                    literal("reroll_card") {
                        executes { setStalemateBehavior(StalemateBehavior.REROLL_CARD) }
                    }
                    literal("do_nothing") {
                        executes { setStalemateBehavior(StalemateBehavior.NOTHING) }
                    }
                }

                literal("end_when") {
                    literal("never") {
                        executes { setEndWhen(EndWhen.Never) }
                    }
                    literal("first_win") {
                        executes { setEndWhen(EndWhen.FirstWin) }
                    }
                    literal("teams_win") {
                        integer("teams", min = 2) { teamsArg ->
                            executes { setEndWhen(EndWhen.TeamsWin(getArgument(teamsArg))) }
                        }
                    }
                    literal("all_win") {
                        executes { setEndWhen(EndWhen.AllWin) }
                    }
                }

                literal("pvp") {
                    requires { hasLobby() && hasConfigureGame() }
                    executesToggle { isPvpEnabled ->
                        scope.get<OptionsService>().togglePvp(optionsContext, isPvpEnabled)
                        scope.get<IEventBus>().emit(OptionsChangedEvent, Unit)
                    }
                }

                literal("elytra") {
                    executesToggle { isElytra ->
                        scope.get<OptionsService>().toggleElytra(optionsContext, isElytra)
                        scope.get<IEventBus>().emit(OptionsChangedEvent, Unit)
                    }
                }

                literal("night_vision") {
                    requires { hasLobby() && hasConfigureGame() }
                    executesToggle { isNightVision ->
                        scope.get<OptionsService>().toggleNightVision(optionsContext, isNightVision)
                        eventBus.emit(OptionsChangedEvent, Unit)
                    }
                }

                literal("preview_card") {
                    requires {
                        hasConfigureGame() && hasState(GameState.PREGAME)
                    }
                    executesToggle { isPreviewCard ->
                        scope.get<OptionsService>().togglePreviewCard(optionsContext, isPreviewCard)
                        eventBus.emit(OptionsChangedEvent, Unit)
                    }
                }

                literal("spawn_distance") {
                    requires { hasLobby() && hasConfigureGame() }
                    integer("chunks", min = 0) { chunksArg ->
                        executes {
                            val chunks = getArgument(chunksArg)
                            scope.get<OptionsService>().setSpawnDistance(optionsContext, chunks)
                            eventBus.emit(OptionsChangedEvent, Unit)
                        }
                    }
                }

                literal("ddi") {
                    // 对局进行中仍可查询；所有修改子命令仅限准备阶段，因为 DDI
                    // 会在开局时保存这些选项的快照。
                    executes {
                        sendMessage(formatDDIOptions(scope.get<BingoState>().options))
                    }
                    literal("enable") {
                        requires { hasState(GameState.PREGAME) }
                        executesToggle { enabled ->
                            updateDDIOptions { service, ctx ->
                                val current = scope.get<BingoState>().options.enableDDI
                                service.setDDIEnabled(ctx, enabled ?: !current)
                            }
                        }
                    }
                    literal("mode") {
                        requires { hasState(GameState.PREGAME) }
                        literal("individual") {
                            executes {
                                updateDDIOptions { service, ctx ->
                                    service.setDDIObjectiveMode(ctx, DDIObjectiveMode.INDIVIDUAL)
                                }
                            }
                        }
                        literal("team") {
                            executes {
                                updateDDIOptions { service, ctx ->
                                    service.setDDIObjectiveMode(ctx, DDIObjectiveMode.TEAM_SHARED)
                                }
                            }
                        }
                    }
                    literal("hearts") {
                        requires { hasState(GameState.PREGAME) }
                        integer("count", min = 1, max = 20) { countArg ->
                            executes {
                                updateDDIOptions { service, ctx ->
                                    service.setDDIMaxHearts(ctx, getArgument(countArg))
                                }
                            }
                        }
                    }
                    literal("timer") {
                        requires { hasState(GameState.PREGAME) }
                        integer("seconds", min = 10, max = 600) { secondsArg ->
                            executes {
                                updateDDIOptions { service, ctx ->
                                    service.setDDIWordTimerSeconds(ctx, getArgument(secondsArg))
                                }
                            }
                        }
                    }

                    literal("events") {
                        executes {
                            val options = scope.get<BingoState>().options
                            sendMessage(
                                text.string(
                                    StringKey.DdiCommandSpecialEventsSummary,
                                    text.boolean(options.ddiSpecialEventsEnabled),
                                    options.ddiSpecialEventIntervalSeconds,
                                    options.ddiSpecialEventTypes.size,
                                )
                            )
                        }
                        literal("enable") {
                            requires { hasState(GameState.PREGAME) }
                            executesToggle { enabled ->
                                updateDDIOptions { service, ctx ->
                                    val current = scope.get<BingoState>().options.ddiSpecialEventsEnabled
                                    service.setDDISpecialEventsEnabled(ctx, enabled ?: !current)
                                }
                            }
                        }
                        literal("interval") {
                            requires { hasState(GameState.PREGAME) }
                            integer(
                                "seconds",
                                min = DDI_SPECIAL_EVENT_INTERVAL_RANGE.first,
                                max = DDI_SPECIAL_EVENT_INTERVAL_RANGE.last,
                            ) { secondsArg ->
                                executes {
                                    updateDDIOptions { service, ctx ->
                                        service.setDDISpecialEventIntervalSeconds(ctx, getArgument(secondsArg))
                                    }
                                }
                            }
                        }
                        literal("preset") {
                            requires { hasState(GameState.PREGAME) }
                            DDISpecialEventPreset.entries.forEach { preset ->
                                literal(preset.name.lowercase()) {
                                    executes {
                                        updateDDIOptions { service, ctx ->
                                            service.setDDISpecialEventPreset(ctx, preset)
                                        }
                                    }
                                }
                            }
                        }
                        literal("include") {
                            requires { hasState(GameState.PREGAME) }
                            string(
                                "event",
                                suggestions = { DDISpecialEventType.entries.map(DDISpecialEventType::id) },
                            ) { eventArg ->
                                executes {
                                    val eventType = getDDISpecialEvent(eventArg)
                                    updateDDIOptions { service, ctx ->
                                        service.setDDISpecialEventEnabled(ctx, eventType, true)
                                    }
                                }
                            }
                        }
                        literal("exclude") {
                            requires { hasState(GameState.PREGAME) }
                            string(
                                "event",
                                suggestions = { DDISpecialEventType.entries.map(DDISpecialEventType::id) },
                            ) { eventArg ->
                                executes {
                                    val eventType = getDDISpecialEvent(eventArg)
                                    updateDDIOptions { service, ctx ->
                                        service.setDDISpecialEventEnabled(ctx, eventType, false)
                                    }
                                }
                            }
                        }
                        literal("list") {
                            executes { sendDDISpecialEventList(scope.get<BingoState>().options) }
                        }
                    }

                    literal("voice") {
                        executes {
                            val options = scope.get<BingoState>().options
                            sendMessage(
                                text.string(
                                    StringKey.DdiCommandVoiceKeywordsSummary,
                                    text.boolean(options.ddiVoiceKeywordsEnabled),
                                    options.ddiVoiceCustomKeywords.size,
                                )
                            )
                        }
                        literal("enable") {
                            requires { hasState(GameState.PREGAME) }
                            executesToggle { enabled ->
                                updateDDIOptions { service, ctx ->
                                    val current = scope.get<BingoState>().options.ddiVoiceKeywordsEnabled
                                    service.setDDIVoiceKeywordsEnabled(ctx, enabled ?: !current)
                                }
                            }
                        }
                        literal("keyword") {
                            literal("add") {
                                string("keyword", greedy = true) { keywordArg ->
                                    executes {
                                        updateDDIOptions { service, ctx ->
                                            service.addDDIVoiceKeyword(ctx, getArgument(keywordArg))
                                        }
                                    }
                                }
                            }
                            literal("remove") {
                                string(
                                    "keyword",
                                    suggestions = {
                                        scope.get<BingoState>().options.ddiVoiceCustomKeywords
                                    },
                                    greedy = true,
                                ) { keywordArg ->
                                    executes {
                                        updateDDIOptions { service, ctx ->
                                            service.removeDDIVoiceKeyword(ctx, getArgument(keywordArg))
                                        }
                                    }
                                }
                            }
                            literal("rename") {
                                string(
                                    "old_keyword",
                                    suggestions = {
                                        scope.get<BingoState>().options.ddiVoiceCustomKeywords
                                    },
                                ) { oldArg ->
                                    string("new_keyword", greedy = true) { newArg ->
                                        executes {
                                            updateDDIOptions { service, ctx ->
                                                service.renameDDIVoiceKeyword(
                                                    ctx,
                                                    getArgument(oldArg),
                                                    getArgument(newArg),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            literal("import") {
                                string("keywords", greedy = true) { keywordsArg ->
                                    executes {
                                        updateDDIOptions { service, ctx ->
                                            service.importDDIVoiceKeywords(ctx, getArgument(keywordsArg))
                                        }
                                    }
                                }
                            }
                            literal("enable") {
                                string(
                                    "keyword",
                                    suggestions = {
                                        scope.get<BingoState>().options.ddiVoiceCustomKeywords
                                    },
                                    greedy = true,
                                ) { keywordArg ->
                                    executes {
                                        updateDDIOptions { service, ctx ->
                                            service.setDDIVoiceKeywordEnabled(
                                                ctx,
                                                getArgument(keywordArg),
                                                enabled = true,
                                            )
                                        }
                                    }
                                }
                            }
                            literal("disable") {
                                string(
                                    "keyword",
                                    suggestions = {
                                        scope.get<BingoState>().options.ddiVoiceCustomKeywords
                                    },
                                    greedy = true,
                                ) { keywordArg ->
                                    executes {
                                        updateDDIOptions { service, ctx ->
                                            service.setDDIVoiceKeywordEnabled(
                                                ctx,
                                                getArgument(keywordArg),
                                                enabled = false,
                                            )
                                        }
                                    }
                                }
                            }
                            literal("find") {
                                string("query", greedy = true) { queryArg ->
                                    executes {
                                        sendDDIVoiceKeywordMatches(
                                            scope.get<BingoState>().options,
                                            getArgument(queryArg),
                                        )
                                    }
                                }
                            }
                            literal("list") {
                                executes { sendDDIVoiceKeywordList(scope.get<BingoState>().options) }
                            }
                            literal("reset") {
                                executes {
                                    updateDDIOptions { service, ctx ->
                                        service.resetDDIVoiceKeywords(ctx)
                                    }
                                }
                            }
                        }
                    }
                }

                literal("spawn_dimension") {
                    requires { hasLobby() && hasConfigureGame() }
                    string(
                        name = "dimension",
                        suggestions = {
                            scope.get<IServerWorldFactory>()
                                .listWorlds()
                                .map { it.identifier }
                                .minus(LOBBY_WORLD_ID.toString())
                        },
                        greedy = true,
                    ) { dimensionArg ->
                        executes {
                            val dimension = getArgument(dimensionArg)

                            scope.get<IServerWorldFactory>()
                                .listWorlds()
                                .find { it.identifier == dimension }
                                ?: error("Dimension '$dimension' does not exist!")

                            scope.get<OptionsService>().setSpawnDimension(optionsContext, dimension)
                            eventBus.emit(OptionsChangedEvent, Unit)
                        }
                    }
                }
            }

            literal("timelimit") {
                requires { hasConfigureGame() }
                integer("minutes", min = 1) { minutesArg ->
                    executes { setTimeLimit(getArgument(minutesArg)) }
                }
                literal("off") {
                    executes { setTimeLimit(null) }
                }
            }
        }
    }

    companion object {
        const val END_WHEN = "/bingo options end_when"
        const val GOAL_FULL_CARD_ITEMS = "/bingo goal full_card items"
    }
}
