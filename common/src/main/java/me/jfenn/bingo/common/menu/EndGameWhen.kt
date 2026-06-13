package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.EndWhen
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import net.minecraft.util.Formatting
import org.joml.Vector3d

internal const val MENU_END_WHEN_WIDTH = 2.0

internal fun MenuComponent.registerEndGameWhen(
    position: Vector3d,
    width: Double = MENU_END_WHEN_WIDTH,
    options: BingoOptions = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val offset = Vector3d()

    registerTitlePanel(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        title = text.string(StringKey.OptionsWinBehaviorEndWhen),
    )

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsEndWhenFirstWin),
        tooltip = buildList {
            add(text.string(StringKey.OptionsEndWhen, StringKey.OptionsEndWhenFirstWin).formatted(Formatting.GREEN))
            add(text.string(StringKey.OptionsEndWhenFirstWinTooltip))
        },
        isActiveProp = computedProperty { options.endGameWhen == EndWhen.FirstWin }
    ) {
        optionsService.setEndWhen(OptionsService.Context(it), EndWhen.FirstWin)
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsEndWhenAllWin),
        tooltip = buildList {
            add(text.string(StringKey.OptionsEndWhen, StringKey.OptionsEndWhenAllWin).formatted(Formatting.GREEN))
            add(text.string(StringKey.OptionsEndWhenAllWinTooltip))
        },
        isActiveProp = computedProperty { options.endGameWhen == EndWhen.AllWin }
    ) {
        optionsService.setEndWhen(OptionsService.Context(it), EndWhen.AllWin)
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsEndWhenNever),
        tooltip = buildList {
            add(text.string(StringKey.OptionsEndWhen, StringKey.OptionsEndWhenNever).formatted(Formatting.GREEN))
            add(text.string(StringKey.OptionsEndWhenNeverTooltip))
        },
        isActiveProp = computedProperty { options.endGameWhen == EndWhen.Never }
    ) {
        optionsService.setEndWhen(OptionsService.Context(it), EndWhen.Never)
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsEndWhenNumTeamsWin),
        tooltip = buildList {
            add(text.string(StringKey.OptionsEndWhen, StringKey.OptionsEndWhenNumTeamsWin).formatted(Formatting.GREEN))
            add(text.string(StringKey.OptionsEndWhenNumTeamsWinTooltip))
        },
        isActiveProp = computedProperty { options.endGameWhen is EndWhen.TeamsWin }
    ) {
        optionsService.setEndWhen(OptionsService.Context(it), EndWhen.TeamsWin(2))
    }

    val countProp = DelegatedProperty(
        getter = {
            when (val endWhen = options.endGameWhen) {
                is EndWhen.TeamsWin -> endWhen.teams
                else -> 0
            }
        },
        setter = { count ->
            options.endGameWhen = when (options.endGameWhen) {
                is EndWhen.TeamsWin -> EndWhen.TeamsWin(count)
                else -> options.endGameWhen
            }
        }
    )

    registerNumberInput(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        height = MENU_LINE_HEIGHT,
        valueProp = countProp,
        minValueProp = ConstantProperty(2),
        maxValueProp = computedProperty { 100 },
        format = { text.teamCount(it) },
        isVisible = { options.endGameWhen is EndWhen.TeamsWin },
    )
}
