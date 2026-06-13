package me.jfenn.bingo.client.common.sound

import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.ClientPacket
import me.jfenn.bingo.common.game.GameOverPacket
import me.jfenn.bingo.platform.event.IEventBus

internal class ScorePacketReceiver(
    eventBus: IEventBus,
    clientPacketEvents: ClientPacketEvents,
    private val soundService: SoundService,
) {

    private fun onGameOver(clientPacket: ClientPacket<GameOverPacket>) {
        val (packet) = clientPacket
        when {
            packet.isUpdate -> {} // if the packet is an update packet, do nothing
            packet.isWinner -> soundService.play(ClientSounds.Key.GAME_WON)
            else -> soundService.play(ClientSounds.Key.GAME_LOST)
        }
    }

    init {
        eventBus.register(clientPacketEvents.scoredItemV1) { (packet) ->
            val score = packet.score.items
            soundService.play(
                sound = when {
                    packet.isViewerOnTeam -> ClientSounds.Key.ITEM_SCORED
                    else -> ClientSounds.Key.OPPONENT_SCORED
                },
                pitch = 0.5f + (score / 25f),
            )
        }

        eventBus.register(clientPacketEvents.scoredItemLostV1) { _ ->
            soundService.play(
                sound = ClientSounds.Key.ITEM_LOST,
                volume = 0.8f,
                pitch = 1.5f,
            )
        }

        eventBus.register(clientPacketEvents.cardCompletedV1) { (packet) ->
            soundService.play(
                if (packet.isWinner) ClientSounds.Key.GAME_WON else ClientSounds.Key.GAME_LOST
            )
        }

        eventBus.register(clientPacketEvents.gameOverV1, ::onGameOver)
        eventBus.register(clientPacketEvents.gameOverV2, ::onGameOver)
        eventBus.register(clientPacketEvents.gameOverV3, ::onGameOver)
        eventBus.register(clientPacketEvents.gameOverV4, ::onGameOver)
        eventBus.register(clientPacketEvents.gameOverV5, ::onGameOver)
        eventBus.register(clientPacketEvents.gameOverV6, ::onGameOver)
        eventBus.register(clientPacketEvents.gameOverV7, ::onGameOver)
        eventBus.register(clientPacketEvents.gameOverV8, ::onGameOver)

        eventBus.register(clientPacketEvents.timerV1) { (packet) ->
            if (packet.secondsRemaining in 1..20 && packet.secondsRemaining % 2 != 0) {
                val volume = 1f - (packet.secondsRemaining / 20f)

                soundService.play(
                    sound = ClientSounds.Key.TIMER_TICK,
                    volume = volume * 0.8f,
                    pitch = volume * 0.5f + 0.5f,
                )
            }
        }

        eventBus.register(clientPacketEvents.countdownV1) { (packet) ->
            when (packet.secondsRemaining) {
                in 1..3 -> soundService.play(ClientSounds.Key.START_COUNTDOWN)
                0 -> soundService.play(ClientSounds.Key.START_RELEASE)
            }
        }
    }
}