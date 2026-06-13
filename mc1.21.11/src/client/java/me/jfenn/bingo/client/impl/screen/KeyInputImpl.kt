package me.jfenn.bingo.client.impl.screen

import me.jfenn.bingo.client.platform.screen.IKeyInput
import net.minecraft.client.input.KeyInput

class KeyInputImpl(val input: KeyInput) : IKeyInput {
    override val isEscape: Boolean
        get() = input.isEscape
}
