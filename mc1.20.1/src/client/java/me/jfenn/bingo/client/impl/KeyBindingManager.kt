package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.IKeyBinding
import me.jfenn.bingo.client.platform.IKeyBindingManager
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil

class KeyBindingManager : IKeyBindingManager {
    override fun registerKey(translationKey: String, code: Int, category: String): IKeyBinding {
        return KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                code,
                category,
            )
        ).let { KeyBindingImpl(it) }
    }

    class KeyBindingImpl(
        private val binding: KeyBinding,
    ) : IKeyBinding {
        override fun isPressed(): Boolean = binding.isPressed
        override fun wasPressed(): Boolean = binding.wasPressed()
    }
}