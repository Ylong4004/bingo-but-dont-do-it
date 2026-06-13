package me.jfenn.bingo.integrations.vanish

import me.jfenn.bingo.platform.IPlayerHandle

object DummyVanish : IVanishApi {
    override fun isInstalled(): Boolean = false
    override fun setVanish(player: IPlayerHandle, isVanished: Boolean) {}
}