package me.jfenn.bingo.client.platform

interface IKeyBindingManager {
    fun registerKey(
        translationKey: String,
        code: Int,
        category: String,
    ): IKeyBinding
}

interface IKeyBinding {
    fun isPressed(): Boolean
    fun wasPressed(): Boolean
}
