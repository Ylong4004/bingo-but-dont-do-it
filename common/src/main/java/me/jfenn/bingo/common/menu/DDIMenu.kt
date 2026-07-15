package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.DDIObjectiveMode
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.text.IText
import org.joml.Vector3d

internal const val MENU_DDI_ENABLE_WIDTH = 2.0
internal const val MENU_DDI_MODE_WIDTH = 2.6
internal const val MENU_DDI_HEARTS_WIDTH = 2.2
internal const val MENU_DDI_TIMER_WIDTH = 2.5

private const val PANEL_BOTTOM = -2.6
private const val PANEL_HEIGHT = 1.9
private const val NUMBER_INPUT_Y = -1.25
private const val NUMBER_INPUT_HEIGHT = 0.5
private const val PRESET_Y = -2.05
private const val PRESET_HEIGHT = 0.55
private const val PRESET_MARGIN = 0.1

internal fun MenuComponent.registerDDIEnable(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_ENABLE_WIDTH,
        title = text.string(StringKey.DdiWordTitle),
    )

    registerToggleButton(
        position = position + Vector3d(0.0, PANEL_BOTTOM, 0.0),
        width = MENU_DDI_ENABLE_WIDTH,
        height = PANEL_HEIGHT,
        textProp = computedProperty {
            text.string(
                if (options.enableDDI) StringKey.DdiMenuEnabled
                else StringKey.DdiMenuDisabled
            )
        },
        toggleProp = propertyRef(options::enableDDI),
        tooltip = buildTooltip(StringKey.DdiOptionEnable),
    ) { player ->
        optionsService.setDDIEnabled(
            OptionsService.Context(player),
            options.enableDDI,
        )
    }
}

internal fun MenuComponent.registerDDIMode(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options
    val modes = DDIObjectiveMode.entries
    val selectedMode = DelegatedProperty(
        getter = { modes.indexOf(options.ddiObjectiveMode) },
        setter = { options.ddiObjectiveMode = modes[it] },
    )

    registerRadioMenu(
        position = position,
        width = MENU_DDI_MODE_WIDTH,
        height = 2.4,
        title = text.string(StringKey.DdiMenuMode),
        options = modes.map { mode -> text.string(mode.stringKey()) },
        tooltips = modes.map { mode -> buildTooltip(mode.stringKey()) },
        optionsPerPage = 2,
        selectedIndexProp = selectedMode,
    ) { player, index ->
        optionsService.setDDIObjectiveMode(
            OptionsService.Context(player),
            modes[index],
        )
    }
}

internal fun MenuComponent.registerDDIHearts(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_HEARTS_WIDTH,
        title = text.string(StringKey.DdiMenuHearts),
    )
    registerNumberInput(
        position = position + Vector3d(0.0, NUMBER_INPUT_Y, 0.0),
        width = MENU_DDI_HEARTS_WIDTH,
        height = NUMBER_INPUT_HEIGHT,
        valueProp = propertyRef(options::ddiMaxHearts),
        minValueProp = ConstantProperty(1),
        maxValueProp = ConstantProperty(20),
        format = { text.string(StringKey.DdiOptionHeartsValue, it) },
        tooltip = buildTooltip(StringKey.DdiOptionHearts),
    ) { player, hearts ->
        optionsService.setDDIMaxHearts(OptionsService.Context(player), hearts)
    }

    registerDDIPresets(
        position = position + Vector3d(0.0, PRESET_Y, 0.0),
        width = MENU_DDI_HEARTS_WIDTH,
        values = listOf(1, 3, 5),
        selected = { options.ddiMaxHearts },
        format = { text.string(StringKey.DdiOptionHeartsValue, it) },
    ) { player, hearts ->
        optionsService.setDDIMaxHearts(OptionsService.Context(player), hearts)
    }
}

internal fun MenuComponent.registerDDITimer(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_TIMER_WIDTH,
        title = text.string(StringKey.DdiMenuTimer),
    )
    registerNumberInput(
        position = position + Vector3d(0.0, NUMBER_INPUT_Y, 0.0),
        width = MENU_DDI_TIMER_WIDTH,
        height = NUMBER_INPUT_HEIGHT,
        valueProp = propertyRef(options::ddiWordTimerSeconds),
        minValueProp = ConstantProperty(10),
        maxValueProp = ConstantProperty(600),
        step = 10,
        format = { text.string(StringKey.DdiOptionTimerValue, it) },
        tooltip = buildTooltip(StringKey.DdiOptionTimer),
    ) { player, seconds ->
        optionsService.setDDIWordTimerSeconds(OptionsService.Context(player), seconds)
    }

    registerDDIPresets(
        position = position + Vector3d(0.0, PRESET_Y, 0.0),
        width = MENU_DDI_TIMER_WIDTH,
        values = listOf(30, 60, 120),
        selected = { options.ddiWordTimerSeconds },
        format = { text.string(StringKey.DdiOptionTimerValue, it) },
    ) { player, seconds ->
        optionsService.setDDIWordTimerSeconds(OptionsService.Context(player), seconds)
    }
}

private fun MenuComponent.registerDDIPresets(
    position: Vector3d,
    width: Double,
    values: List<Int>,
    selected: () -> Int,
    format: (Int) -> IText,
    onSelect: (IPlayerHandle, Int) -> Unit,
) {
    val buttonWidth = (width - PRESET_MARGIN*(values.size - 1)) / values.size
    val left = -width/2 + buttonWidth/2
    values.forEachIndexed { index, value ->
        registerTileButton(
            position = position + Vector3d(
                left + index*(buttonWidth + PRESET_MARGIN),
                0.0,
                0.0,
            ),
            width = buttonWidth,
            height = PRESET_HEIGHT,
            textProp = computedProperty { format(value) },
            isActiveProp = computedProperty { selected() == value },
        ) { player ->
            onSelect(player, value)
        }
    }
}

private fun DDIObjectiveMode.stringKey(): StringKey = when (this) {
    DDIObjectiveMode.INDIVIDUAL -> StringKey.DdiOptionModeIndividual
    DDIObjectiveMode.TEAM_SHARED -> StringKey.DdiOptionModeTeam
}
