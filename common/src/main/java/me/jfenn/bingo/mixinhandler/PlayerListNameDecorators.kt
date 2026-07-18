package me.jfenn.bingo.mixinhandler

import me.jfenn.bingo.platform.text.IText
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** 在 Bingo 内置准备标记之后依次组合的可选作用域显示名装饰器。 */
object PlayerListNameDecorators {
    fun interface Decorator {
        /** 此装饰器无需为 [uuid] 添加内容时返回空。 */
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
