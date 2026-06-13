package me.jfenn.bingo.common.timer

import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.state.BingoState
import net.minecraft.server.MinecraftServer

internal class TimerPacketController(
    private val server: MinecraftServer,
    private val state: BingoState,
    private val packetManager: ServerPacketEvents,
    events: ScopedEvents,
) {

    private var prevSecondsRemaining: Long = -1L

    init {
        events.onGameTick {
            // Calculate the seconds remaining
            val timeLimit = state.options.timeLimit ?: return@onGameTick
            val duration = state.ingameDuration() ?: return@onGameTick
            val secondsRemaining = (timeLimit - duration).seconds

            // When it changes, send a new TimerPacket to the client
            if (secondsRemaining != prevSecondsRemaining && secondsRemaining >= 0L) {
                prevSecondsRemaining = secondsRemaining
                val packet = TimerPacket(secondsRemaining.toInt())
                for (player in server.playerManager.playerList) {
                    packetManager.timerV1.send(player, packet)
                }
            }
        }
    }
}