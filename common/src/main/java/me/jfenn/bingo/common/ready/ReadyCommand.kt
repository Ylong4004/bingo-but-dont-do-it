package me.jfenn.bingo.common.ready

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.event.model.OptionsChangedEvent
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.event.IEventBus

class ReadyCommand(
    commandManager: ICommandManager,
    private val text: TextProvider,
    private val eventBus: IEventBus,
) {
    init {
        commandManager.register("ready") {
            requires {
                scope.get<ReadyService>().isReadyEnabled(playerOrThrow)
            }
            executes {
                when (
                    val result = scope.get<ReadyService>().toggleReady(playerOrThrow)
                ) {
                    is ReadyResult.ChangedTo -> sendFeedback(result.message)
                    else -> error(result.message)
                }
            }

            literal("cancel") {
                requires { hasPermission(Permission.CONFIGURE_GAME) }
                executes {
                    val timerState = scope.get<ReadyTimerState>()

                    if (timerState.isRunning) {
                        timerState.reset()
                        timerState.isCancelled = true
                        // call onChangeOptions to refresh the player list
                        eventBus.emit(OptionsChangedEvent, Unit)
                    }

                    sendFeedback(text.string(StringKey.CommandReadyCancelled))
                }
            }
        }
    }
}