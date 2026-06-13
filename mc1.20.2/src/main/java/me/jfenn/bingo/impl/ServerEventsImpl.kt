package me.jfenn.bingo.impl

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.*
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.*
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceReloader
import net.minecraft.resource.ResourceType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.util.profiler.Profiler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

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

    private fun <T> ActionResult<T>.toTypedActionResult(): TypedActionResult<T> {
        return when (this) {
            is ActionResult.Fail -> TypedActionResult.fail(value)
            is ActionResult.Success -> TypedActionResult.success(value)
            is ActionResult.Pass -> TypedActionResult.pass(value)
        }
    }

    companion object {
        var afterSaveCallback: ((MinecraftServer) -> Unit)? = null
    }

    init {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            eventBus.emit(ServerEvent.Started, ServerEvent(server))
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            eventBus.emit(ServerEvent.Stopped, ServerEvent(server))
        }

        afterSaveCallback = { server ->
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
            eventBus.emit(EntityLoadEvent, EntityLoadEvent(world.server, entity, world))
        }

        UseBlockCallback.EVENT.register { player, world, hand, hit ->
            if (player !is ServerPlayerEntity) return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseBlockEvent, UseBlockEvent(player.server, playerImpl, world, hand, hit))
            ActionResult.collapse(results)
                ?.toActionResult()
                ?: net.minecraft.util.ActionResult.PASS
        }

        UseEntityCallback.EVENT.register { player, world, hand, entity, hit ->
            if (player !is ServerPlayerEntity) return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseEntityEvent, UseEntityEvent(player.server, playerImpl, world, hand, entity, hit))
            ActionResult.collapse(results)
                ?.toActionResult()
                ?: net.minecraft.util.ActionResult.PASS
        }

        UseItemCallback.EVENT.register { player, world, hand ->
            if (player !is ServerPlayerEntity) return@register TypedActionResult.pass(null)
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(UseItemEvent, UseItemEvent(player.server, playerImpl, world, hand))
            ActionResult.collapse(results)
                ?.map { it?.stack }
                ?.toTypedActionResult()
                ?: TypedActionResult.pass(null)
        }

        AttackEntityCallback.EVENT.register { player, world, hand, entity, hit ->
            if (player !is ServerPlayerEntity) return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(AttackEntityEvent, AttackEntityEvent(player.server, playerImpl, world, hand, entity, hit))
            ActionResult.collapse(results)
                ?.toActionResult()
                ?: net.minecraft.util.ActionResult.PASS
        }

        AttackBlockCallback.EVENT.register { player, world, hand, blockPos, direction ->
            if (player !is ServerPlayerEntity) return@register net.minecraft.util.ActionResult.PASS
            val playerImpl = getPlayerImpl(player)
            val results = eventBus.emit(
                AttackBlockEvent,
                AttackBlockEvent(
                    player.server,
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
            eventBus.emit(PlayerEvent.ChannelRegister, PlayerEvent(player))
        }

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(
            object : IdentifiableResourceReloadListener {
                override fun reload(
                    synchronizer: ResourceReloader.Synchronizer,
                    manager: ResourceManager,
                    prepareProfiler: Profiler,
                    applyProfiler: Profiler,
                    prepareExecutor: Executor,
                    applyExecutor: Executor
                ): CompletableFuture<Void?> {
                    val reloadEvent = ReloadEvent(
                        resourceManager = manager,
                        prepareExecutor = prepareExecutor,
                        applyExecutor = applyExecutor,
                        whenPrepared = synchronizer::whenPrepared,
                    )
                    return eventBus.emit(ReloadEvent, reloadEvent)
                        .takeIf { it.isNotEmpty() }
                        ?.let { CompletableFuture.allOf(*it.toTypedArray()) }
                        ?: CompletableFuture.completedFuture(reloadEvent)
                            .thenCompose(synchronizer::whenPrepared)
                            .thenAcceptAsync({}, applyExecutor)
                }

                override fun getName(): String = MOD_ID_BINGO

                override fun getFabricId(): Identifier = Identifier.of(MOD_ID_BINGO, "server_reload")!!
            }
        )

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