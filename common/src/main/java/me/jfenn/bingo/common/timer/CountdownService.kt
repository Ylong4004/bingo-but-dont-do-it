package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.Sounds
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import net.minecraft.util.Formatting

internal class CountdownService(
    private val state: BingoState,
    private val playerManager: IPlayerManager,
    private val packetManager: ServerPacketEvents,
    private val teamService: TeamService,
    private val textProvider: TextProvider,
) {

    private fun sendCountdownPacket(player: IPlayerHandle, packet: CountdownPacket) {
        if (packet.secondsRemaining > 0) {
            player.sendTitle(
                title = textProvider.literal(packet.secondsRemaining.toString()),
                subtitle = null,
            )
        }

        if (packet.secondsRemaining == 0) {
            val team = teamService.getPlayerTeam(player)
            player.sendTitle(
                title = textProvider.string(StringKey.GameAnnounceBingo).formatted(team?.textColor ?: Formatting.WHITE),
                subtitle = textProvider.string(StringKey.GameAnnounceStarted),
            )
        }

        when {
            packetManager.countdownV1.send(player, packet) -> {}
            packet.secondsRemaining in 1..3 -> Sounds.playGameCountdown(player)
            packet.secondsRemaining == 0 -> Sounds.playGameStarted(player)
        }
    }

    fun sendCountdownPacket(packet: CountdownPacket) {
        val players = playerManager.getPlayers()
            .filter { state.isLobbyMode || teamService.isPlaying(it) }

        for (player in players) {
            sendCountdownPacket(player, packet)
        }
    }

}