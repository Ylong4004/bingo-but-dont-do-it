package me.jfenn.bingo.client.common.hud.card

import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.common.state.ClientCard
import me.jfenn.bingo.client.common.state.ClientCardBase
import me.jfenn.bingo.client.platform.renderer.IDrawServiceFactory
import me.jfenn.bingo.client.platform.renderer.use
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.map.CardView
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.utils.minus
import org.slf4j.Logger
import kotlin.math.roundToInt

internal class ClientCardManager(
    private val log: Logger,
    private val drawServiceFactory: IDrawServiceFactory,
    private val clientCardBufferRenderer: ClientCardBufferRenderer,
    private val state: BingoHudState,
    private val config: BingoConfig,
) {

    private fun getCardSize(cardScale: Float): Pair<Int, Int> {
        val width = (ClientCardBufferRenderer.CARD_WIDTH * cardScale * drawServiceFactory.window.scaleFactor).roundToInt()
        val height = (ClientCardBufferRenderer.CARD_HEIGHT * cardScale * drawServiceFactory.window.scaleFactor).roundToInt()
        return Pair(width, height)
    }

    fun newCard(view: CardView, scale: Float = config.client.cardScale): ClientCardBase {
        val (width, height) = getCardSize(scale)
        val framebuffer = drawServiceFactory.newBuffer(width, height)
            .also { it.register() }

        val (guiWidth, guiHeight) = getCardSize(1f)
        val guiFramebuffer = drawServiceFactory.newBuffer(guiWidth, guiHeight)
            .also { it.register() }

        return ClientCardBase(
            framebuffer = framebuffer,
            guiFramebuffer = guiFramebuffer,
            view = view,
        )
    }

    fun resizeCard(card: ClientCard, scale: Float = config.client.cardScale) {
        val (width, height) = getCardSize(scale)
        if (width != card.framebuffer.width || height != card.framebuffer.height) {
            card.framebuffer.resize(width, height)
            card.isDirty = true
        }
    }

    fun markDirty(key: BingoTeamKey?) {
        state.cards[key]?.isDirty = true
    }

    private fun updateCard(card: ClientCard) {
        if (!drawServiceFactory.isBufferSupported)
            return

        card.framebuffer.write { drawService ->
            val (originalWidth, _) = getCardSize(1f)
            val cardScale = card.framebuffer.width.toFloat() / originalWidth

            drawService.matrices.use {
                scale(cardScale, cardScale, 1f)
                clientCardBufferRenderer.drawCard(
                    service = drawService,
                    card = card,
                )
            }
        }
    }

    fun updateCards() {
        for (card in state.cards.values) {
            val isDirty = card.isDirty ||
                    // Always rerender cards while shuffling
                    (state.now - card.shuffledAt) < (ClientCardBufferRenderer.SHUFFLE_DURATION + ClientCardBufferRenderer.SHUFFLE_DURATION_PADDING)

            if (!isDirty) continue

            updateCard(card)
            updateCard(card.guiCard)

            card.isDirty = false
            log.debug("[ClientCardManager] Card {} redrawn!", card.teamKey)
        }
    }

}