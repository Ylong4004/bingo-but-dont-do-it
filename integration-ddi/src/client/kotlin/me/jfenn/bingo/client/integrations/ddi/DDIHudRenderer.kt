package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.common.hud.DDIHudStyle
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.use
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextFactory
import net.minecraft.util.Formatting
import kotlin.math.floor
import kotlin.math.roundToInt

/** Renders individual or team-shared status, visible forbidden words and notices. */
class DDIHudRenderer(
    private val state: DDIHudState,
    private val text: ITextFactory,
    private val config: BingoConfig,
) {
    companion object {
        private const val SCREEN_MARGIN = 4
        private const val NOTICE_DURATION_MS = 4_000L
        private const val NOTICE_FADE_START_MS = 1_000L
    }

    fun render(drawService: IDrawService, showStatusPanel: Boolean = true) {
        if (state.isVisible && showStatusPanel) {
            renderStatusPanel(drawService)
        }
        renderNotifications(drawService)
    }

    private fun renderStatusPanel(drawService: IDrawService) {
        val screenWidth = drawService.window.scaledWindowWidth.coerceAtLeast(0)
        val screenHeight = drawService.window.scaledWindowHeight.coerceAtLeast(0)
        val scale = config.client.ddiHudScale.coerceIn(0.5f, 2.5f)
        val availableWidth = floor((screenWidth - SCREEN_MARGIN * 2) / scale).toInt().coerceAtLeast(0)
        val panelWidth = DDIHudStyle.PANEL_WIDTH.coerceAtMost(availableWidth)
        if (panelWidth <= DDIHudStyle.PANEL_PADDING * 2 + DDIHudStyle.ICON_SIZE) return

        val lineHeight = DDIHudStyle.lineHeight(drawService)
        val availableHeight = floor((screenHeight - SCREEN_MARGIN * 2) / scale).toInt().coerceAtLeast(0)
        val fixedHeight = DDIHudStyle.HEADER_HEIGHT + DDIHudStyle.PANEL_PADDING * 2 - DDIHudStyle.LINE_GAP
        val maxLines = ((availableHeight - fixedHeight) / lineHeight).coerceAtLeast(0)
        if (maxLines <= 0) return

        val content = buildStatusContent()
        val allLines = content.lines
        val visibleLines = when {
            allLines.size <= maxLines -> allLines
            maxLines == 1 -> listOf(text.literal("…"))
            else -> allLines.take(maxLines - 1) + text.literal("… 还有 ${allLines.size - maxLines + 1} 项")
        }

        val panelHeight = DDIHudStyle.panelHeight(drawService, visibleLines.size)
        val scaledWidth = (panelWidth * scale).roundToInt()
        val scaledHeight = (panelHeight * scale).roundToInt()
        val alignment = config.client.ddiHudAlignment
        val signedOffsetX = config.client.ddiHudOffsetX * if (alignment.x > 0) -1 else 1
        val signedOffsetY = config.client.ddiHudOffsetY * if (alignment.y > 0) -1 else 1
        val panelX = (alignment.x * (screenWidth - scaledWidth) + signedOffsetX)
            .coerceIn(0, (screenWidth - scaledWidth).coerceAtLeast(0))
        val panelY = (alignment.y * (screenHeight - scaledHeight) + signedOffsetY)
            .coerceIn(0, (screenHeight - scaledHeight).coerceAtLeast(0))

        drawService.matrices.use {
            drawService.matrices.translate(panelX.toFloat(), panelY.toFloat(), 0f)
            drawService.matrices.scale(scale, scale, 1f)
            DDIHudStyle.draw(
                drawService = drawService,
                title = content.title,
                lines = visibleLines,
                width = panelWidth,
                backgroundOpacity = config.client.ddiHudBackgroundOpacity,
            )
        }
    }

    private data class StatusContent(val title: IText, val lines: List<IText>)

    private fun buildStatusContent(): StatusContent = when (state.projectionMode) {
        DDIHudState.ProjectionMode.TEAM -> buildTeamStatusContent()
        DDIHudState.ProjectionMode.PLAYER, null -> buildPlayerStatusContent()
    }

    private fun buildPlayerStatusContent() = StatusContent(
        title = text.literal("不要做挑战").formatted(Formatting.GOLD, Formatting.BOLD),
        lines = buildList {
        if (!state.hasOwnObjective) {
            add(text.literal("旁观者：可查看所有玩家词条").formatted(Formatting.GRAY))
        } else if (state.isMyEliminated) {
            add(text.literal("✖ 你已被淘汰").formatted(Formatting.DARK_RED, Formatting.BOLD))
        } else {
            val status = text.literal("❤×${state.myHearts}  ${state.myHearts}/${state.myMaxHearts}")
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
        },
    )

    private fun buildTeamStatusContent() = StatusContent(
        title = text.literal("不要做挑战 · 队伍共享").formatted(Formatting.GOLD, Formatting.BOLD),
        lines = buildList {
        if (!state.hasOwnTeam) {
            add(text.literal("旁观者：可查看所有队伍词条").formatted(Formatting.GRAY))
        } else if (state.isMyEliminated) {
            add(
                text.literal("✖ ").formatted(Formatting.DARK_RED, Formatting.BOLD)
                    .append(text.literal(state.myTeamName).formatted(state.myTeamColor, Formatting.BOLD))
                    .append(text.literal(" 已淘汰").formatted(Formatting.DARK_RED, Formatting.BOLD))
            )
        } else {
            val status = text.literal("我方 ").formatted(Formatting.WHITE)
                .append(text.literal(state.myTeamName).formatted(state.myTeamColor))
                .append(text.literal("：❤×${state.myHearts}  ${state.myHearts}/${state.myMaxHearts}").formatted(Formatting.RED))
            if (state.myMaxTimerSeconds > 0) {
                status.append(text.literal("  ⏱ ${state.myTimerSeconds}s").formatted(Formatting.GRAY))
            }
            add(status)
        }

        state.otherTeams.values
            .sortedWith(compareBy<DDIHudState.TeamDDIInfo> { it.isEliminated }.thenBy { it.teamName })
            .forEach { team ->
                val teamLine = text.literal("◆ ").formatted(team.teamColor)
                    .append(text.literal("${team.teamName}：").formatted(team.teamColor))
                if (team.isEliminated) {
                    teamLine.append(text.literal("已淘汰 ✖").formatted(Formatting.DARK_GRAY))
                } else {
                    teamLine.append(text.literal("❤×${team.hearts}").formatted(Formatting.RED))
                }
                add(teamLine)
                add(
                    text.literal("  ↳ ").formatted(Formatting.DARK_GRAY)
                        .append(
                            text.literal(team.wordText.ifEmpty { "?" }).formatted(
                                if (team.isEliminated) Formatting.DARK_GRAY else Formatting.AQUA
                            )
                        )
                )
            }
        },
    )

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
                ?.let { "$it（$teamName）" }
                ?: teamName
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
