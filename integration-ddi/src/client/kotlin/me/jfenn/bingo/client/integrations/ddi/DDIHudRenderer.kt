package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.platform.text.IText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * DDI 客户端 HUD 渲染器 — 在游戏界面上显示玩家的词条、心数和计时器。
 *
 * 布局（屏幕右侧上方）：
 * ┌─────────────────────┐
 * │ 🚫 不要潜行          │  ← 当前词条
 * │ ❤❤❤  (3/3)         │  ← 剩余心数
 * │ ⏱ 42s 后更换        │  ← 倒计时
 * └─────────────────────┘
 */
class DDIHudRenderer(
    private val state: DDIHudState,
) {
    companion object {
        const val PANEL_WIDTH = 130
        const val PANEL_PADDING = 6
        const val LINE_HEIGHT = 14
        const val PANEL_X_OFFSET = 8
        const val PANEL_Y_OFFSET = 80
    }

    fun render(drawService: IDrawService) {
        if (!state.isVisible || state.myWordText.isEmpty()) return

        val window = drawService.window
        val panelX = window.scaledWindowWidth - PANEL_WIDTH - PANEL_X_OFFSET
        val panelY = PANEL_Y_OFFSET

        val lines = mutableListOf<IText>()

        // 词条行
        lines.add(Text.literal("").append(Text.literal("❌ ").formatted(Formatting.RED))
            .append(Text.literal(state.myWordText).formatted(Formatting.WHITE)))

        // 心数行
        val heartsStr = "❤".repeat(state.myHearts) + "🤍".repeat(state.myMaxHearts - state.myHearts)
        lines.add(Text.literal("$heartsStr (${state.myHearts}/${state.myMaxHearts})")
            .formatted(Formatting.RED))

        // 计时器行
        if (state.myTimerSeconds > 0 && !state.isMyEliminated) {
            lines.add(Text.literal("⏱ ${state.myTimerSeconds}s").formatted(Formatting.GRAY))
        }

        // 淘汰覆盖
        if (state.isMyEliminated) {
            lines.clear()
            lines.add(Text.literal("💀 已淘汰").formatted(Formatting.DARK_RED, Formatting.BOLD))
        }

        // 绘制背景
        val totalHeight = lines.size * LINE_HEIGHT + PANEL_PADDING * 2
        drawService.setShaderColor(0f, 0f, 0f, 0.5f)
        drawService.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + totalHeight, 0x000000)
        drawService.setShaderColor(1f, 1f, 1f, 1f)

        // 绘制文字
        var y = panelY + PANEL_PADDING
        for (line in lines) {
            val textWidth = drawService.font.getTextWidth(line)
            val x = panelX + (PANEL_WIDTH - textWidth) / 2
            drawService.drawText(line, x, y, 0xFFFFFF, true)
            y += LINE_HEIGHT
        }
    }
}
