package me.jfenn.bingo.integrations.vanish

import me.jfenn.bingo.platform.IPlayerHandle

interface IVanishApi {
    fun isInstalled(): Boolean
    fun setVanish(player: IPlayerHandle, isVanished: Boolean)
}