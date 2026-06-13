package me.jfenn.bingo.impl

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.*
import me.jfenn.bingo.platform.item.IItemStack
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.*
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.resource.ResourceType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import java.util.concurrent.CompletableFuture

class ServerEventsImpl(
    eventBus: IEventBus,
) {
    private fun getPlayerImpl(player: ServerPlayerEntity): IPlayerHandle {
        return PlayerHandle(player, null)
    }

    private fun ActionResult<Unit>.toActionResult(): net.minecraft.util.ActionResult {
        return when (this) {
            is ActionResult.Fail -> net.minecraft.util.ActionResult.FAIL
            is ActionResult.Success -> net.minecraft.util.ActionResult.SUCCESS
            is ActionResult.Pass -> net.minecraft.util.ActionResult.PASS
        }
    }

    private fun ActionResult<IItemStack?>.toTypedActionResult(): net.minecraft.util.ActionResult {
        return takeIf { it is ActionResult.Success }
            ?.value?.stack
            ?.let { net.minecraft.util.ActionResult.SUCCESS.withNewHandStack(it) }
            ?: this.map {}.toActionResult()
    }

    init {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            eventBus.emit(ServerEvent.Started, ServerEvent(server))
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            eventBus.emit(ServerEvent.Stopped, ServerEvent(server))
        }

        ServerLifecycleEvents.AFTER_SAVE.register { server, flush, force ->
            eventBus.emit(ServerEvent.Saved, ServerEvent(server))
        }

        ServerTickEvents.START_SERVER_TICK.register {
            val event = TickEvent(it.ticks)
            eventBus.emit(TickEvent.Start, event)
        }

        ServerTickEvents.END_SERVER_TICK.register {
            eventBus.emit(TickEvent.End, TickEvent(it.ticks))
        }

        ServerPlayConnectionEvents.INIT.register { handler, _ ->
            val player = getPlayerImpl(handler.player)
            eventBus.emit(PlayerEvent.Init, PlayerEvent(player))
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = getPlayerImpl(handler.player)
            eventBus.emit(PlayerEvent.Join, PlayerEvent(player))
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = getPlayerImpl(handler.player)
            eventBus.emit(PlayerEvent.Disconnect, PlayerEvent(player))
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { _, player, _ ->
            val playerImpl = getPlayerImpl(player)
            eventBus.emit(PlayerEvent.AfterRespawn, PlayerEvent(playerImpl))
        }

        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            val server = world.server ?: return@register
            eventBus.emit(EntityLoadEvent, EntityLoadEvent(server, entity, world))
        }

        UseBlockCallback.EVENT.register { player, world, hand, hit ->
            if (player !is ServerPlayerEntity || world !is ServerWorld) return@register net.minecraft.util.ActionResult.PASS
            val server = world.server ?: return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseBlockEvent, UseBlockEvent(server, playerImpl, world, hand, hit))
            ActionResult.collapse(results)
                ?.toActionResult()
                ?: net.minecraft.util.ActionResult.PASS
        }

        UseEntityCallback.EVENT.register { player, world, hand, entity, hit ->
            if (player !is ServerPlayerEntity || world !is ServerWorld) return@register net.minecraft.util.ActionResult.PASS
            val server = world.server ?: return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseEntityEvent, UseEntityEvent(server, playerImpl, world, hand, entity, hit))
            ActionResult.collapse(results)
                ?.toActionResult()
                ?: net.minecraft.util.ActionResult.PASS
        }

        UseItemCallback.EVENT.register { player, world, hand ->
            if (player !is ServerPlayerEntity || world !is ServerWorld) return@register net.minecraft.util.ActionResult.PASS
            val server = world.server ?: return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseItemEvent, UseItemEvent(server, playerImpl, world, hand))
            ActionResult.collapse(results)
                ?.toTypedActionResult()
                ?: net.minecraft.util.ActionResult.PASS
        }

        AttackEntityCallback.EVENT.register { player, world, hand, entity, hit ->
            if (player !is ServerPlayerEntity || world !is ServerWorld) return@register net.minecraft.util.ActionResult.PASS
            val server = world.server ?: return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(AttackEntityEvent, AttackEntityEvent(server, playerImpl, world, hand, entity, hit))
            ActionResult.collapse(results)
                ?.toActionResult()
                ?: net.minecraft.util.ActionResult.PASS
        }

        AttackBlockCallback.EVENT.register { player, world, hand, blockPos, direction ->
            if (player !is ServerPlayerEntity || world !is ServerWorld) return@register net.minecraft.util.ActionResult.PASS
            val server = world.server ?: return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(
                AttackBlockEvent,
                AttackBlockEvent(
                    server,
                    playerImpl,
                    world,
                    hand,
                    blockPos,
                    direction
                )
            )
            ActionResult.collapse(results)
                ?.toActionResult()
                ?: net.minecraft.util.ActionResult.PASS
        }

        S2CPlayChannelEvents.REGISTER.register { handler, _, _, _ ->
            val player = getPlayerImpl(handler.player)
            try {
                ServerPlayNetworking.getSendable(handler.player)
            } catch (_: NullPointerException) {
                // The player is not set up yet
                return@register
            }

            eventBus.emit(PlayerEvent.ChannelRegister, PlayerEvent(player))
        }

        ResourceLoader.get(ResourceType.SERVER_DATA).registerReloader(
            Identifier.of(MOD_ID_BINGO, "server_reload")!!
        ) { store, prepareExecutor, reloadSynchronizer, applyExecutor ->
            val reloadEvent = ReloadEvent(
                resourceManager = store.resourceManager,
                prepareExecutor = prepareExecutor,
                applyExecutor = applyExecutor,
                whenPrepared = reloadSynchronizer::whenPrepared,
            )
            eventBus.emit(ReloadEvent, reloadEvent)
                .takeIf { it.isNotEmpty() }
                ?.let { CompletableFuture.allOf(*it.toTypedArray()) }
                ?: CompletableFuture.completedFuture(reloadEvent)
                    .thenCompose(reloadSynchronizer::whenPrepared)
                    .thenAcceptAsync({}, applyExecutor)
        }

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, _ ->
            eventBus.emit(ReloadEvent.After, ReloadEvent.After())
        }

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, player, _ ->
            val event = AllowChatMessageEvent(
                message = SignedMessageImpl(message),
                player = getPlayerImpl(player),
            )
            val isAllowed = eventBus.emit(AllowChatMessageEvent, event)
                .all { it }

            isAllowed
        }
    }
}