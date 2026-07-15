package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.commands.BingoPrefsCommand
import me.jfenn.bingo.common.config.PlayerSettings
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import net.minecraft.util.Formatting
import org.joml.Vector3d

private const val TILE_HEIGHT = 0.65
private const val TILE_WIDTH = 1.05
private const val TILE_MARGIN = 0.1
private const val DDI_LINK_HEIGHT = 0.4
internal const val MENU_FEATURES_WIDTH = 3*TILE_WIDTH + 2*TILE_MARGIN

internal const val ICON_KEEP_INVENTORY = "\uD83C\uDF92"
internal const val ICON_ELYTRA = "\uD83E\uDD87"
internal const val ICON_NIGHTVIS = "\uD83D\uDC41"
internal const val ICON_PVP = "\uD83D\uDDE1"

internal fun MenuComponent.registerFeatures(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options by state::options

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_FEATURES_WIDTH,
        title = text.string(StringKey.OptionsFeatures),
    )

    val offsetY = 0.6
    val columnOffset = TILE_WIDTH + TILE_MARGIN
    val firstRowY = -(offsetY + TILE_HEIGHT)
    val secondRowY = -(offsetY + 2*TILE_HEIGHT + TILE_MARGIN)

    registerIconButton(
        position = position + Vector3d(-columnOffset, firstRowY, 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = "᎒᎒᎒",
        text = text.string(StringKey.OptionsPreviewCard),
        tooltip = buildTooltip(StringKey.OptionsPreviewCard),
        isActiveProp = computedProperty { options.showPreviewCard },
    ) {
        optionsService.togglePreviewCard(OptionsService.Context(it))
    }

    registerIconButton(
        position = position + Vector3d(0.0, firstRowY, 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = ICON_KEEP_INVENTORY,
        text = text.string(StringKey.OptionsKeepInventory),
        tooltip = listOf(
            text.string(StringKey.OptionsKeepInventory).formatted(Formatting.GREEN),
            this.text.translatable("gamerule.keepInventory", null),
        ),
        isActiveProp = computedProperty { options.isKeepInventory },
    ) {
        optionsService.toggleKeepInventory(OptionsService.Context(it))
    }

    registerIconButton(
        position = position + Vector3d(columnOffset, firstRowY, 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = ICON_ELYTRA,
        text = text.string(StringKey.OptionsElytra),
        tooltip = buildTooltip(StringKey.OptionsElytra),
        isActiveProp = computedProperty { options.isElytra },
    ) {
        optionsService.toggleElytra(OptionsService.Context(it))
    }

    registerIconButton(
        position = position + Vector3d(-columnOffset, secondRowY, 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = ICON_NIGHTVIS,
        text = text.string(StringKey.OptionsNightVis),
        tooltip = buildTooltip(StringKey.OptionsNightVis) +
                text.string(StringKey.OptionsNightVisTooltip_2, BingoPrefsCommand.getCommand(PlayerSettings::nightVision)),
        isActiveProp = computedProperty { options.isNightVision },
    ) {
        optionsService.toggleNightVision(OptionsService.Context(it))
    }

    registerIconButton(
        position = position + Vector3d(0.0, secondRowY, 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = ICON_PVP,
        text = text.string(StringKey.OptionsAllowPvp),
        tooltip = buildTooltip(StringKey.OptionsAllowPvp),
        isActiveProp = computedProperty { options.isPvpEnabled },
    ) {
        optionsService.togglePvp(OptionsService.Context(it))
    }

    registerIconButton(
        position = position + Vector3d(columnOffset, secondRowY, 0.0),
        width = TILE_WIDTH,
        height = TILE_HEIGHT,
        icon = "\uD83D\uDD6E",
        text = text.string(StringKey.OptionsUnlockRecipes),
        tooltip = buildTooltip(StringKey.OptionsUnlockRecipes),
        isActiveProp = computedProperty { options.isUnlockRecipes },
    ) {
        optionsService.toggleUnlockRecipes(OptionsService.Context(it))
    }

    registerTileButton(
        position = position + Vector3d(
            0.0,
            -(offsetY + 2*TILE_HEIGHT + 2*TILE_MARGIN + DDI_LINK_HEIGHT),
            0.0,
        ),
        width = MENU_FEATURES_WIDTH,
        height = DDI_LINK_HEIGHT,
        icon = "🚫",
        text = text.string(StringKey.DdiMenuOpen),
        tooltip = buildTooltip(StringKey.DdiMenuOpen),
        brightness = MENU_BRIGHTNESS_ALT,
    ) {
        state.menu.page = MenuPage.DDI
    }
}
