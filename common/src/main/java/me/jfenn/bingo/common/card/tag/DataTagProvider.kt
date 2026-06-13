package me.jfenn.bingo.common.card.tag

import me.jfenn.bingo.common.card.TagExpansionService
import me.jfenn.bingo.common.data.ScopedData

internal class DataTagProvider(
    private val data: ScopedData,
    private val tagExpansionService: TagExpansionService,
): ITagProvider {
    override fun listTags(): Map<String, TagData> = data.tags
        .mapValues {
            it.value.copy(
                values = tagExpansionService.expandItemTags(it.value.values)
            )
        }
}