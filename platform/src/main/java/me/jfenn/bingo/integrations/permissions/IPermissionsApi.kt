package me.jfenn.bingo.integrations.permissions

import me.jfenn.bingo.platform.IPlayerHandle

interface IPermissionsApi {
    fun hasPermission(player: IPlayerHandle, key: PermissionKey): Boolean
}