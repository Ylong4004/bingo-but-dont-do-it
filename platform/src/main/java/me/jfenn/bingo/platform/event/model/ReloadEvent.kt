package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.event.IEvent
import me.jfenn.bingo.platform.event.IReturnEvent
import net.minecraft.resource.ResourceManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

data class ReloadEvent(
    val resourceManager: ResourceManager,
    val prepareExecutor: Executor,
    val applyExecutor: Executor,
    val whenPrepared: (obj: Any?) -> CompletableFuture<Any?> = { CompletableFuture.completedFuture(null) },
) {
    companion object : IReturnEvent<ReloadEvent, CompletableFuture<Void>>
    class After {
        companion object : IEvent<After>
    }
}
