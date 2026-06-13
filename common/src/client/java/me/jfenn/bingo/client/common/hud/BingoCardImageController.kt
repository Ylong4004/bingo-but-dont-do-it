package me.jfenn.bingo.client.common.hud

import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.ClientPacket
import me.jfenn.bingo.client.platform.INativeImageFactory
import me.jfenn.bingo.common.map.CardImagePacket
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal class BingoCardImageController(
    private val log: Logger,
    private val nativeImageFactory: INativeImageFactory,
    private val hudState: BingoHudState,
    packetEvents: ClientPacketEvents,
    eventBus: IEventBus,
) {
    private fun updateCardImage(packet: ClientPacket<CardImagePacket>) {
        val (image) = packet

        if (image.image == null) {
            // If the image is being removed, drop it from memory
            log.info("[BingoCardImageController] Removing card image ${image.id}")
            val nativeImage = hudState.images.remove(image.id)
            nativeImage?.close()
            return
        }

        log.info("[BingoCardImageController] Loading card image ${image.id}")
        val png = ByteArrayInputStream(image.image).use {
            ImageIO.read(it)
        }

        val nativeImage = hudState.images.getOrPut(image.id) {
            nativeImageFactory.create(png.width, png.height)
        }

        for (x in 0 until png.width) for (y in 0 until png.height) {
            nativeImage.setPixel(x, y, png.getRGB(x, y))
        }

        nativeImage.upload()
    }

    init {
        eventBus.register(packetEvents.cardImageV1, ::updateCardImage)
    }
}