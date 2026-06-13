package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.BingoGoal
import me.jfenn.bingo.common.options.BingoWinCondition
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import org.joml.Vector3d

internal const val MENU_GOAL_WIDTH = 2.0

internal fun MenuComponent.registerGoal(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    text: TextProvider = koinScope.get(),
) {
    val offset = Vector3d()

    registerCardGoal(
        position = position + offset.sub(0.0, MENU_LINE_PADDING, 0.0),
    )

    registerTimer(
        position = position + offset.sub(0.0, 5*MENU_LINE_HEIGHT + 5*MENU_LINE_PADDING, 0.0),
        width = MENU_GOAL_WIDTH,
    )

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + 0.4, 0.0),
        height = 0.4,
        width = MENU_GOAL_WIDTH,
        icon = "⋯",
        text = text.string(StringKey.OptionsMore),
        brightness = MENU_BRIGHTNESS_ALT,
    ) {
        state.menu.page = MenuPage.GOAL
    }
}

internal fun MenuComponent.registerCardGoal(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
    text: TextProvider = koinScope.get(),
) {
    val offset = Vector3d()

    val supportsFullCard by computedProperty {
        state.options.winCondition !is BingoWinCondition.ReplaceGoals
    }

    registerTitlePanel(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = MENU_GOAL_WIDTH,
        title = text.string(StringKey.OptionsGoal),
    )

    val cardOptions by DelegatedProperty(
        getter = { state.getActiveCard().options },
        setter = {},
    )

    val countProp = DelegatedProperty(
        getter = {
            when (val goal = cardOptions.goal) {
                is BingoGoal.Items -> goal.items
                is BingoGoal.Lines -> goal.lines
            }
        },
        setter = {}
    )

    registerNumberInput(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = MENU_GOAL_WIDTH,
        height = MENU_LINE_HEIGHT,
        valueProp = countProp,
        minValueProp = ConstantProperty(1),
        maxValueProp = computedProperty {
            if (!supportsFullCard) return@computedProperty 100
            when (cardOptions.goal) {
                is BingoGoal.Items -> BingoGoal.MAX_ITEMS
                is BingoGoal.Lines -> BingoGoal.MAX_LINES
            }
        },
        format = { cardOptions.goal.format(text, supportsFullCard) },
    ) { player, count ->
        val newGoal = when (cardOptions.goal) {
            is BingoGoal.Items -> BingoGoal.Items(count)
            is BingoGoal.Lines -> BingoGoal.Lines(count)
        }
        optionsService.setGoal(
            ctx = OptionsService.Context(player),
            card = state.getActiveCard(),
            goal = newGoal,
        )
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = MENU_GOAL_WIDTH,
        height = MENU_LINE_HEIGHT,
        icon = "⇄",
        text = this.text.empty(),
        textProp = computedProperty { text.string(cardOptions.goal.string()) },
    ) { player ->
        val newGoal = when (val goal = cardOptions.goal) {
            is BingoGoal.Items -> BingoGoal.Lines(
                when {
                    supportsFullCard && goal.isFullCard() -> BingoGoal.MAX_LINES
                    supportsFullCard -> goal.items.coerceAtMost(BingoGoal.MAX_LINES)
                    else -> goal.items.coerceAtMost(BingoGoal.MAX_LINES)
                }
            )
            is BingoGoal.Lines -> BingoGoal.Items(
                when {
                    supportsFullCard && goal.isFullCard() -> BingoGoal.MAX_ITEMS
                    else -> goal.lines
                }
            )
        }
        optionsService.setGoal(
            ctx = OptionsService.Context(player),
            card = state.getActiveCard(),
            goal = newGoal,
        )
    }
}
