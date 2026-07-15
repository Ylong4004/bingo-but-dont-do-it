package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.DDIObjectiveMode
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.PermissionDefault
import me.jfenn.bingo.integrations.permissions.PermissionKey
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Formatting

/** Operator-only diagnostics and client projection recovery for DDI. */
class DDICommands(
    commandManager: ICommandManager,
    private val text: TextProvider,
) : BingoComponent() {

    private fun modeText(mode: DDIObjectiveMode): IText = text.string(
        when (mode) {
            DDIObjectiveMode.INDIVIDUAL -> StringKey.DdiOptionModeIndividual
            DDIObjectiveMode.TEAM_SHARED -> StringKey.DdiOptionModeTeam
        }
    )

    private fun sessionText(state: DDISessionState): IText = text.string(
        when (state) {
            DDISessionState.Inactive -> StringKey.DdiCommandStatusSessionInactive
            is DDISessionState.Active -> StringKey.DdiCommandStatusSessionActive
            is DDISessionState.Completed -> StringKey.DdiCommandStatusSessionCompleted
        }
    )

    private fun sessionGameId(state: DDISessionState) = when (state) {
        DDISessionState.Inactive -> null
        is DDISessionState.Active -> state.gameId
        is DDISessionState.Completed -> state.gameId
    }

    private fun IExecutionContext.showStatus(reveal: Boolean) {
        val options = scope.get<BingoOptions>()
        val controller = scope.get<DDIGameController>()
        val snapshot = scope.get<DDIObjectiveManager>().snapshot()

        sendMessage(
            text.string(
                if (reveal) StringKey.DdiCommandStatusHeaderReveal
                else StringKey.DdiCommandStatusHeader
            ).formatted(Formatting.GOLD, Formatting.BOLD)
        )
        sendMessage(
            text.string(
                StringKey.DdiCommandOptionsSummary,
                text.boolean(options.enableDDI),
                modeText(options.ddiObjectiveMode).formatted(Formatting.YELLOW),
                text.literal(options.ddiMaxHearts.toString()).formatted(Formatting.YELLOW),
                text.literal(options.ddiWordTimerSeconds.toString()).formatted(Formatting.YELLOW),
            ).formatted(Formatting.GRAY)
        )

        val sessionState = controller.sessionState
        val gameId = sessionGameId(sessionState)
            ?.let { text.literal(it.toString()) }
            ?: text.string(StringKey.DdiCommandStatusNone)
        sendMessage(
            text.string(
                StringKey.DdiCommandStatusSession,
                sessionText(sessionState),
                gameId,
            ).formatted(Formatting.GRAY)
        )

        val config = snapshot.config
        if (!snapshot.hasRound || config == null) {
            sendMessage(text.string(StringKey.DdiCommandStatusNoRound).formatted(Formatting.GRAY))
            return
        }

        sendMessage(
            text.string(
                StringKey.DdiCommandStatusRound,
                modeText(config.objectiveMode).formatted(Formatting.YELLOW),
                snapshot.participantCount,
                snapshot.inactiveParticipantCount,
                snapshot.objectives.size,
            ).formatted(Formatting.GRAY)
        )

        if (snapshot.objectives.isEmpty()) {
            sendMessage(text.string(StringKey.DdiCommandStatusNoObjectives).formatted(Formatting.GRAY))
            return
        }

        snapshot.objectives.forEach { objective ->
            val objectiveStatus = text.string(
                if (objective.isEliminated) StringKey.DdiCommandStatusEliminated
                else StringKey.DdiCommandStatusAlive
            )
            sendMessage(
                text.string(
                    StringKey.DdiCommandStatusObjective,
                    objective.objectiveName,
                    objective.teamName,
                    objective.hearts,
                    objective.maxHearts,
                    objective.timerSeconds,
                    objective.maxTimerSeconds,
                    objectiveStatus,
                )
            )

            if (reveal) {
                val wordText = objective.wordText
                val wordId = objective.wordId
                val ruleSummary = objective.ruleSummary
                if (wordText == null || wordId == null || ruleSummary == null) {
                    sendMessage(
                        text.string(StringKey.DdiCommandStatusWordNone)
                            .formatted(Formatting.DARK_GRAY)
                    )
                } else {
                    sendMessage(
                        text.string(
                            StringKey.DdiCommandStatusWord,
                            wordText,
                            wordId,
                            ruleSummary,
                        ).formatted(Formatting.DARK_GRAY)
                    )
                }
            }
        }
    }

    private fun IExecutionContext.syncAllPlayers() {
        val manager = scope.get<DDIObjectiveManager>()
        val targets = server.playerManager.playerList.toList()
        targets.forEach(manager::resyncTo)
        sendFeedback(text.string(StringKey.DdiCommandSyncAllSuccess, targets.size))
    }

    init {
        commandManager.register("bingo") {
            literal("ddi") {
                requires { hasPermission(DDI_ADMIN_PERMISSION) }

                literal("status") {
                    executes { showStatus(reveal = false) }
                    literal("reveal") {
                        executes { showStatus(reveal = true) }
                    }
                }

                literal("sync") {
                    executes { syncAllPlayers() }
                    player("player") { playerArg ->
                        executes {
                            val target = getArgument(playerArg)
                            scope.get<DDIObjectiveManager>().resyncTo(target.player)
                            sendFeedback(
                                text.string(StringKey.DdiCommandSyncPlayerSuccess, target.playerName)
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val DDI_ADMIN_PERMISSION = PermissionKey(
            "$MOD_ID_BINGO.command.ddi",
            PermissionDefault.OPERATORS,
        )
    }
}
