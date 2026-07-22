package me.jfenn.bingo.common.game

import me.jfenn.bingo.common.Sounds
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.BingoWinCondition
import me.jfenn.bingo.common.options.EndWhen
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.MessageService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatString
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.dialog.IDialogManager
import me.jfenn.bingo.platform.text.IText
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Formatting
import org.slf4j.Logger
import java.time.Duration

internal class GameOverController(
    private val server: MinecraftServer,
    private val state: BingoState,
    private val options: BingoOptions,
    private val text: TextProvider,
    private val gameOverService: GameOverService,
    private val cardViewService: CardViewService,
    private val playerManager: IPlayerManager,
    private val packetManager: ServerPacketEvents,
    private val messageService: MessageService,
    private val teamService: TeamService,
    private val dialogManager: IDialogManager,
    private val log: Logger,
    events: ScopedEvents,
) {

    private fun broadcastGameOver(info: GameOverService.GameOverInfo) {
        val scores = buildList {
            if (
                (options.endGameWhen !is EndWhen.FirstWin || options.winCondition is BingoWinCondition.Infinite)
                && info.scoreRankings.size > 1
            ) {
                add(text.empty())
                // List all teams that have completed the card
                for (score in info.scoreRankings) {
                    val duration = score.duration?.formatString() ?: "DNF"
                    val teamName = state.teams[score.key]?.getName(text, playerName = true)
                        ?: text.literal("[unknown]")

                    text.empty()
                        .append(text.literal("${score.index + 1}. ").formatted(Formatting.GRAY))
                        .append(teamName)
                        .append(" - ")
                        .append(score.score.formatText(text))
                        .append(" - $duration")
                        .also { add(it) }
                }
            }
        }

        val placeholders = mapOf<String, List<IText>>(
            "%game_end_reason%" to listOf(
                (gameOverService.getMessage(info) ?: gameOverService.getTitle(info))
                    .let { text.literal("  ").append(it) }
            ),
            "%time%" to text.string(
                StringKey.GameEndDuration, text.literal(
                    (state.ingameDuration() ?: Duration.ZERO).formatString()
                ).formatted(Formatting.GREEN))
                .let { listOf(text.literal("  ").append(it)) },
            "%seed%" to text.string(
                StringKey.GameEndSeed,
                text.bracketedCopyable(server.overworld.seed.toString())
            ).let { listOf(text.literal("  ").append(it)) },
            "%scores%" to scores,
        )

        val players = playerManager.getPlayers()
            .filter { state.isLobbyMode || teamService.isPlaying(it) }

        messageService.getLines(MessageService.MessageType.GAME_END, placeholders)
            .forEach { line ->
                log.info(line.toString())
                players.forEach { it.sendMessage(line) }
            }
    }

    private fun openGameOverDialog(
        player: IPlayerHandle,
        packet: GameOverPacket,
    ): Boolean {
        val dialog = gameOverService.createDialog(packet) ?: return false
        dialogManager.showDialog(player, dialog)
        return true
    }

    private fun sendGameOverPacket(
        info: GameOverService.GameOverInfo,
        isUpdate: Boolean,
    ) {
        if (!isUpdate) {
            // On the first call, ensure that all players are sent updated card data
            for (team in state.getRegisteredTeams()) {
                cardViewService.updateCard(team, forceNotFlashing = true)
            }
        }

        val title = gameOverService.getTitle(info)
        val message = gameOverService.getMessage(info)
        val scoreRankings = gameOverService.getScoreRankings(info)

        for (player in playerManager.getPlayers()) {
            val packet = gameOverService.createPacket(
                player = player,
                info = info,
                isUpdate = isUpdate,
                title = title,
                message = message,
                scoreRankings = scoreRankings,
            )

            when {
                packetManager.gameOverV10.send(player.player, packet) -> {}
                packetManager.gameOverV9.send(player.player, packet) -> {}
                packetManager.gameOverV8.send(player.player, packet) -> {}
                packetManager.gameOverV7.send(player.player, packet) -> {}
                packetManager.gameOverV6.send(player.player, packet) -> {}
                packetManager.gameOverV5.send(player.player, packet) -> {}
                packetManager.gameOverV4.send(player.player, packet) -> {}
                packetManager.gameOverV3.send(player.player, packet) -> {}
                isUpdate -> {
                    // if this is an update packet, this should only be sent to v3 clients
                    // (for v2/below, this would re-open the end screen on each update)
                }
                packetManager.gameOverV2.send(player.player, packet) -> {}
                packetManager.gameOverV1.send(player.player, packet) -> {
                    Sounds.playGameOver(player)
                }
                openGameOverDialog(player, packet) -> {
                    Sounds.playGameOver(player)
                }
                else -> {
                    // if the player does not have the mod, send the vanilla title message
                    player.sendTitle(title, message)
                    Sounds.playGameOver(player)
                }
            }
        }
    }

    init {
        events.onEnter(GameState.POSTGAME) {
            val info = state.gameOverInfo ?: return@onEnter
            broadcastGameOver(info)
            sendGameOverPacket(info, isUpdate = false)
        }

        events.onUpdateTick {
            // If the game is currently over, keep sending the result to players
            // (this prevents state loss if relogging / switching dimensions)
            state.gameOverInfo?.let {
                sendGameOverPacket(it, isUpdate = true)
            }
        }

        events.onPlayerChannelRegister {
            state.gameOverInfo?.let {
                sendGameOverPacket(it, isUpdate = true)
            }
        }
    }
}
