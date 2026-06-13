package me.jfenn.bingo.common.chat

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.commands.hasState
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.BlockPositionType
import me.jfenn.bingo.common.utils.InstantType
import me.jfenn.bingo.common.utils.formatStringSmall
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.commands.IExecutionSource
import me.jfenn.bingo.platform.commands.ISignedMessage
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.time.Duration
import java.time.Instant

class CoordsCommand(
    commandManager: ICommandManager,
) : BingoComponent() {

    @Serializable
    data class CoordinatesEntry(
        val player: String,
        val pos: BlockPositionType,
        val dimension: String,
        val message: String?,
        val created: InstantType,
    ) {

        fun formatText(): String {
            return with(pos) { "[$x, $y, $z] " } +
                    ("($dimension) ".takeIf { dimension != "overworld" } ?: "") +
                    (message ?: "")
        }

    }

    private fun IExecutionContext.storeCoordinates(
        player: IPlayerHandle,
        team: BingoTeam,
        message: ISignedMessage?
    ) {
        val text = scope.get<TextProvider>()

        val entry = CoordinatesEntry(
            player = player.playerName,
            pos = player.blockPos,
            dimension = player.world.identifier.substringAfter(':').removePrefix("the_"),
            message = message?.raw,
            created = Instant.now(),
        )

        // add the coordinates message to the stored list
        team.chatCoordinates.add(entry)

        // send coordinates to the team
        val chatMessageService = scope.get<ChatMessageService>()
        if (message != null) {
            val signedMessage = message.withUnsignedContent(text.literal(entry.formatText()))
            chatMessageService.sendTeamMessage(signedMessage, player)
        } else {
            chatMessageService.sendTeamMessage(text.literal(entry.formatText()), player)
        }
    }

    private fun IExecutionContext.listCoordinates(player: IPlayerHandle, team: BingoTeam) {
        val text = scope.get<TextProvider>()

        val coordMessages = team.chatCoordinates
        if (coordMessages.isEmpty()) {
            player.sendMessage(text.literal("No coordinates have been saved yet.").formatted(Formatting.GRAY))
            return
        }

        player.sendMessage(text.literal("Coordinates:").formatted(Formatting.GRAY, Formatting.BOLD))
        for (entry in coordMessages) {
            val duration = Duration.between(entry.created, Instant.now())
            player.sendMessage(
                text.literal("  • ${entry.player} (${duration.formatStringSmall()} ago): ${entry.formatText()}")
                    .formatted(Formatting.GRAY)
            )
        }
    }

    private val IExecutionSource.teamOrThrow get() = scope.get<TeamService>()
        .getPlayerTeam(playerOrThrow)
        ?: error(Text.literal("Player must be on a team!"))

    init {
        // send current player coordinates to other team members
        commandManager.register("coords") {
            requires {
                hasState(GameState.PLAYING, GameState.POSTGAME)
                        && hasPermission(Permission.COMMAND_COORDS)
                        && scope.get<TeamService>().isPlaying(playerOrThrow)
            }

            literal("list") {
                executes {
                    listCoordinates(playerOrThrow, teamOrThrow)
                }
            }

            signedMessage("message") { message ->
                executes {
                    val player = playerOrThrow
                    val team = teamOrThrow
                    getArgument(message).thenAccept {
                        storeCoordinates(player, team, it)
                    }
                }
            }

            executes {
                storeCoordinates(playerOrThrow, teamOrThrow, null)
            }
        }
    }

}