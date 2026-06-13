package me.jfenn.bingo.common.card.tag

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class TagData(
    val values: Set<String>,
    val replace: Boolean = false,
) {

    @Transient
    var shouldValidate: Boolean = false

    companion object {
        val EMPTY = TagData(values = emptySet())
    }

    operator fun plus(other: TagData): TagData {
        return when {
            other.replace -> other
            else -> TagData(
                values = this.values + other.values,
            )
        }
    }

    fun contains(value: String): Boolean {
        return values.contains(value)
    }

    operator fun plus(value: String) = this.copy(values = values + value).sort()
    operator fun minus(value: String) = this.copy(values = values - value).sort()

    fun sort() = this.copy(values = values.sorted().toSet())

    fun isEmpty() = values.isEmpty()
}