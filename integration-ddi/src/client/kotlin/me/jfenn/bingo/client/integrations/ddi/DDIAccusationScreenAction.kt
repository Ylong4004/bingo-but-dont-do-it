package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.common.hud.screen.BingoHudScreenAction
import me.jfenn.bingo.platform.text.IText
import net.minecraft.client.gui.screen.Screen

/** 将 DDI 举报页面作为按 Y 页面中的可选操作入口提供给通用 Bingo UI。 */
class DDIAccusationScreenAction(
    private val hudState: DDIHudState,
    private val accusationState: DDIAccusationClientState,
    private val screenFactory: DDIAccusationScreen.Factory,
    override val label: IText,
) : BingoHudScreenAction {
    override val isAvailable: Boolean
        get() = hudState.isVisible || accusationState.activeVoteCount > 0 || accusationState.candidates.isNotEmpty()

    override fun open(parent: Screen): Screen = screenFactory.create(parent)
}
