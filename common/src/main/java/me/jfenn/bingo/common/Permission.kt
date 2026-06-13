package me.jfenn.bingo.common

import me.jfenn.bingo.integrations.permissions.PermissionDefault
import me.jfenn.bingo.integrations.permissions.PermissionKey
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

internal object Permission {

    val CONFIGURE_LOBBY = PermissionKey("$MOD_ID.configure.lobby", PermissionDefault.OPERATORS)

    /** "/bingo ..." commands (& lobby game menu) */
    val CONFIGURE_GAME = PermissionKey("$MOD_ID.configure.game", PermissionDefault.OPERATORS)

    /** "/bingo set" command (& lobby player menu) */
    val CONFIGURE_PLAYER = PermissionKey("$MOD_ID.configure.player", PermissionDefault.ALLOW)

    /** "/join {team}" command */
    val COMMAND_JOIN = PermissionKey("$MOD_ID.command.join", PermissionDefault.ALLOW)

    /** "/join {team} {player}" command */
    val COMMAND_JOIN_PLAYER = PermissionKey("$MOD_ID.command.join_player", PermissionDefault.OPERATORS)

    /** "/ready" command */
    val COMMAND_READY = PermissionKey("$MOD_ID.command.ready", PermissionDefault.ALLOW)

    /** "/coords" command */
    val COMMAND_COORDS = PermissionKey("$MOD_ID.command.coords", PermissionDefault.ALLOW)

    /** "/spectator" command */
    val COMMAND_SPECTATOR = PermissionKey("$MOD_ID.command.spectator", PermissionDefault.ALLOW)

    /** "/bingo reset" command */
    val COMMAND_RESET = PermissionKey("$MOD_ID.command.reset", PermissionDefault.OPERATORS)

    /** "/bingodata" commands */
    val COMMAND_DATA = PermissionKey("$MOD_ID.command.bingodata", PermissionDefault.OPERATORS)

    /** "/bingo debug" command */
    val COMMAND_DEBUG = PermissionKey("$MOD_ID.command.debug", PermissionDefault.OPERATORS)

    /** Make sounds in the lobby, even if lobbyChaosPrevention=true */
    val BYPASS_CHAOS_PREVENTION = PermissionKey("$MOD_ID.lobby.bypass_chaos_prevention", PermissionDefault.OPERATORS)

    /** Interact with blocks/doors in vanished spectator mode */
    val SPECTATOR_USE_DOORS = PermissionKey("$MOD_ID.spectator.use_doors", PermissionDefault.DENY)

    /** View inventories in spectator */
    val SPECTATOR_VIEW_INVENTORY = PermissionKey("$MOD_ID.spectator.view_inventory", PermissionDefault.ALLOW)

    /** View all team cards in spectator */
    val SPECTATOR_VIEW_CARDS = PermissionKey("$MOD_ID.spectator.view_cards", PermissionDefault.ALLOW)

    /** View a player's own statistics */
    val STATS_VIEW_SELF = PermissionKey("$MOD_ID.stats.view_self", PermissionDefault.ALLOW)

    /** View another player's statistics */
    val STATS_VIEW_PLAYER = PermissionKey("$MOD_ID.stats.view_player", PermissionDefault.OPERATORS)

    /** Clear a player's own statistics */
    val STATS_RESET_SELF = PermissionKey("$MOD_ID.stats.reset_self", PermissionDefault.ALLOW)

    /** Clear server-wide statistics */
    val STATS_RESET_GLOBAL = PermissionKey("$MOD_ID.stats.reset_global", PermissionDefault.OPERATORS)

    fun getPermissions() = this::class.declaredMemberProperties
        .filter { it.returnType == PermissionKey::class }
        .map { @Suppress("UNCHECKED_CAST") (it as KProperty1<Permission, PermissionKey>).get(this) }

}