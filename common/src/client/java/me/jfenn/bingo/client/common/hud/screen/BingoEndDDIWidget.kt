package me.jfenn.bingo.client.common.hud.screen

import me.jfenn.bingo.client.common.state.BingoHudState
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.IScrollableWidget
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Formatting
import org.koin.core.Koin

/** 赛后标签页，只列出确实导致队伍扣血的词条。 */
internal class BingoEndDDIWidget(
    koin: Koin,
    private val gameOver: BingoHudState.GameOver,
    private val client: IClient = koin.get(),
    private val text: TextProvider = koin.get(),
) : IScrollableWidget() {

    private val lineHeight = client.font.getTextHeight() + 6

    override fun measureContentHeight(): Int {
        val teamLines = gameOver.packet.ddiDamageHistory.sumOf { team ->
            1 + team.entries.size.coerceAtLeast(1) + 1
        }
        return lineHeight * (1 + teamLines)
    }

    override fun renderContents(drawService: IDrawService, mouseX: Int, mouseY: Int) {
        var line = 0
        drawLine(
            drawService,
            text.string(StringKey.GameEndDdiHistory).formatted(Formatting.GOLD, Formatting.BOLD),
            line++,
            indent = 4,
        )

        for (team in gameOver.packet.ddiDamageHistory) {
            val scoreName = gameOver.packet.scores.find { it.key == team.teamKey }?.name?.copy()
            val teamName = scoreName ?: text.literal(team.teamName)
            drawLine(
                drawService,
                text.literal("◆ ").formatted(Formatting.GRAY).append(teamName.formatted(Formatting.WHITE)),
                line++,
                indent = 4,
            )

            if (team.entries.isEmpty()) {
                drawLine(
                    drawService,
                    text.string(StringKey.GameEndDdiNoDamage).formatted(Formatting.DARK_GRAY),
                    line++,
                    indent = 18,
                )
            } else {
                team.entries.forEachIndexed { index, entry ->
                    val actor = entry.actorName
                        ?.let { text.literal(it) }
                        ?: text.string(StringKey.GameEndDdiSystem)
                    val entryText = text.literal("${index + 1}. ").formatted(Formatting.GRAY)
                        .append(text.literal(entry.wordText).formatted(Formatting.AQUA))
                        .append(" — ")
                        .append(actor.formatted(Formatting.WHITE))
                        .append(" — ")
                        .append(
                            text.string(
                                StringKey.GameEndDdiRemaining,
                                entry.heartsRemaining,
                                entry.maxHearts,
                            ).formatted(
                                if (entry.heartsRemaining == 0) Formatting.DARK_RED else Formatting.RED
                            )
                        )
                    drawLine(drawService, entryText, line++, indent = 18)
                }
            }
            line++
        }
    }

    private fun drawLine(
        drawService: IDrawService,
        value: IText,
        line: Int,
        indent: Int,
    ) {
        val maxWidth = (contentWidth - indent - 4).coerceAtLeast(1)
        val fitted = if (drawService.font.getTextWidth(value) > maxWidth) {
            drawService.font.truncate(value, maxWidth)
        } else {
            value
        }
        drawService.drawText(
            fitted,
            indent,
            line * lineHeight + 2,
            0xFF_FFFFFF.toInt(),
            true,
        )
    }
}
