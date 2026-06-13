package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.options.BingoCardOptions
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import org.joml.Vector3d

private const val TILE_HEIGHT = 0.7
private const val TILE_WIDTH = 1.2
private const val TILE_MARGIN = 0.1
internal const val GAME_MODES_WIDTH = TILE_WIDTH + TILE_MARGIN + TILE_WIDTH

internal const val ICON_LOCKOUT = "\uD83D\uDD12"
internal const val ICON_INVENTORY = "\uD83C\uDF92"
internal const val ICON_HIDDEN = "❓"
internal const val ICON_CONSUME = "⬚"

internal fun MenuComponent.registerGameModes(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val cardOptions by DelegatedProperty(
        getter = { state.getActiveCard().options },
        setter = {},
    )

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = GAME_MODES_WIDTH,
        title = text.string(StringKey.OptionsGameMode),
    )

    val offsetY = 0.6
    val offsetX = (TILE_WIDTH + TILE_MARGIN)/2.0

    registerIconButton(
        position = position + Vector3d(-offsetX, -(offsetY + TILE_HEIGHT), 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = ICON_LOCKOUT,
        text = text.string(StringKey.OptionsModeLockout),
        tooltip = buildTooltip(StringKey.OptionsModeLockout),
        isActiveProp = computedProperty { cardOptions.isLockoutMode },
    ) {
        optionsService.toggleCardMode(
            ctx = OptionsService.Context(it),
            card = state.getActiveCard(),
            prop = BingoCardOptions::isLockoutMode,
        )
    }

    registerIconButton(
        position = position + Vector3d(offsetX, -(offsetY + TILE_HEIGHT), 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = ICON_INVENTORY,
        text = text.string(StringKey.OptionsModeInventory),
        tooltip = buildTooltip(StringKey.OptionsModeInventory),
        isActiveProp = computedProperty { cardOptions.isInventoryMode },
    ) {
        optionsService.toggleCardMode(
            ctx = OptionsService.Context(it),
            card = state.getActiveCard(),
            prop = BingoCardOptions::isInventoryMode,
        )
    }

    registerIconButton(
        position = position + Vector3d(-offsetX, -(offsetY + 2*TILE_HEIGHT + TILE_MARGIN), 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = ICON_HIDDEN,
        text = text.string(StringKey.OptionsModeHiddenItems),
        tooltip = buildTooltip(StringKey.OptionsModeHiddenItems),
        isActiveProp = computedProperty { cardOptions.isHiddenItemsMode },
    ) {
        optionsService.toggleCardMode(
            ctx = OptionsService.Context(it),
            card = state.getActiveCard(),
            prop = BingoCardOptions::isHiddenItemsMode,
        )
    }

    registerIconButton(
        position = position + Vector3d(offsetX, -(offsetY + 2*TILE_HEIGHT + TILE_MARGIN), 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = ICON_CONSUME,
        text = text.string(StringKey.OptionsModeConsumeItems),
        tooltip = buildTooltip(StringKey.OptionsModeConsumeItems),
        isActiveProp = computedProperty { cardOptions.isConsumeItemsMode },
    ) {
        optionsService.toggleCardMode(
            ctx = OptionsService.Context(it),
            card = state.getActiveCard(),
            prop = BingoCardOptions::isConsumeItemsMode,
        )
    }

    registerTileButton(
        position = position + Vector3d(0.0, -(offsetY + 2*TILE_HEIGHT + 2*TILE_MARGIN + 0.4), 0.0),
        height = 0.4,
        width = GAME_MODES_WIDTH,
        icon = "⋯",
        text = text.string(StringKey.OptionsFeatures),
        brightness = MENU_BRIGHTNESS_ALT,
    ) {
        state.menu.page = MenuPage.FEATURES
    }
}