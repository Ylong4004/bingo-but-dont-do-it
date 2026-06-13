package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import org.joml.Vector3d
import java.util.*

const val MAX_TIMER_MINUTES: Int = 360

internal fun MenuComponent.registerTimer(
    position: Vector3d,
    width: Double,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    registerTitlePanel(
        position = position + Vector3d(0.0, MENU_LINE_HEIGHT + MENU_LINE_PADDING, 0.0),
        width = width,
        title = text.string(StringKey.OptionsTimeLimit),
    )

    val timerProp = DelegatedProperty(
        getter = { state.options.timeLimit?.toMinutes()?.toInt() ?: 0 },
        setter = {},
    )

    registerNumberInput(
        position = position,
        width = width,
        height = MENU_LINE_HEIGHT,
        valueProp = timerProp,
        step = 15,
        minValueProp = ConstantProperty(0),
        maxValueProp = ConstantProperty(MAX_TIMER_MINUTES),
        format = { minutes ->
            minutes.takeIf { it > 0 }
                ?.let {
                    String.format(Locale.US, "%dh %02dm", minutes / 60, minutes % 60)
                }
                ?.let { text.literal(it) }
                ?: text.string(StringKey.OptionsTimeLimitOff)
        }
    ) { player, minutes ->
        optionsService.setTimeLimit(
            OptionsService.Context(player),
            minutes,
        )
    }
}
