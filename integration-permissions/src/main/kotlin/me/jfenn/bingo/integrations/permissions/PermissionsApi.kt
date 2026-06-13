package me.jfenn.bingo.integrations.permissions

import me.jfenn.bingo.platform.IPlayerHandle
import me.lucko.fabric.api.permissions.v0.Permissions

class PermissionsApi : IPermissionsApi {
    private val fallback = FallbackPermissionsApi()

    override fun hasPermission(player: IPlayerHandle, key: PermissionKey): Boolean {
        return Permissions.check(player.commandSource, key.permission, fallback.hasPermission(player, key))
    }
}