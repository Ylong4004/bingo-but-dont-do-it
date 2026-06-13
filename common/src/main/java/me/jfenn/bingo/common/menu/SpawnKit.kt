package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.spawn.SpawnKitService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.text.IText
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.util.Formatting
import org.joml.Vector3d

internal const val MENU_SPAWN_KIT_WIDTH = 2.0
private const val EDIT_HEIGHT = 0.55

internal fun MenuComponent.registerSpawnKit(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    cardService: CardService = koinScope.get(),
    itemStackFactory: IItemStackFactory = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
    spawnKitService: SpawnKitService = koinScope.get(),
) {
    val options by state::options
    val offset = Vector3d()

    registerTitlePanel(
        position = position + offset.sub(0.0, 2*MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = MENU_SPAWN_KIT_WIDTH,
        title = text.string(StringKey.OptionsSpawnKit),
    )

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = MENU_SPAWN_KIT_WIDTH,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsSpawnKitPlayer),
        tooltip = buildTooltip(StringKey.OptionsSpawnKitPlayer),
        isActiveProp = computedProperty { options.isPlayerKit },
    ) {
        options.isPlayerKit = !options.isPlayerKit
        cardService.generateCard()
        optionsService.broadcastHotbarMessage(
            it,
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.OptionsSpawnKitPlayer,
                text.boolean(options.isPlayerKit)
            )
        )
    }

    fun editStacks(
        player: IPlayerHandle,
        stacks: List<IItemStack>,
        title: IText,
        callback: (List<IItemStack>) -> Unit,
    ) {
        player.player.openHandledScreen(
            SimpleNamedScreenHandlerFactory(
                { syncId, inv, _ ->
                    InventoryScreenHandler(syncId, stacks, inv).apply {
                        onClose { newStacks ->
                            callback(
                                newStacks.map { itemStackFactory.forStack(it) }
                            )
                        }
                    }
                },
                title.value
            )
        )
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + EDIT_HEIGHT, 0.0),
        width = MENU_SPAWN_KIT_WIDTH,
        height = EDIT_HEIGHT,
        icon = "\uD83C\uDF71",
        text = text.string(StringKey.OptionsSpawnKitEdit),
        tooltip = listOf(
            text.string(StringKey.OptionsSpawnKitPlayerEdit).formatted(Formatting.GREEN),
            text.string(StringKey.OptionsSpawnKitEditTooltip)
        ),
        brightness = MENU_BRIGHTNESS_ALT,
    ) { player ->
        editStacks(player, spawnKitService.getPlayerItems(), text.string(StringKey.OptionsSpawnKitPlayer)) { newStacks ->
            spawnKitService.writePlayerItems(newStacks)
            cardService.generateCard()
        }
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + MENU_LINE_HEIGHT, 0.0),
        width = MENU_SPAWN_KIT_WIDTH,
        height = MENU_LINE_HEIGHT,
        text = text.string(StringKey.OptionsSpawnKitTeam),
        tooltip = buildTooltip(StringKey.OptionsSpawnKitTeam),
        isActiveProp = computedProperty { options.isTeamKit },
    ) {
        options.isTeamKit = !options.isTeamKit
        cardService.generateCard()
        optionsService.broadcastHotbarMessage(
            it,
            text.string(
                StringKey.OptionsNotifyChanged,
                StringKey.OptionsSpawnKitTeam,
                text.boolean(options.isTeamKit)
            )
        )
    }

    registerTileButton(
        position = position + offset.sub(0.0, MENU_LINE_PADDING + EDIT_HEIGHT, 0.0),
        width = MENU_SPAWN_KIT_WIDTH,
        height = EDIT_HEIGHT,
        icon = "\uD83C\uDF71",
        text = text.string(StringKey.OptionsSpawnKitEdit),
        tooltip = listOf(
            text.string(StringKey.OptionsSpawnKitTeamEdit).formatted(Formatting.GREEN),
            text.string(StringKey.OptionsSpawnKitEditTooltip)
        ),
        brightness = MENU_BRIGHTNESS_ALT,
    ) { player ->
        editStacks(player, spawnKitService.getTeamItems(), text.string(StringKey.OptionsSpawnKitTeam)) { newStacks ->
            spawnKitService.writeTeamItems(newStacks)
            cardService.generateCard()
        }
    }
}