package me.jfenn.bingo.common.card.filter

import kotlinx.serialization.Serializable

@Serializable(with = ObjectiveFilterSerializer::class)
data class ObjectiveFilterList(
    val list: List<ObjectiveFilter> = emptyList(),
) : Iterable<ObjectiveFilter> {

    constructor(vararg filter: ObjectiveFilter) : this(filter.toList())

    override fun iterator(): Iterator<ObjectiveFilter> {
        return list.iterator()
    }

    override fun toString(): String {
        return list.joinToString(" ") { it.asString() }
    }

    companion object {
        val EVERYTHING = ObjectiveFilterList(
            ObjectiveFilter.Exclude(ObjectiveFilter.UNOBTAINABLE),
            ObjectiveFilter.Exclude(ObjectiveFilter.TEDIOUS)
        )

        val PRESETS = mapOf(
            ObjectiveFilter.EVERYTHING to EVERYTHING
        )

        private fun ObjectiveFilter.asString() = when (this) {
            is ObjectiveFilter.Include -> "+$tag"
            is ObjectiveFilter.Exclude -> "-$tag"
            is ObjectiveFilter.Count -> "$tag#$count"
        }

        fun fromString(str: String): ObjectiveFilterList {
            val list = str.split(" ")
                .mapNotNull {
                    when {
                        it.startsWith('+') -> ObjectiveFilter.Include(it.substring(1))
                        it.startsWith('-') -> ObjectiveFilter.Exclude(it.substring(1))
                        it.contains("#") -> {
                            val tag = it.substringBefore('#')
                            val count = it.substringAfter('#').toIntOrNull() ?: 0
                            ObjectiveFilter.Count(tag, count)
                        }
                        else -> null
                    }
                }
                .distinctBy { it.tag }
                .sortedBy { it.asString() }

            return ObjectiveFilterList(list)
        }
    }
}
