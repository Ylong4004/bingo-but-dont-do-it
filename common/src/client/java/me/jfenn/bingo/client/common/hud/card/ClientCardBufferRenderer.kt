package me.jfenn.bingo.client.common.hud.card

import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.common.state.ClientCard
import me.jfenn.bingo.client.common.utils.Interpolate
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.use
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.map.Color
import me.jfenn.bingo.common.utils.milliseconds
import me.jfenn.bingo.common.utils.minus
import me.jfenn.bingo.common.utils.seconds
import net.minecraft.util.Identifier

internal class ClientCardBufferRenderer(
    private val state: BingoHudState,
    private val config: BingoConfig,
    private val cardTileRenderer: CardTileRenderer,
) {

    companion object {
        const val CARD_WIDTH = 122
        const val CARD_HEIGHT = 132

        val BLACK_A40 = Color.BLACK.copy(a = 64)

        val FRAME_LOCKED_ID = Identifier.of("minecraft", "bingo/image_frame_locked")!!
        val IMAGE_HIDDEN_ID = Identifier.of("minecraft", "bingo/image_hidden")!!

        val SHUFFLE_DURATION = 280.milliseconds
        val SHUFFLE_DURATION_PADDING = 50.milliseconds
    }

    private fun shouldRenderReplacing(tile: CardTile): Boolean {
        val updatedAt = tile.updatedAt ?: return false
        val timeSinceUpdated = state.now - updatedAt
        // "== false" asserts that the tile was sent from card_tile_v6 which does not send isFlashing timed with server ticks
        return tile.isFlashing == false &&
                timeSinceUpdated < 4.seconds &&
                (timeSinceUpdated.toMillis() / 500L) % 2L == 0L
    }

    private fun shouldRenderFlashing(tile: CardTile): Boolean {
        val isFlashRender = (state.now.toEpochMilli() / 500L) % 2L == 0L
        return tile.isFlashing?.let { isFlashRender && it } ?: tile.isFlashingOnMap
    }

    fun drawCard(
        service: IDrawService,
        card: ClientCard,
    ) {
        val colors = card.colors

        service.enableBlend()

        service.drawGuiTexture(
            texture = if (card.isGui) colors.cardTextureGui else colors.cardTexture,
            x = 0, y = 0,
            u = 0f, v = 0f,
            width = CARD_WIDTH,
            height = CARD_HEIGHT,
            textureWidth = CARD_WIDTH,
            textureHeight = CARD_HEIGHT,
        )

        card.display.teamName?.let { teamName ->
            // Get or create clientTeamName (truncated to fit on the card)
            val clientTeamName = card.display.clientTeamName
                ?: service.font.truncate(teamName, 70)
                    .also { card.display.clientTeamName = it }
            service.drawText(clientTeamName, colors.textX, colors.textY, colors.textColor.asIntWithAlpha, false)
        }

        val showTeamOutlines = config.client.cardTeamOutlines && (
                (!card.isGui && !config.client.showMultipleCards) || state.cards.size <= 1
        )

        val createInterpolations = state.now < card.shuffledAt + SHUFFLE_DURATION + SHUFFLE_DURATION_PADDING

        for (tileIndex in 0 until 25) {
            val tileX = tileIndex % 5
            val tileY = tileIndex / 5

            val tile = card.tiles.getOrNull(tileIndex) ?: continue

            val isReplacing = shouldRenderReplacing(tile)
            if (isReplacing) continue

            var itemX = (12 + tileX*20).toFloat()
            var itemY = (22 + tileY*20).toFloat()

            if (createInterpolations) {
                val interpolateFrom = card.shufflePositions[tileIndex % card.shufflePositions.size]
                    .let { Pair(it % 5, it / 5) }
                    .let { (x, y) -> Pair((12 + x*20).toFloat(), (22 + y*20).toFloat()) }
                val interpolateX = Interpolate(
                    from = interpolateFrom.first,
                    to = itemX,
                    duration = SHUFFLE_DURATION,
                    startedAt = card.shuffledAt,
                )
                val interpolateY = Interpolate(
                    from = interpolateFrom.second,
                    to = itemY,
                    duration = SHUFFLE_DURATION,
                    startedAt = card.shuffledAt,
                )

                itemX = interpolateX.get(Interpolate.Easing.IN_OUT, state.now)
                itemY = interpolateY.get(Interpolate.Easing.IN_OUT, state.now)
            }

            // BEGIN: TILE DRAW
            service.matrices.use {
                translate(itemX, itemY, 0f)

                val isFlashing = shouldRenderFlashing(tile)
                when {
                    isFlashing -> service.fill(0, 0, 18, 18, colors.tileFlashingColor.asInt)
                    tile.isAchieved -> service.fill(0, 0, 18, 18, colors.tileAchievedColor.asInt)
                    tile.progress >= (1/18f) -> service.fill(
                        0, (18 * (1f - tile.progress)).toInt().coerceIn(0, 18),
                        18, 18,
                        colors.tileProgressColor.asInt
                    )
                }

                if (tile.isHidden) {
                    val texture = tile.itemTier
                        ?.let { Identifier.of("minecraft", "bingo/image_hidden_${it.name.lowercase()}")!! }
                        ?: IMAGE_HIDDEN_ID

                    service.drawGuiTexture(
                        texture = texture,
                        x = 1, y = 1,
                        u = 0f, v = 0f,
                        width = 16, height = 16,
                        textureWidth = 16, textureHeight = 16,
                    )
                }

                cardTileRenderer.renderTile(service, tile, 1, 1)

                if (tile.isAchieved && !isFlashing && !card.isGui) {
                    service.draw()
                    service.overlayFill(
                        0,
                        0,
                        18,
                        18,
                        colors.tileAchievedColor.copy(a = colors.tileAchievedColor.a / 4).asIntWithAlpha
                    )
                }

                cardTileRenderer.renderTileDecorations(service, tile, 1, 1)

                if (tile.isLocked) {
                    service.drawGuiTexture(
                        texture = FRAME_LOCKED_ID,
                        x = -1, y = -1, z = 301,
                        u = 0f, v = 0f,
                        width = 20, height = 20,
                        textureWidth = 20, textureHeight = 20,
                    )
                }

                if (showTeamOutlines) {
                    for ((i, team) in tile.teamKeys.withIndex()) {
                        val color = state.cardColors.getTeamColors(team, card.display.teamColor).outlineColor
                        val colorValue = color.asInt
                        val colorValueDark = color.mix(BLACK_A40).asInt

                        val start = i * 38f / tile.teamKeys.size
                        val end = (i+1) * 38f / tile.teamKeys.size

                        if (start < 19f) {
                            val lineStart = start.toInt()
                            val lineEnd = end.toInt().coerceAtMost(19)
                            service.drawHorizontalLine((-1) + lineStart, (-1) + lineEnd, (-1), colorValue)
                            service.drawVerticalLine((-1), (-1) + lineStart, (-1) + lineEnd, colorValue)
                        }

                        if (end > 19f) {
                            val lineStart = (start.toInt() - 19).coerceAtLeast(0)
                            val lineEnd = end.toInt() - 19
                            service.drawHorizontalLine((-1) + lineStart, (-1) + lineEnd, (-1) + 19, colorValueDark)
                            service.drawVerticalLine((-1) + 19, (-1) + lineStart, (-1) + lineEnd, colorValueDark)
                        }

                        val start2 = i * 34f / tile.teamKeys.size
                        val end2 = (i+1) * 34f / tile.teamKeys.size

                        if (start2 < 17f) {
                            val lineStart = start2.toInt()
                            val lineEnd = end2.toInt().coerceAtMost(17)
                            service.drawHorizontalLine(lineStart, lineEnd, 0, colorValueDark)
                            service.drawVerticalLine(0, lineStart, lineEnd, colorValueDark)
                        }

                        if (end2 > 17f) {
                            val lineStart = (start2.toInt() - 17).coerceAtLeast(0)
                            val lineEnd = end2.toInt() - 17
                            service.drawHorizontalLine(lineStart, lineEnd, 17, colorValue)
                            service.drawVerticalLine(17, lineStart, lineEnd, colorValue)
                        }
                    }
                }
            }

            // END: TILE DRAW
        }
    }

}