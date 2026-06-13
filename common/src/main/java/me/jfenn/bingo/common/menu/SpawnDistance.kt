package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import org.joml.Vector3d

const val MAX_SPAWN_DISTANCE = 500
internal const val MENU_SPAWN_DISTANCE_WIDTH = 2.0

internal fun MenuComponent.registerSpawnDistance(
    position: Vector3d,
    width: Double = MENU_SPAWN_DISTANCE_WIDTH,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val offset = Vector3d()

    registerTitlePanel(
        position = position + offset.sub(0.0, 2*MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        title = text.string(StringKey.OptionsSpawnDistance),
    )

    registerNumberInput(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        valueProp = propertyRef(state.options::spawnDistance),
        minValueProp = ConstantProperty(0),
        maxValueProp = ConstantProperty(MAX_SPAWN_DISTANCE),
        step = 5,
        format = { text.string(StringKey.OptionsSpawnDistanceChunks, it) },
        tooltip = buildTooltip(StringKey.OptionsSpawnDistance),
    ) { player, distance ->
        optionsService.setSpawnDistance(OptionsService.Context(player), distance)
    }
}
