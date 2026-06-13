package me.jfenn.bingo.integrations.permissions

import me.jfenn.bingo.platform.IPlayerHandle

class FallbackPermissionsApi : IPermissionsApi {
    override fun hasPermission(player: IPlayerHandle, key: PermissionKey): Boolean {
        return when (key.default) {
            PermissionDefault.ALLOW -> true
            PermissionDefault.OPERATORS -> (player.server?.isSingleplayer ?: false) || player.hasPermissionLevel(2)
            PermissionDefault.DENY -> false
        }
    }
}