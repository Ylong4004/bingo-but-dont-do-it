package me.jfenn.bingo.common.map

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.event.packet.ServerPacketEvents
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.platform.IPlayerManager
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import java.io.InputStream
import java.util.*
import kotlin.jvm.optionals.getOrNull

internal class CardImageService(
    private val server: MinecraftServer,
    private val playerManager: IPlayerManager,
    private val packets: ServerPacketEvents,
    private val state: BingoState,
) {

    private val playersWithImages = mutableMapOf<String, Set<UUID>>()

    fun readCardImageStream(id: String): InputStream? {
        val namespace = id.substringBefore(':')
        val path = "$MOD_ID_BINGO/images/${id.substringAfterLast(':')}.png"
        return server.resourceManager.getResource(Identifier.of(namespace, path))
            .getOrNull()
            ?.inputStream
    }

    private fun readCardImage(id: String): CardImagePacket? {
        val bytes = readCardImageStream(id)?.use { it.readAllBytes() }
            ?: return null

        return CardImagePacket(id, bytes)
    }

    fun clearPlayerStates() {
        playersWithImages.clear()
    }

    fun clearPlayerState(player: UUID) {
        for (entry in playersWithImages.entries) {
            entry.setValue(entry.value - player)
        }
    }

    fun sendNecessaryImages() {
        val images = state.cards
            .flatMap { card -> card.objectives.values.mapNotNull { it.display.image } }
            .toSet()

        val players = playerManager.getPlayers()

        // When custom images are present on the card, ensure that a CardImagePacket has been sent to each player
        for (image in images) {
            val playerSet = playersWithImages[image] ?: emptySet()
            val playersNeeded = players.filter { !playerSet.contains(it.uuid) }

            if (playersNeeded.isEmpty()) continue
            val packet = readCardImage(image) ?: continue

            for (playerNeeded in playersNeeded) {
                packets.cardImageV1.send(playerNeeded.player, packet)
            }

            playersWithImages[image] = playerSet + playersNeeded.map { it.uuid }
        }

        // If an image is removed (e.g. the card is re-rolled), tell clients that they can drop the image
        for (removedImage in playersWithImages.keys - images) {
            val playerSet = playersWithImages[removedImage] ?: continue
            val packet = CardImagePacket(removedImage, null)

            for (uuid in playerSet) {
                val player = playerManager.getPlayer(uuid) ?: continue
                packets.cardImageV1.send(player.player, packet)
            }

            playersWithImages.remove(removedImage)
        }
    }
}