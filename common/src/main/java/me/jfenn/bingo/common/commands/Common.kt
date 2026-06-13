package me.jfenn.bingo.common.commands

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.integrations.permissions.PermissionKey
import me.jfenn.bingo.platform.commands.IExecutionSource
import net.minecraft.util.Formatting

val IExecutionSource.runByName get() = when {
    isConsole -> "RCON"
    else -> playerOrThrow.playerName
}

fun IExecutionSource.hasState(vararg requiredState: GameState): Boolean {
    val state = scope.get<BingoState>()
    return requiredState.contains(state.state)
}

fun IExecutionSource.hasPermission(key: PermissionKey): Boolean {
    if (isConsole) return true
    val player = player ?: return false
    val permissions = scope.get<IPermissionsApi>()
    return permissions.hasPermission(player, key)
}

fun IExecutionSource.hasLobby(): Boolean {
    return scope.get<BingoState>().isLobbyMode
}

fun TextProvider.formatWarning(text: IText, first: Boolean = true) =
    literal(if (first) "⚠  " else "   ")
        .append(text)
        .formatted(Formatting.YELLOW)
