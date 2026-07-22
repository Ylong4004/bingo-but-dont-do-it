package me.jfenn.bingo.client.common.hud.screen

import me.jfenn.bingo.platform.text.IText
import net.minecraft.client.gui.screen.Screen

/**
 * 可选集成向按 Y 打开的 Bingo 页面增加一个活动中操作入口。
 *
 * 通用页面只认识这个很小的接口，因此不会反向依赖任何具体玩法集成。
 */
interface BingoHudScreenAction {
    val isAvailable: Boolean
    val label: IText
    fun open(parent: Screen): Screen
}
