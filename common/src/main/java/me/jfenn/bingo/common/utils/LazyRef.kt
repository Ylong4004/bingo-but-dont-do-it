package me.jfenn.bingo.common.utils

import java.lang.ref.WeakReference
import kotlin.reflect.KProperty

class LazyRefHolder<T>(
    private var factory: () -> T
) {

    private var weakRef: WeakReference<T>? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return weakRef?.get()
            ?: factory().also {
                weakRef = WeakReference(it)
            }
    }
}

fun <T> lazyRef(factory: () -> T) = LazyRefHolder<T>(factory)
