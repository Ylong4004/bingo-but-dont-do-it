package me.jfenn.bingo.common.event

import me.jfenn.bingo.platform.event.ICallbackHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.IReturnEvent
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Function

internal class ScopedEventBus(
    private val eventBus: IEventBus,
    scope: Scope,
) : IEventBus, ScopeCallback {
    private val handles = ConcurrentLinkedQueue<Closeable>()

    init {
        scope.registerCallback(this)
    }

    override fun <T : Any, R> register(type: IReturnEvent<T, R>, callback: Function<T, R>): ICallbackHandle {
        return eventBus.register(type, callback)
            .also { handles.add(it) }
    }

    override fun <T : Any, R> emit(type: IReturnEvent<T, R>, event: T): List<R> {
        return eventBus.emit(type, event)
    }

    override fun onScopeClose(scope: Scope) {
        for (handle in handles) {
            handle.close()
        }
        handles.clear()
    }
}