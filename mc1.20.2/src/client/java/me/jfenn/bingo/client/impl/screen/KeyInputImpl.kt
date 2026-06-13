package me.jfenn.bingo.client.impl.screen

import me.jfenn.bingo.client.platform.screen.IKeyInput

class KeyInputImpl(
    val keyCode: Int,
): IKeyInput {
    override val isEscape: Boolean
        get() = keyCode == 256
}
