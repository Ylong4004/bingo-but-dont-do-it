package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.options.StalemateBehavior
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import net.minecraft.util.Formatting
import org.joml.Vector3d

internal const val MENU_STALEMATE_WIDTH = 2.0

internal fun MenuComponent.registerStalemateBehavior(
    position: Vector3d,
    width: Double = MENU_STALEMATE_WIDTH,
    options: BingoOptions = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val offset = Vector3d()

    registerTitlePanel(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = width,
        title = text.string(StringKey.OptionsWinBehaviorWhenStalemate),
    )

    for (behavior in StalemateBehavior.entries) {
        registerTileButton(
            position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
            width = width,
            height = MENU_LINE_HEIGHT,
            text = text.string(behavior.string),
            tooltip = buildList {
                add(text.string(StringKey.OptionsWinBehaviorStalemate, behavior.string).formatted(Formatting.GREEN))
                add(text.string(
                    StringKey.entries
                        .find { it.key == behavior.string.key + ".tooltip" }
                        ?: error("No string found for $behavior tooltip!")
                ))
            },
            isActiveProp = computedProperty { options.stalemateBehavior == behavior }
        ) {
            optionsService.setStalemateBehavior(OptionsService.Context(it), behavior)
        }
    }
}
