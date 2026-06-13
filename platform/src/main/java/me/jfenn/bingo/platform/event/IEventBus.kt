package me.jfenn.bingo.platform.event

import java.util.function.Function

interface IEventBus {
    fun <T: Any, R> register(
        type: IReturnEvent<T, R>,
        callback: Function<T, R>
    ): ICallbackHandle

    fun <T: Any, R> emit(type: IReturnEvent<T, R>, event: T): List<R>
}
