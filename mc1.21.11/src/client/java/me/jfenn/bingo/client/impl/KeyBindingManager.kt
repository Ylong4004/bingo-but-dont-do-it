package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.IKeyBinding
import me.jfenn.bingo.client.platform.IKeyBindingManager
import me.jfenn.bingo.common.MOD_ID_BINGO
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier

class KeyBindingManager : IKeyBindingManager {
    private val categoryInstance by lazy {
        KeyBinding.Category.create(Identifier.of(MOD_ID_BINGO, "bingo"))
    }

    override fun registerKey(translationKey: String, code: Int, category: String): IKeyBinding {
        return KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                code,
                categoryInstance,
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