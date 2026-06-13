package me.jfenn.bingo.common.card.filter

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.text.TextProvider
import net.minecraft.util.Formatting

internal class ObjectiveFilterService(
    private val config: BingoConfig,
    private val data: ScopedData,
    private val text: TextProvider,
) {
    fun getPresetFilters(): Map<String, ObjectiveFilterPreset> {
        val configPresets = config.itemFilterPresets
            .map { (id, value) -> ObjectiveFilterPreset(id, null, value) }
            .associateBy { it.id }

        val dataPresets = data.filterPresets.filters
            .associateBy { it.id }

        return configPresets + dataPresets
    }

    fun formatFilter(itemFilter: ObjectiveFilterList, includePresetDetails: Boolean): IText {
        return getPresetFilters().values
            .find { filter -> filter.value == itemFilter }
            ?.let { filter ->
                val name = filter.name(text)
                when {
                    includePresetDetails -> name.append(" ")
                        .append(text.literal("(${filter.value})").formatted(Formatting.GRAY))
                    else -> name
                }
            }
            ?: text.literal(itemFilter.toString())
    }
}