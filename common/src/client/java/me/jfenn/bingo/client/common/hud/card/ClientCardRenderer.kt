package me.jfenn.bingo.client.common.hud.card

import me.jfenn.bingo.client.common.hud.card.ClientCardBufferRenderer.Companion.CARD_HEIGHT
import me.jfenn.bingo.client.common.hud.card.ClientCardBufferRenderer.Companion.CARD_WIDTH
import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.common.state.ClientCard
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.IDrawServiceFactory
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner
import net.minecraft.util.Identifier
import java.time.Instant
import kotlin.math.ceil

internal class ClientCardRenderer(
    private val text: TextProvider,
    private val state: BingoHudState,
    private val cardTileRenderer: CardTileRenderer,
    private val cardBufferRenderer: ClientCardBufferRenderer,
    private val drawServiceFactory: IDrawServiceFactory,
) {

    companion object {
        const val TOOLTIP_MAX_WIDTH = 200

        val CARD_TOOLTIP_ID = Identifier.of("minecraft", "bingo/tooltip")!!

        private val winColors = listOf(
            0xFF_F5C0A3.toInt(),
            0xFF_A3F5A3.toInt(),
            0xFF_A3F5C7.toInt(),
            0xFF_A3ABF5.toInt(),
            0xFF_CCA3F5.toInt(),
            0xFF_F5A3D6.toInt(),
        )

        fun getWinColor(now: Instant): Int {
            val winIndex = now.epochSecond % winColors.size
            return winColors[winIndex.toInt()]
        }
    }

    fun getItem(
        x: Int,
        y: Int,
        cardScale: Float,
        mouseX: Int,
        mouseY: Int,
    ): Pair<Int, Int>? {
        val startX = x + (11 * cardScale)
        val startY = y + (21 * cardScale)

        val tileX = (mouseX - startX)
            .takeIf { it >= 0f }
            ?.div(20 * cardScale)
            ?.toInt()
            ?.takeIf { it in 0..4 }
            ?: return null

        val tileY = (mouseY - startY)
            .takeIf { it >= 0f }
            ?.div(20 * cardScale)
            ?.toInt()
            ?.takeIf { it in 0..4 }
            ?: return null

        return Pair(tileX, tileY)
    }

    fun draw(
        service: IDrawService,
        card: ClientCard,
        x: Float,
        y: Float,
        z: Int = 0,
        cardScale: Float,
        mouseX: Int = -1,
        mouseY: Int = -1,
        isFocused: Boolean = false,
        isMouseOver: Boolean = false,
        isWinner: Boolean = false,
    ) {
        service.matrices.push()
        service.matrices.translate(x, y, z.toFloat())
        service.matrices.scale(cardScale, cardScale, 1f)

        if (!drawServiceFactory.isBufferSupported) {
            cardBufferRenderer.drawCard(service, card)
        } else {
            service.enableBlend()
            card.framebuffer.draw(service, CARD_WIDTH, CARD_HEIGHT)
        }

        if (isWinner) {
            service.matrices.push()
            service.matrices.translate(90f, -4f, 1000f)
            service.matrices.rotate(.25f)

            service.drawText(text.string(StringKey.CardWinner), 0, 0, getWinColor(state.now), true)

            service.matrices.pop()
        }

        if (isFocused) {
            service.drawGuiTexture(
                texture = card.colors.cardTextureOutline,
                x = 0, y = 0,
                u = 0f, v = 0f,
                width = CARD_WIDTH,
                height = CARD_HEIGHT,
                textureWidth = CARD_WIDTH,
                textureHeight = CARD_HEIGHT,
            )
        }

        service.matrices.pop()

        // Render the team's player list if the title is hovered over
        if (
            isMouseOver &&
            mouseX in x.toInt()..(x + CARD_WIDTH*0.7*cardScale).toInt() &&
            mouseY in y.toInt()..(y+20*cardScale).toInt() &&
            card.display.players.isNotEmpty()
        ) {
            service.matrices.push()
            service.matrices.translate(0f, 0f, z + 2000f)
            service.drawTooltip(card.display.players.map { text.literal(it.name) }, mouseX, mouseY)
            service.matrices.pop()
        }

        if (isMouseOver) {
            // Render the hover tooltips last, without the matrix translate/transform
            val (tileX, tileY) = getItem(x.toInt(), y.toInt(), cardScale, mouseX, mouseY) ?: return
            val tile = card.view.tile(tileX, tileY) ?: return

            drawTooltip(service, tile, mouseX, mouseY, z)
        }
    }

    private fun drawTooltip(
        service: IDrawService,
        tile: CardTile,
        mouseX: Int,
        mouseY: Int,
        z: Int,
    ) {
        val tooltip = tile.clientTooltip ?: buildList {
            tile.name?.let { add(it) }
            addAll(
                tile.lore.flatMapIndexed { i, lore ->
                    val isLast = i >= tile.lore.size - 1 // the last item (tile info) should never be wrapped
                    if (isLast) listOf(lore)
                    else service.font.wrapLines(lore, TOOLTIP_MAX_WIDTH)
                }
            )
        }.also {
            tile.clientTooltip = it
        }

        val rows = when (tile.imageList.size) {
            0 -> 0
            in 1..5 -> 1
            in 6..12 -> 2
            in 13..21 -> 3
            in 22..28 -> 4
            else -> 5
        }
        val columns = ceil(tile.imageList.size / rows.toFloat()).toInt()

        if (tooltip.isNotEmpty()) {
            service.matrices.push()
            service.matrices.translate(0f, 0f, z + 2000f)

            service.drawTooltip(tooltip, mouseX, mouseY)

            if (tile.imageList.isNotEmpty()) {
                val tooltipWidth = tooltip.maxOf { service.font.getTextWidth(it) }
                val tooltipHeight = 10 * tooltip.size // hardcoded in OrderedTextTooltipComponent
                val componentHeight = 14 + 20 * rows
                val componentWidth = 14 + 20 * columns

                val tooltipPosition = HoveredTooltipPositioner.INSTANCE.getPosition(
                    service.window.scaledWindowWidth,
                    service.window.scaledWindowHeight,
                    mouseX,
                    mouseY,
                    tooltipWidth,
                    tooltipHeight,
                )

                val x = tooltipPosition.x() - 4
                val y = when {
                    tooltipPosition.y() - componentHeight - 6 > 0 -> tooltipPosition.y() - componentHeight - 6
                    else -> tooltipPosition.y() + tooltipHeight + 6
                }

                service.drawTooltipAddon {
                    service.drawNinePatch(
                        texture = CARD_TOOLTIP_ID,
                        x = x, y = y,
                        sliceSize = 7,
                        width = componentWidth,
                        height = componentHeight,
                        textureWidth = 34,
                        textureHeight = 34,
                    )

                    tile.imageList.forEachIndexed { index, cardTileImage ->
                        val row = index / columns
                        val col = index % columns
                        cardTileRenderer.renderTileImage(
                            drawService = service,
                            image = cardTileImage,
                            itemX = x + 9 + 20 * col,
                            itemY = y + 9 + 20 * row,
                        )
                    }
                }
            }

            service.matrices.pop()
        }
    }

}