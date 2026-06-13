package me.jfenn.bingo.common.menu

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.card.CardService
import me.jfenn.bingo.common.commands.formatWarning
import me.jfenn.bingo.common.config.ConfigService
import me.jfenn.bingo.common.event.model.CardShuffledEvent
import me.jfenn.bingo.common.game.GameService
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.OptionsService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import org.joml.Vector3d
import org.koin.core.scope.Scope

private fun createRootPage(
    koinScope: Scope,
    position: Vector3d,
    state: BingoState = koinScope.get(),
    options: BingoOptions = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
    gameService: GameService = koinScope.get(),
    cardService: CardService = koinScope.get(),
    configService: ConfigService = koinScope.get(),
) = component(koinScope) {
    val pos = position + Vector3d(-5.5 + MENU_LINE_PADDING*2, 0.0, 0.0)
    registerGameModes(
        position = pos + Vector3d(GAME_MODES_WIDTH/2, 0.0, 0.0),
    )
    pos.add(GAME_MODES_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerGoal(
        position = pos + Vector3d(MENU_GOAL_WIDTH/2, 0.0, 0.0),
    )
    pos.add(MENU_GOAL_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerCardDifficulty(
        position = pos + Vector3d(CARD_DIFFICULTY_WIDTH/2, 0.0, 0.0),
    )
    pos.add(CARD_DIFFICULTY_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerTeams(
        position = pos + Vector3d(MENU_TEAMS_WIDTH/2, 0.0, 0.0),
    )
    registerCardReroll(
        position = pos + Vector3d(MENU_TEAMS_WIDTH/2, -0.5 - 2*MENU_LINE_HEIGHT - 3*MENU_LINE_PADDING - 0.55, 0.0),
        width = MENU_TEAMS_WIDTH,
        height = 0.55,
    )
    registerTileButton(
        position = pos + Vector3d(MENU_TEAMS_WIDTH/2, -0.5 - 2*MENU_LINE_HEIGHT - 3*MENU_LINE_PADDING - 1.2, 0.0),
        width = MENU_TEAMS_WIDTH,
        height = 0.55,
        icon = "⟳",
        text = text.string(StringKey.OptionsResetDefaults),
        brightness = MENU_BRIGHTNESS_ALT,
    ) {
        // Reset the menu to the default options
        options.copyFrom(configService.optionsDefault)

        // Reset the active card options to the defaults
        cardService.createInitialCards()

        optionsService.broadcastHotbarMessage(it, text.string(StringKey.OptionsResetDefaults))
    }
    pos.add(MENU_TEAMS_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    val startWidth = (position.x + 5.5 - MENU_LINE_PADDING*2) - pos.x

    registerPlayerCount(
        position = pos + Vector3d(startWidth/2, 0.0, 0.0),
        width = startWidth,
    )

    registerTileButton(
        position = pos + Vector3d(startWidth/2, -2.4 - 2*MENU_LINE_PADDING, 0.0),
        width = startWidth,
        height = 2.4 - MENU_PLAYER_COUNT_HEIGHT + 2*MENU_LINE_PADDING,
        text = text.string(StringKey.OptionsStart),
        brightness = MENU_BRIGHTNESS_ALT,
    ) { player ->
        // Start the game!
        optionsService.broadcastHotbarMessage(player, text.string(StringKey.CommandStartSuccess))
        val warnings = mutableListOf<IText>()
        gameService.start(
            warnings = warnings,
            allowSpectators = player.isSneaking,
        )
        warnings.forEachIndexed { i, warning ->
            player.sendMessage(text.formatWarning(warning, i == 0))
        }
    }
}

private const val MENU_BACK_WIDTH = 0.8

private fun MenuComponent.registerBackButton(
    position: Vector3d,
    state: BingoState = koinScope.get(),
) {
    registerTileButton(
        position = position + Vector3d(0.0, -2.4 - 2*MENU_LINE_PADDING, 0.0),
        width = MENU_BACK_WIDTH,
        height = 2.4,
        text = text.literal("⬅"),
        textScale = 3.5f,
        brightness = MENU_BRIGHTNESS_ALT,
    ) {
        state.menu.page = MenuPage.ROOT
    }
}

private fun createFeaturesPage(
    koinScope: Scope,
    position: Vector3d,
) = component(koinScope) {
    val pos = position + Vector3d(-5.5 + MENU_LINE_PADDING*2, 0.0, 0.0)
    registerBackButton(
        position = pos + Vector3d(MENU_BACK_WIDTH/2, 0.0, 0.0),
    )
    pos.add(MENU_BACK_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerFeatures(
        position = pos + Vector3d(MENU_FEATURES_WIDTH/2, 0.0, 0.0),
    )
    pos.add(MENU_FEATURES_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerSpawnKit(
        position = pos + Vector3d(MENU_SPAWN_KIT_WIDTH/2, 0.0, 0.0),
    )
    pos.add(MENU_SPAWN_KIT_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerDimensionMenu(
        position = pos + Vector3d(MENU_DIMENSION_WIDTH/2, 0.0, 0.0),
        height = 2.6,
    )
    pos.add(MENU_DIMENSION_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerSpawnDistance(
        position = pos + Vector3d(MENU_SPAWN_DISTANCE_WIDTH/2, 0.0, 0.0),
    )
    pos.add(MENU_SPAWN_DISTANCE_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)
}

private fun createGoalPage(
    koinScope: Scope,
    position: Vector3d,
) = component(koinScope) {
    val pos = position + Vector3d(-5.5 + MENU_LINE_PADDING*2, 0.0, 0.0)
    registerBackButton(
        position = pos + Vector3d(MENU_BACK_WIDTH/2, 0.0, 0.0),
    )
    pos.add(MENU_BACK_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerWinCondition(
        position = pos + Vector3d(MENU_GOAL_WIDTH/2, -MENU_LINE_PADDING, 0.0),
        width = MENU_GOAL_WIDTH,
    )
    pos.add(MENU_GOAL_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerEndGameWhen(
        position = pos + Vector3d(MENU_END_WHEN_WIDTH/2, -MENU_LINE_PADDING, 0.0),
    )
    pos.add(MENU_END_WHEN_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerStalemateBehavior(
        position = pos + Vector3d(MENU_STALEMATE_WIDTH/2, -MENU_LINE_PADDING, 0.0),
    )
    pos.add(MENU_STALEMATE_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerScoreInfo(
        position = pos + Vector3d(MENU_SCORE_INFO_WIDTH/2, -MENU_LINE_PADDING, 0.0),
    )
    pos.add(MENU_SCORE_INFO_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)
}

private fun createItemsPage(
    koinScope: Scope,
    position: Vector3d,
) = component(koinScope) {
    val pos = position + Vector3d(-5.5 + MENU_LINE_PADDING*2, 0.0, 0.0)
    registerBackButton(
        position = pos + Vector3d(MENU_BACK_WIDTH/2, 0.0, 0.0),
    )
    pos.add(MENU_BACK_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerItemFilters(
        position = pos + Vector3d(MENU_ITEM_FILTERS_WIDTH/2, 0.0, 0.0),
        height = 2.6,
    )
    pos.add(MENU_ITEM_FILTERS_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)

    registerItemDistribution(
        position = pos + Vector3d(0.0, -MENU_LINE_PADDING, 0.0),
    )
    pos.add(MENU_ITEM_DISTRIBUTION_WIDTH + MENU_LINE_PADDING, 0.0, 0.0)
}

internal fun MenuComponent.registerMenuPage(
    position: Vector3d,
    state: BingoState = koinScope.get(),
) {
    val menuState by state::menu

    val pages = MenuPage.entries.associateWith {
        when (it) {
            MenuPage.ROOT -> createRootPage(koinScope, position)
            MenuPage.FEATURES -> createFeaturesPage(koinScope, position)
            MenuPage.GOAL -> createGoalPage(koinScope, position)
            MenuPage.ITEMS -> createItemsPage(koinScope, position)
        }
    }

    onTick { instance ->
        pages[menuState.page]?.tick(instance)
    }

    onUpdate { instance ->
        for ((page, component) in pages.entries) {
            if (menuState.page != page) {
                component.despawn()
            }
        }
        pages[menuState.page]?.spawn(instance)
    }

    onDespawn {
        pages.values.forEach { it.despawn() }
    }
}