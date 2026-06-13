package me.jfenn.bingo.common.card

import me.jfenn.bingo.common.card.objective.objectiveError
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.platform.ITagAccessor
import org.slf4j.Logger

internal class TagExpansionService(
    private val log: Logger,
    private val tagAccessor: ITagAccessor,
    private val data: ScopedData,
) {

    fun expandItemTag(
        item: String,
        visited: MutableSet<String> = mutableSetOf()
    ) : Set<String> {
        return if (item.startsWith("#")) {
            // if the tag has already been visited, don't iterate through it again
            if (!visited.add(item)) return emptySet()

            tagAccessor.getItemTag(item.substring(1))
                ?.let { expandItemTags(it, visited) }
                ?: data.tags[item.substringAfter(':')]
                    ?.let { expandItemTags(it.values, visited) }
                ?: run {
                    log.objectiveError(null, "Item tag '$item' is missing!")
                    emptySet()
                }
        } else {
            setOf(item)
        }
    }

    fun expandItemTags(
        items: Iterable<String>,
        visited: MutableSet<String> = mutableSetOf()
    ) : Set<String> {
        return items
            .flatMap { expandItemTag(it, visited) }
            .toSet()
    }

}