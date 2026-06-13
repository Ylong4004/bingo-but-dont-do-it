package me.jfenn.bingo.platform.event.model

sealed class ActionResult<T>{
    abstract val value: T

    class Fail<T>(override val value: T) : ActionResult<T>()
    class Success<T>(override val value: T) : ActionResult<T>()
    class Pass<T>(override val value: T) : ActionResult<T>()

    fun <R> map(fn: (T) -> R): ActionResult<R> {
        return when (this) {
            is Fail -> Fail(fn(value))
            is Success -> Success(fn(value))
            is Pass -> Pass(fn(value))
        }
    }

    companion object {
        fun <T> collapse(results: List<ActionResult<T>?>): ActionResult<T>? {
            results.find { it is Fail<T> }
                ?.let { return it }

            results.find { it is Success<T> }
                ?.let { return it }

            // Otherwise, return a PASS result (preferring a returned value)
            return results.find { it is Pass<T> }
        }
    }
}