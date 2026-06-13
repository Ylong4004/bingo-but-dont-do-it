package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.card.filter.ObjectiveFilterService
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.generated.StringKey
import net.minecraft.util.Formatting
import org.joml.Vector3d

internal const val MENU_ITEM_FILTERS_WIDTH = 2.5

internal fun MenuComponent.registerItemFilters(
    position: Vector3d,
    width: Double = MENU_ITEM_FILTERS_WIDTH,
    height: Double,
    state: BingoState = koinScope.get(),
    objectiveFilterService: ObjectiveFilterService = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val presets = objectiveFilterService.getPresetFilters()

    val selectedIndexProp = DelegatedProperty(
        getter = {
            presets.values.indexOfFirst { itemFilter ->
                state.getActiveCard().options.itemFilter == itemFilter.value
            }
        },
        setter = {}
    )

    registerRadioMenu(
        position = position,
        width = width,
        height = height,
        title = text.string(StringKey.OptionsFilter),
        options = presets.values.map {
            it.name(this.text)
        },
        tooltips = presets.values.map { value ->
            buildList {
                add(
                    text.string(StringKey.OptionsFilter)
                        .append(": ")
                        .append(value.name(text))
                        .formatted(Formatting.GREEN)
                )
                add(text.string(StringKey.OptionsFilterTooltip))
                add(text.literal(value.value.toString()).formatted(Formatting.GRAY))
            }
        },
        selectedIndexProp = selectedIndexProp,
    ) { player, index ->
        val preset = presets.values.elementAtOrNull(index) ?: return@registerRadioMenu
        optionsService.setCardFilter(
            ctx = OptionsService.Context(player),
            card = state.getActiveCard(),
            filter = preset.value,
        )
    }
}