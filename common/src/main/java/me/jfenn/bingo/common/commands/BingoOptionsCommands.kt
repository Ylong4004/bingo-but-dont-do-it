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
    )

    private fun IExecutionContext.updateDDIOptions(update: (BingoOptions) -> Unit) {
        val options = scope.get<BingoState>().options
        update(options)
        eventBus.emit(OptionsChangedEvent, Unit)
        sendFeedback(
            text.string(
                StringKey.DdiCommandOptionsUpdated,
                formatDDIOptions(options),
            )
        )
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
                    // Querying remains available during PLAYING, while every
                    // mutating child stays PREGAME-only because the DDI round
                    // snapshots these values when it starts.
                    executes {
                        sendMessage(formatDDIOptions(scope.get<BingoState>().options))
                    }
                    literal("enable") {
                        requires { hasState(GameState.PREGAME) }
                        executesToggle { enabled ->
                            updateDDIOptions { options ->
                                options.enableDDI = enabled ?: !options.enableDDI
                            }
                        }
                    }
                    literal("mode") {
                        requires { hasState(GameState.PREGAME) }
                        literal("individual") {
                            executes {
                                updateDDIOptions { options ->
                                    options.ddiObjectiveMode = DDIObjectiveMode.INDIVIDUAL
                                }
                            }
                        }
                        literal("team") {
                            executes {
                                updateDDIOptions { options ->
                                    options.ddiObjectiveMode = DDIObjectiveMode.TEAM_SHARED
                                }
                            }
                        }
                    }
                    literal("hearts") {
                        requires { hasState(GameState.PREGAME) }
                        integer("count", min = 1, max = 20) { countArg ->
                            executes {
                                updateDDIOptions { options ->
                                    options.ddiMaxHearts = getArgument(countArg)
                                }
                            }
                        }
                    }
                    literal("timer") {
                        requires { hasState(GameState.PREGAME) }
                        integer("seconds", min = 10, max = 600) { secondsArg ->
                            executes {
                                updateDDIOptions { options ->
                                    options.ddiWordTimerSeconds = getArgument(secondsArg)
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
