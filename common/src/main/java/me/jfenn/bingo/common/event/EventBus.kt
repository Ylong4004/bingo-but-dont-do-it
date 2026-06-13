package me.jfenn.bingo.common.event

import me.jfenn.bingo.platform.event.ICallbackHandle
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.IReturnEvent
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.function.Function

internal class EventBus(
    private val log: Logger,
) : IEventBus {
    val events = ConcurrentHashMap<Any, CopyOnWriteArraySet<Function<*, *>>>()

    override fun <T : Any, R> register(type: IReturnEvent<T, R>, callback: Function<T, R>): ICallbackHandle {
        val subscribers = events.getOrPut(type) { CopyOnWriteArraySet() }
        subscribers.add(callback)
        log.debug("[EventBus] Registered {} from {}", type.name, callback.javaClass.simpleName)
        return CallbackHandle(type.name, subscribers, callback)
    }

    override fun <T : Any, R> emit(type: IReturnEvent<T, R>, event: T): List<R> {
        val subscribers = events[type]?.iterator()
            ?.let { @Suppress("UNCHECKED_CAST") (it as? Iterator<Function<T, R>>) }
            ?: return emptyList()

        val responses = mutableListOf<R>()
        for (subscriber in subscribers) {
            try {
                responses.add(subscriber.apply(event))
            } catch (e: Throwable) {
                log.error("Exception in event handler (${type.name}, ${subscriber.javaClass.simpleName}):", e)
            }
        }

        return responses
    }

    inner class CallbackHandle(
        private val name: String,
        private val subscribers: CopyOnWriteArraySet<Function<*, *>>,
        private val callback: Function<*, *>
    ) : ICallbackHandle {
        override fun close() {
            log.debug("[EventBus] Unregistered {} from {}", name, callback.javaClass.simpleName)
            subscribers.remove(callback)
        }
    }
}