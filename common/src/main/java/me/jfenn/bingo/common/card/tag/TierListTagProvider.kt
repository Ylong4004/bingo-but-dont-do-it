package me.jfenn.bingo.common.card.tag

import me.jfenn.bingo.common.card.TagExpansionService
import me.jfenn.bingo.common.data.ScopedData

internal class TierListTagProvider(
    private val data: ScopedData,
    private val tagExpansionService: TagExpansionService,
): ITagProvider {
    override fun listTags(): Map<String, TagData> {
        return data.tierLists
            .mapValues { (_, config) ->
                TagData(
                    values = (config.s + config.a + config.b + config.c + config.d + config.values)
                        .map { it.item }
                        .let { tagExpansionService.expandItemTags(it) }
                )
            }
    }
}