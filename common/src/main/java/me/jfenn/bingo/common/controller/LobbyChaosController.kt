package me.jfenn.bingo.common.controller

import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.integrations.permissions.IPermissionsApi
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.ActionResult
import me.jfenn.bingo.platform.event.model.UseBlockEvent

internal class LobbyChaosController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val config: BingoConfig,
    private val state: BingoState,
    private val permissions: IPermissionsApi,
    private val textProvider: TextProvider,
) : BingoComponent() {

    private fun shouldPreventLobbyChaos(player: IPlayerHandle): Boolean {
        return config.preventLobbyChaos &&
                state.state == GameState.PREGAME &&
                !permissions.hasPermission(player, Permission.BYPASS_CHAOS_PREVENTION)
    }

    private val hitBlockSet = setOf(
        "minecraft:barrel",
        "minecraft:chest",
        "minecraft:sweet_berry_bush",
        "minecraft:decorated_pot"
    )

    init {
        events.onPlayerJoin { (player) ->
            if (!state.isLobbyMode) return@onPlayerJoin
            if (config.preventLobbyChaos && state.state == GameState.PREGAME) {
                player.sendMessage(textProvider.string(StringKey.LobbySoundsMuted))
            }
        }

        eventBus.register(UseBlockEvent) {
            if (!state.isLobbyMode) return@register null
            val player = it.player

            if (config.preventLobbyChaos && state.state == GameState.PREGAME) {
                val blockState = player.world.getBlockState(BlockPosition.fromBlockPos(it.hit.blockPos))
                if (blockState.identifier in hitBlockSet) {
                    player.sendHotbarMessage(textProvider.string(StringKey.LobbySoundsMuted))
                }
            }

            // If preventLobbyChaos=true, prevent players from accessing the brewing stand
            // (levitation potions are annoying...)
            if (shouldPreventLobbyChaos(player)) {
                val blockState = player.world.getBlockState(BlockPosition.fromBlockPos(it.hit.blockPos))
                if (blockState.identifier == "minecraft:brewing_stand")
                    return@register ActionResult.Fail(Unit)
            }

            null
        }
    }

}