package me.jfenn.bingo.mixinhandler

import me.jfenn.bingo.platform.text.IText
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Optional scoped decorations that are composed after Bingo's built-in Ready marker. */
object PlayerListNameDecorators {
    fun interface Decorator {
        /** Returns null when this decorator has nothing to add for [uuid]. */
        fun decorate(uuid: UUID, current: IText): IText?
    }

    private val decorators = CopyOnWriteArrayList<Decorator>()

    fun register(decorator: Decorator): AutoCloseable {
        decorators += decorator
        return AutoCloseable { decorators -= decorator }
    }

    fun apply(uuid: UUID, base: IText): IText? {
        var current = base
        var changed = false
        decorators.forEach { decorator ->
            decorator.decorate(uuid, current)?.let {
                current = it
                changed = true
            }
        }
        return current.takeIf { changed }
    }
}
