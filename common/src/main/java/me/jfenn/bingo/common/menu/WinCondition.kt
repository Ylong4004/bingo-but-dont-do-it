package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.BingoWinCondition
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import org.joml.Vector3d

internal fun MenuComponent.registerWinCondition(
    position: Vector3d,
    width: Double = MENU_GOAL_WIDTH,
    options: BingoOptions = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val offset = Vector3d()

    registerTitlePanel(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        title = text.string(StringKey.OptionsWinCondition),
    )

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsWinConditionReplaceGoals),
        tooltip = buildTooltip(StringKey.OptionsWinConditionReplaceGoals),
        isActiveProp = computedProperty { options.winCondition is BingoWinCondition.ReplaceGoals }
    ) {
        optionsService.setWinCondition(OptionsService.Context(it), BingoWinCondition.ReplaceGoals)
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsWinConditionInfinite),
        tooltip = buildTooltip(StringKey.OptionsWinConditionInfinite),
        isActiveProp = computedProperty { options.winCondition is BingoWinCondition.Infinite }
    ) {
        optionsService.setWinCondition(OptionsService.Context(it), BingoWinCondition.Infinite)
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsWinConditionNumCards),
        tooltip = buildTooltip(StringKey.OptionsWinConditionNumCards),
        isActiveProp = computedProperty { options.winCondition is BingoWinCondition.Cards }
    ) {
        optionsService.setWinCondition(OptionsService.Context(it), BingoWinCondition.Cards(1))
    }

    val countProp = DelegatedProperty(
        getter = {
            when (val goal = options.winCondition) {
                is BingoWinCondition.Cards -> goal.cards
                else -> 0
            }
        },
        setter = { count ->
            options.winCondition = when (options.winCondition) {
                is BingoWinCondition.Cards -> BingoWinCondition.Cards(count)
                else -> options.winCondition
            }
        }
    )

    registerNumberInput(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        valueProp = countProp,
        minValueProp = ConstantProperty(1),
        maxValueProp = computedProperty { 100 },
        format = { text.cardCount(it) },
        isVisible = { options.winCondition is BingoWinCondition.Cards },
    )
}
