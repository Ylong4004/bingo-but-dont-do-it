package me.jfenn.bingo.platform.event

interface IReturnEvent<T: Any, R> {
    val name: String get() = this::class.qualifiedName.toString()
}

interface IEvent<T: Any> : IReturnEvent<T, Unit>
