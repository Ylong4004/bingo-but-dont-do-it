package me.jfenn.bingo.common.card.tag

import me.jfenn.bingo.common.card.filter.ObjectiveFilter
import me.jfenn.bingo.common.utils.cacheFor
import me.jfenn.bingo.common.utils.seconds

class TagService(
    private val providers: List<ITagProvider>
) {

    private val tagsCache = cacheFor(5.seconds) { _: Unit ->
        providers
            .flatMap { it.listTags().toList() }
            .groupBy { (name, _) -> name }
            .mapValues { (_, tags) ->
                tags
                    .map { it.second }
                    .reduce { acc, tag -> acc.plus(tag) }
            }
            .toMap()
            .plus(ObjectiveFilter.UNCATEGORIZED to TagData.EMPTY)
    }

    fun getTags() = tagsCache.get(Unit)
}