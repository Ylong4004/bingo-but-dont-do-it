package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextFactory
import net.minecraft.util.Formatting
import kotlin.math.roundToInt

/** Renders individual or team-shared status, visible forbidden words and notices. */
class DDIHudRenderer(
    private val state: DDIHudState,
    private val text: ITextFactory,
) {
    companion object {
        private const val PANEL_WIDTH = 220
        private const val PANEL_PADDING = 6
        private const val PANEL_X_OFFSET = 8
        private const val PANEL_Y_OFFSET = 80
        private const val SCREEN_MARGIN = 4
        private const val BACKGROUND_COLOR = 0x90000000.toInt()
        private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val SEPARATOR_COLOR = 0x40FFFFFF
        private const val NOTICE_DURATION_MS = 4_000L
        private const val NOTICE_FADE_START_MS = 1_000L
    }

    fun render(drawService: IDrawService) {
        if (state.isVisible) {
            renderStatusPanel(drawService)
        }
        renderNotifications(drawService)
    }

    private fun renderStatusPanel(drawService: IDrawService) {
        val screenWidth = drawService.window.scaledWindowWidth.coerceAtLeast(0)
        val screenHeight = drawService.window.scaledWindowHeight.coerceAtLeast(0)
        val panelWidth = PANEL_WIDTH.coerceAtMost((screenWidth - SCREEN_MARGIN * 2).coerceAtLeast(0))
        if (panelWidth <= PANEL_PADDING * 2) return

        val lineHeight = drawService.font.getTextHeight() + 3
        val availableHeight = (screenHeight - SCREEN_MARGIN * 2 - PANEL_PADDING * 2).coerceAtLeast(0)
        val maxLines = availableHeight / lineHeight
        if (maxLines <= 0) return

        val allLines = buildStatusLines()
        val visibleLines = when {
            allLines.size <= maxLines -> allLines
            maxLines == 1 -> listOf(text.literal("…"))
            else -> allLines.take(maxLines - 1) + text.literal("… 还有 ${allLines.size - maxLines + 1} 项")
        }

        val panelHeight = visibleLines.size * lineHeight + PANEL_PADDING * 2
        val panelX = (screenWidth - panelWidth - PANEL_X_OFFSET)
            .coerceIn(0, (screenWidth - panelWidth).coerceAtLeast(0))
        val panelY = PANEL_Y_OFFSET
            .coerceIn(0, (screenHeight - panelHeight).coerceAtLeast(0))

        drawService.fill(
            panelX,
            panelY,
            panelX + panelWidth,
            panelY + panelHeight,
            BACKGROUND_COLOR,
        )

        val maxTextWidth = (panelWidth - PANEL_PADDING * 2).coerceAtLeast(1)
        visibleLines.forEachIndexed { index, line ->
            val fitted = if (drawService.font.getTextWidth(line) > maxTextWidth) {
                drawService.font.truncate(line, maxTextWidth)
            } else {
                line
            }
            drawService.drawText(
                fitted,
                panelX + PANEL_PADDING,
                panelY + PANEL_PADDING + index * lineHeight,
                TEXT_COLOR,
                true,
            )
        }

        if (visibleLines.size > 2) {
            val separatorY = panelY + PANEL_PADDING + lineHeight * 2 - 2
            drawService.drawHorizontalLine(
                panelX + PANEL_PADDING,
                panelX + panelWidth - PANEL_PADDING,
                separatorY,
                SEPARATOR_COLOR,
            )
        }
    }

    private fun buildStatusLines(): List<IText> = when (state.projectionMode) {
        DDIHudState.ProjectionMode.TEAM -> buildTeamStatusLines()
        DDIHudState.ProjectionMode.PLAYER, null -> buildPlayerStatusLines()
    }

    private fun buildPlayerStatusLines(): List<IText> = buildList {
        add(text.literal("不要做挑战").formatted(Formatting.GOLD, Formatting.BOLD))

        if (!state.hasOwnObjective) {
            add(text.literal("旁观者：可查看所有玩家词条").formatted(Formatting.GRAY))
        } else if (state.isMyEliminated) {
            add(text.literal("✖ 你已被淘汰").formatted(Formatting.DARK_RED, Formatting.BOLD))
        } else {
            val hearts = "❤".repeat(state.myHearts.coerceAtMost(10))
            val heartSummary = if (state.myHearts > 10) {
                "❤×${state.myHearts}"
            } else {
                hearts.ifEmpty { "♡" }
            }
            val status = text.literal("$heartSummary ${state.myHearts}/${state.myMaxHearts}")
                .formatted(Formatting.RED)
            if (state.myMaxTimerSeconds > 0) {
                status.append(
                    text.literal("  ⏱ ${state.myTimerSeconds}s").formatted(Formatting.GRAY)
                )
            }
            add(status)
        }

        state.otherPlayers.values
            .sortedWith(compareBy<DDIHudState.PlayerDDIInfo> { it.isEliminated }.thenBy { it.playerName })
            .forEach { player ->
                val line = text.literal("${player.playerName}: ").formatted(Formatting.WHITE)
                    .append(
                        text.literal(player.wordText.ifEmpty { "?" }).formatted(
                            if (player.isEliminated) Formatting.DARK_GRAY else Formatting.AQUA
                        )
                    )
                if (player.isEliminated) {
                    line.append(text.literal("  ✖").formatted(Formatting.DARK_GRAY))
                } else {
                    line.append(text.literal("  ❤×${player.hearts}").formatted(Formatting.RED))
                }
                add(line)
            }
    }

    private fun buildTeamStatusLines(): List<IText> = buildList {
        add(text.literal("不要做挑战 · 队伍共享").formatted(Formatting.GOLD, Formatting.BOLD))

        if (!state.hasOwnTeam) {
            add(text.literal("旁观者：可查看所有队伍词条").formatted(Formatting.GRAY))
        } else if (state.isMyEliminated) {
            add(
                text.literal("✖ ${state.myTeamName} 队已淘汰")
                    .formatted(Formatting.DARK_RED, Formatting.BOLD)
            )
        } else {
            val hearts = "❤".repeat(state.myHearts.coerceAtMost(10))
            val heartSummary = if (state.myHearts > 10) "❤×${state.myHearts}" else hearts.ifEmpty { "♡" }
            val status = text.literal("我方 ${state.myTeamName}：$heartSummary ${state.myHearts}/${state.myMaxHearts}")
                .formatted(Formatting.RED)
            if (state.myMaxTimerSeconds > 0) {
                status.append(text.literal("  ⏱ ${state.myTimerSeconds}s").formatted(Formatting.GRAY))
            }
            add(status)
        }

        state.otherTeams.values
            .sortedWith(compareBy<DDIHudState.TeamDDIInfo> { it.isEliminated }.thenBy { it.teamName })
            .forEach { team ->
                val members = team.memberNames.joinToString("/").let { if (it.isBlank()) "" else " [$it]" }
                val line = text.literal("${team.teamName}$members: ").formatted(Formatting.WHITE)
                    .append(
                        text.literal(team.wordText.ifEmpty { "?" }).formatted(
                            if (team.isEliminated) Formatting.DARK_GRAY else Formatting.AQUA
                        )
                    )
                if (team.isEliminated) {
                    line.append(text.literal("  ✖").formatted(Formatting.DARK_GRAY))
                } else {
                    line.append(text.literal("  ❤×${team.hearts}").formatted(Formatting.RED))
                }
                add(line)
            }
    }

    private fun renderNotifications(drawService: IDrawService) {
        if (state.recentTriggers.isEmpty()) return

        val screenWidth = drawService.window.scaledWindowWidth.coerceAtLeast(0)
        val screenHeight = drawService.window.scaledWindowHeight.coerceAtLeast(0)
        val maxTextWidth = (screenWidth - SCREEN_MARGIN * 2).coerceAtLeast(0)
        if (maxTextWidth == 0 || screenHeight == 0) return

        val lineHeight = drawService.font.getTextHeight() + 4
        val notifications = state.recentTriggers.asReversed()
            .take(((screenHeight / 2 - SCREEN_MARGIN) / lineHeight).coerceAtLeast(0))

        notifications.forEachIndexed { index, notification ->
            val elapsed = notification.timeAliveMs.coerceIn(0L, NOTICE_DURATION_MS)
            val visibility = if (elapsed <= NOTICE_FADE_START_MS) {
                1f
            } else {
                1f - (elapsed - NOTICE_FADE_START_MS).toFloat() /
                    (NOTICE_DURATION_MS - NOTICE_FADE_START_MS).toFloat()
            }.coerceIn(0f, 1f)

            val line = notificationText(notification)
            val fitted = if (drawService.font.getTextWidth(line) > maxTextWidth) {
                drawService.font.truncate(line, maxTextWidth)
            } else {
                line
            }
            val x = (screenWidth - drawService.font.getTextWidth(fitted)) / 2
            val y = (screenHeight / 2 - 24 - index * lineHeight).coerceAtLeast(0)
            val alpha = (visibility * 255f).roundToInt().coerceIn(0, 255)
            drawService.drawText(fitted, x, y, (alpha shl 24) or 0xFFFFFF, true)
        }
    }

    private fun notificationText(notification: DDIHudState.TriggerNotification): IText {
        val teamName = notification.teamName
        if (teamName != null) {
            val actor = notification.actorName
                .takeIf { it.isNotBlank() }
                ?.let { "$it（$teamName 队）" }
                ?: "$teamName 队"
            return when {
                notification.isGain -> text.literal(
                    "$actor 获得一颗共享生命（${notification.wordText}）"
                ).formatted(Formatting.GREEN)

                notification.isElimination -> text.literal(
                    "$actor 因「${notification.wordText}」使全队淘汰"
                ).formatted(Formatting.RED, Formatting.BOLD)

                else -> text.literal(
                    "$actor 触发「${notification.wordText}」，队伍剩 ${notification.remainingHearts} 心"
                ).formatted(Formatting.YELLOW)
            }
        }

        return when {
            notification.isGain -> text.literal(
                "${notification.actorName} 获得一颗心（${notification.wordText}）"
            ).formatted(Formatting.GREEN)

            notification.isElimination -> text.literal(
                "${notification.actorName} 因「${notification.wordText}」被淘汰"
            ).formatted(Formatting.RED, Formatting.BOLD)

            else -> text.literal(
                "${notification.actorName} 触发「${notification.wordText}」，剩 ${notification.remainingHearts} 心"
            ).formatted(Formatting.YELLOW)
        }
    }
}
