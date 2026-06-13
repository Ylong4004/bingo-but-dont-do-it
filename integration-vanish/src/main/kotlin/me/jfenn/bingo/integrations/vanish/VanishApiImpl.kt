package me.jfenn.bingo.integrations.vanish

import me.drex.vanish.api.VanishAPI
import me.jfenn.bingo.platform.IPlayerHandle

class VanishApiImpl : IVanishApi {
    override fun isInstalled(): Boolean {
        return true
    }

    override fun setVanish(player: IPlayerHandle, isVanished: Boolean) {
        VanishAPI.setVanish(player.player, isVanished)
    }
}