package me.jfenn.bingo.platform

import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface IPersistentStateManager {

    fun <T: Any> register(id: String, kType: KType, default: () -> T): IPersistentStateType<T>

    fun <T: Any> getFromWorld(type: IPersistentStateType<T>): T

    fun <T: Any> put(type: IPersistentStateType<T>, value: T)

}

interface IPersistentStateType<T>

inline fun <reified T: Any> IPersistentStateManager.register(id: String, noinline default: () -> T): IPersistentStateType<T> =
    register(id, typeOf<T>(), default)
