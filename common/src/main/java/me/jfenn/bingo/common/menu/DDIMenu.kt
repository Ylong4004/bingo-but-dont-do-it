package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.commands.BingoPrefsCommand
import me.jfenn.bingo.common.config.PlayerSettings
import me.jfenn.bingo.common.options.*
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.utils.plus
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.dialog.IDialogAction
import me.jfenn.bingo.platform.dialog.IDialogInput
import me.jfenn.bingo.platform.dialog.IDialogManager
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.TextAction
import net.minecraft.util.Formatting
import org.joml.Vector3d
import kotlin.math.ceil

internal const val MENU_DDI_ENABLE_WIDTH = 2.0
internal const val MENU_DDI_MODE_WIDTH = 3.1
internal const val MENU_DDI_HEARTS_WIDTH = 3.0
internal const val MENU_DDI_TIMER_WIDTH = 3.3
internal const val MENU_DDI_WORD_SLOTS_WIDTH = 3.0
internal const val MENU_DDI_MULTI_HIT_WIDTH = 3.5
internal const val MENU_DDI_EVENT_ENABLE_WIDTH = 2.0
internal const val MENU_DDI_EVENT_INTERVAL_WIDTH = 2.7
internal const val MENU_DDI_EVENT_PRESET_WIDTH = 2.7
internal const val MENU_DDI_EVENT_SELECT_LINK_WIDTH = 2.0
internal const val MENU_DDI_VOICE_ENABLE_WIDTH = 2.5
internal const val MENU_DDI_VOICE_KEYWORDS_WIDTH = 3.3
internal const val MENU_DDI_VOICE_HELP_WIDTH = 3.7

private const val PANEL_BOTTOM = -2.6
private const val PANEL_HEIGHT = 1.9
private const val NUMBER_INPUT_Y = -1.25
private const val NUMBER_INPUT_HEIGHT = 0.5
private const val PRESET_Y = -2.05
private const val PRESET_HEIGHT = 0.55
private const val PRESET_MARGIN = 0.1
private const val EVENT_SELECTOR_PAGE_SIZE = 10
private const val EVENT_SELECTOR_ROWS = 5
private const val WORD_CATALOG_PAGE_SIZE = 10
private const val WORD_CATALOG_ROWS = 5

private class DDIEventSelectorState(var page: Int = 0)
internal class DDIWordCatalogMenuState(
    var categoryPage: Int = 0,
    var wordPage: Int = 0,
    var selectedCategoryId: String? = null,
)

private fun <T> readOnlyMutableProperty(getter: () -> T): MutableProperty<T> =
    DelegatedProperty(getter = getter, setter = {})

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

    registerTileButton(
        position = position + Vector3d(0.0, PANEL_BOTTOM, 0.0),
        width = MENU_DDI_ENABLE_WIDTH,
        height = PANEL_HEIGHT,
        text = text.string(StringKey.DdiMenuEnabled),
        isActiveProp = computedProperty { options.enableDDI },
        tooltip = buildTooltip(StringKey.DdiOptionEnable),
    ) { player ->
        optionsService.setDDIEnabled(OptionsService.Context(player), !options.enableDDI)
    }
}

internal fun MenuComponent.registerDDISectionLink(
    position: Vector3d,
    width: Double,
    title: StringKey,
    targetPage: MenuPage,
    icon: String,
    state: BingoState = koinScope.get(),
) {
    registerTileButton(
        position = position + Vector3d(0.0, -2.6, 0.0),
        width = width,
        height = 2.4,
        icon = icon,
        text = text.string(title),
        tooltip = buildTooltip(title),
        brightness = MENU_BRIGHTNESS_ALT,
        affectsOptions = false,
    ) {
        state.menu.page = targetPage
    }
}

internal fun MenuComponent.registerDDIMode(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options
    val modes = DDIObjectiveMode.entries
    val selectedMode = readOnlyMutableProperty { modes.indexOf(options.ddiObjectiveMode) }

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
        valueProp = readOnlyMutableProperty { options.ddiMaxHearts },
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
        valueProp = readOnlyMutableProperty { options.ddiWordTimerSeconds },
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

internal fun MenuComponent.registerDDISpecialEventEnable(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_EVENT_ENABLE_WIDTH,
        title = text.string(StringKey.DdiMenuSpecialEvents),
    )
    registerTileButton(
        position = position + Vector3d(0.0, PANEL_BOTTOM, 0.0),
        width = MENU_DDI_EVENT_ENABLE_WIDTH,
        height = PANEL_HEIGHT,
        text = text.string(StringKey.DdiMenuEnabled),
        isActiveProp = computedProperty { options.ddiSpecialEventsEnabled },
        tooltip = buildTooltip(StringKey.DdiOptionSpecialEvents),
    ) { player ->
        optionsService.setDDISpecialEventsEnabled(
            OptionsService.Context(player),
            !options.ddiSpecialEventsEnabled,
        )
    }
}

internal fun MenuComponent.registerDDISpecialEventInterval(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_EVENT_INTERVAL_WIDTH,
        title = text.string(StringKey.DdiMenuSpecialEventInterval),
    )
    registerNumberInput(
        position = position + Vector3d(0.0, NUMBER_INPUT_Y, 0.0),
        width = MENU_DDI_EVENT_INTERVAL_WIDTH,
        height = NUMBER_INPUT_HEIGHT,
        valueProp = readOnlyMutableProperty { options.ddiSpecialEventIntervalSeconds },
        minValueProp = ConstantProperty(DDI_SPECIAL_EVENT_INTERVAL_RANGE.first),
        maxValueProp = ConstantProperty(DDI_SPECIAL_EVENT_INTERVAL_RANGE.last),
        step = 30,
        format = { text.string(StringKey.DdiOptionSpecialEventIntervalValue, it) },
        tooltip = buildTooltip(StringKey.DdiOptionSpecialEventInterval),
    ) { player, seconds ->
        optionsService.setDDISpecialEventIntervalSeconds(OptionsService.Context(player), seconds)
    }
    registerDDIPresets(
        position = position + Vector3d(0.0, PRESET_Y, 0.0),
        width = MENU_DDI_EVENT_INTERVAL_WIDTH,
        values = listOf(60, 180, 300, 420),
        selected = { options.ddiSpecialEventIntervalSeconds },
        format = { text.string(StringKey.DdiMenuSpecialEventIntervalShort, it) },
    ) { player, seconds ->
        optionsService.setDDISpecialEventIntervalSeconds(OptionsService.Context(player), seconds)
    }
}

internal fun MenuComponent.registerDDISpecialEventPresets(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options
    val presets = DDISpecialEventPreset.entries
    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_EVENT_PRESET_WIDTH,
        title = text.string(StringKey.DdiMenuSpecialEventPreset),
    )
    presets.forEachIndexed { index, preset ->
        val key = "bingo.ddi.special_event.preset.${preset.name.lowercase()}"
        registerTileButton(
            position = position + Vector3d(0.0, -1.0 - index * 0.48, 0.0),
            width = MENU_DDI_EVENT_PRESET_WIDTH,
            height = 0.4,
            text = text.empty(),
            textProp = computedProperty { text.translatable(key, null) },
            tooltipProp = computedProperty {
                listOf(
                    text.translatable(key, null).formatted(Formatting.GREEN),
                    text.translatable("$key.tooltip", null),
                )
            },
            isActiveProp = computedProperty { options.ddiSpecialEventTypes == preset.eventTypes },
        ) { player ->
            optionsService.setDDISpecialEventPreset(OptionsService.Context(player), preset)
        }
    }
}

internal fun MenuComponent.registerDDISpecialEventSelectorLink(
    position: Vector3d,
    state: BingoState = koinScope.get(),
) {
    val options = state.options
    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_EVENT_SELECT_LINK_WIDTH,
        title = text.string(StringKey.DdiMenuSpecialEventCustom),
    )
    registerTileButton(
        position = position + Vector3d(0.0, PANEL_BOTTOM, 0.0),
        width = MENU_DDI_EVENT_SELECT_LINK_WIDTH,
        height = PANEL_HEIGHT,
        text = text.empty(),
        textProp = computedProperty {
            text.string(
                StringKey.DdiMenuSpecialEventSelectedCount,
                options.ddiSpecialEventTypes.size,
                DDISpecialEventType.entries.size,
            )
        },
        tooltip = buildTooltip(StringKey.DdiMenuSpecialEventCustom),
        brightness = MENU_BRIGHTNESS_ALT,
        affectsOptions = false,
    ) {
        state.menu.page = MenuPage.DDI_SPECIAL_EVENT_SELECT
    }
}

internal fun MenuComponent.registerDDISpecialEventSelector(
    position: Vector3d,
    width: Double,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options
    val eventTypes = DDISpecialEventType.entries
    val pageCount = ceil(eventTypes.size / EVENT_SELECTOR_PAGE_SIZE.toDouble()).toInt()
    val selectorState = DDIEventSelectorState()

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = width,
        titleProp = computedProperty {
            text.string(
                StringKey.DdiMenuSpecialEventSelectionTitle,
                selectorState.page + 1,
                pageCount,
                options.ddiSpecialEventTypes.size,
                eventTypes.size,
            )
        },
    )

    registerTileButton(
        position = position + Vector3d(-width / 2 + 0.25, -0.5, 0.0),
        width = 0.5,
        height = MENU_LINE_HEIGHT,
        text = text.literal("<"),
        isActiveProp = computedProperty { selectorState.page > 0 },
        affectsOptions = false,
    ) {
        selectorState.page = (selectorState.page - 1).coerceAtLeast(0)
        markDirty()
    }
    registerTileButton(
        position = position + Vector3d(width / 2 - 0.25, -0.5, 0.0),
        width = 0.5,
        height = MENU_LINE_HEIGHT,
        text = text.literal(">"),
        isActiveProp = computedProperty { selectorState.page < pageCount - 1 },
        affectsOptions = false,
    ) {
        selectorState.page = (selectorState.page + 1).coerceAtMost(pageCount - 1)
        markDirty()
    }

    val columnGap = 0.1
    val itemWidth = (width - columnGap) / 2
    repeat(EVENT_SELECTOR_PAGE_SIZE) { slot ->
        val itemIndex by computedProperty {
            selectorState.page * EVENT_SELECTOR_PAGE_SIZE + slot
        }
        val column = slot / EVENT_SELECTOR_ROWS
        val row = slot % EVENT_SELECTOR_ROWS
        val x = if (column == 0) {
            -itemWidth / 2 - columnGap / 2
        } else {
            itemWidth / 2 + columnGap / 2
        }
        val y = -0.95 - row * 0.4

        registerTileButton(
            position = position + Vector3d(x, y, 0.0),
            width = itemWidth,
            height = 0.32,
            text = text.empty(),
            textProp = computedProperty {
                eventTypes.getOrNull(itemIndex)
                    ?.let(::specialEventText)
                    ?: text.empty()
            },
            tooltipProp = computedProperty {
                eventTypes.getOrNull(itemIndex)?.let(::specialEventTooltip)
            },
            isActiveProp = computedProperty {
                eventTypes.getOrNull(itemIndex) in options.ddiSpecialEventTypes
            },
        ) { player ->
            eventTypes.getOrNull(itemIndex)?.let { eventType ->
                optionsService.setDDISpecialEventEnabled(
                    OptionsService.Context(player),
                    eventType,
                    eventType !in options.ddiSpecialEventTypes,
                )
            }
        }
    }
}

internal fun MenuComponent.registerDDIWordSlots(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options
    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_WORD_SLOTS_WIDTH,
        title = text.literal("同时词条数"),
    )
    registerNumberInput(
        position = position + Vector3d(0.0, NUMBER_INPUT_Y, 0.0),
        width = MENU_DDI_WORD_SLOTS_WIDTH,
        height = NUMBER_INPUT_HEIGHT,
        valueProp = readOnlyMutableProperty { options.ddiWordsPerObjective },
        minValueProp = ConstantProperty(DDIRoundSettings.MIN_WORD_SLOTS),
        maxValueProp = ConstantProperty(DDIRoundSettings.MAX_WORD_SLOTS),
        format = { text.literal("$it 条") },
        tooltip = listOf(text.literal("每个玩家或共享队伍同时持有的禁做词数量。")),
    ) { player, count ->
        optionsService.setDDIWordsPerObjective(OptionsService.Context(player), count)
    }
    registerDDIPresets(
        position = position + Vector3d(0.0, PRESET_Y, 0.0),
        width = MENU_DDI_WORD_SLOTS_WIDTH,
        values = listOf(1, 2, 3, 5),
        selected = { options.ddiWordsPerObjective },
        format = { text.literal("$it") },
    ) { player, count ->
        optionsService.setDDIWordsPerObjective(OptionsService.Context(player), count)
    }
}

internal fun MenuComponent.registerDDIMultiHitPolicy(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options
    val policies = DDIMultiHitPolicy.entries
    registerRadioMenu(
        position = position,
        width = MENU_DDI_MULTI_HIT_WIDTH,
        height = 2.4,
        title = text.literal("同次命中"),
        options = listOf(text.literal("全部扣血"), text.literal("仅第一条")),
        tooltips = listOf(
            listOf(text.literal("一次动作命中几条词，就扣几颗心并分别换词。")),
            listOf(text.literal("一次动作只结算槽位顺序最靠前的一条词。")),
        ),
        optionsPerPage = 2,
        selectedIndexProp = readOnlyMutableProperty { policies.indexOf(options.ddiMultiHitPolicy) },
    ) { player, index ->
        optionsService.setDDIMultiHitPolicy(OptionsService.Context(player), policies[index])
    }
}

private fun MenuComponent.ddiWordCatalogSnapshot(): DDIWordCatalogSnapshot? =
    runCatching { koinScope.get<DDIWordCatalog>().snapshot() }.getOrNull()

private fun MenuComponent.ddiWordCategoryText(category: DDIWordCatalogCategory): IText =
    text.translatable(category.translationKey, null)

internal fun MenuComponent.registerDDIWordCategorySelector(
    position: Vector3d,
    width: Double,
    selectorState: DDIWordCatalogMenuState,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options
    fun catalog() = ddiWordCatalogSnapshot()
    fun categories() = catalog()?.categories.orEmpty()
    fun pageCount() = ceil(categories().size / WORD_CATALOG_PAGE_SIZE.toDouble()).toInt().coerceAtLeast(1)
    fun categoryStats(categoryId: String): Pair<Int, Int> {
        val entries = catalog()?.entries.orEmpty().filter { it.categoryId == categoryId }
        val enabled = entries.count { it.id !in options.ddiDisabledWordIds }
        return enabled to entries.size
    }

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = width,
        titleProp = computedProperty {
            val availableCategories = categories()
            if (availableCategories.isEmpty()) {
                text.literal("DDI 词条目录不可用")
            } else {
                text.string(
                    StringKey.DdiMenuWordCategorySelectionTitle,
                    selectorState.categoryPage + 1,
                    pageCount(),
                    availableCategories.count { it.id !in options.ddiDisabledWordCategories },
                    availableCategories.size,
                )
            }
        },
    )

    registerTileButton(
        position = position + Vector3d(-width / 2 + 0.25, -0.5, 0.0),
        width = 0.5,
        height = MENU_LINE_HEIGHT,
        text = text.literal("<"),
        isActiveProp = computedProperty { selectorState.categoryPage > 0 },
        affectsOptions = false,
    ) {
        selectorState.categoryPage = (selectorState.categoryPage - 1).coerceAtLeast(0)
        markDirty()
    }
    registerTileButton(
        position = position + Vector3d(width / 2 - 0.25, -0.5, 0.0),
        width = 0.5,
        height = MENU_LINE_HEIGHT,
        text = text.literal(">"),
        isActiveProp = computedProperty { selectorState.categoryPage < pageCount() - 1 },
        affectsOptions = false,
    ) {
        selectorState.categoryPage = (selectorState.categoryPage + 1).coerceAtMost(pageCount() - 1)
        markDirty()
    }

    val columnGap = 0.1
    val itemWidth = (width - columnGap) / 2
    val viewWidth = 0.85
    val categoryWidth = itemWidth - viewWidth - columnGap
    repeat(WORD_CATALOG_PAGE_SIZE) { slot ->
        val itemIndex by computedProperty {
            selectorState.categoryPage * WORD_CATALOG_PAGE_SIZE + slot
        }
        val column = slot / WORD_CATALOG_ROWS
        val row = slot % WORD_CATALOG_ROWS
        val centerX = if (column == 0) {
            -itemWidth / 2 - columnGap / 2
        } else {
            itemWidth / 2 + columnGap / 2
        }
        val y = -0.95 - row * 0.4

        registerTileButton(
            position = position + Vector3d(
                centerX - (viewWidth + columnGap) / 2,
                y,
                0.0,
            ),
            width = categoryWidth,
            height = 0.32,
            text = text.empty(),
            textProp = computedProperty {
                categories().getOrNull(itemIndex)
                    ?.let(::ddiWordCategoryText)
                    ?: text.empty()
            },
            tooltipProp = computedProperty {
                categories().getOrNull(itemIndex)?.let { category ->
                    val (enabled, total) = categoryStats(category.id)
                    listOf(text.literal("已单独启用 $enabled/$total 条"))
                }
            },
            isActiveProp = computedProperty {
                categories().getOrNull(itemIndex)?.let { category ->
                    category.id !in options.ddiDisabledWordCategories
                } ?: false
            },
        ) { player ->
            categories().getOrNull(itemIndex)?.let { category ->
                optionsService.setDDIWordCategoryEnabled(
                    ctx = OptionsService.Context(player),
                    categoryId = category.id,
                    enabled = category.id in options.ddiDisabledWordCategories,
                )
            }
        }
        registerTileButton(
            position = position + Vector3d(
                centerX + (categoryWidth + columnGap) / 2,
                y,
                0.0,
            ),
            width = viewWidth,
            height = 0.32,
            text = text.string(StringKey.DdiMenuWordCategoryView),
            isActiveProp = computedProperty { categories().getOrNull(itemIndex) != null },
            affectsOptions = false,
        ) {
            categories().getOrNull(itemIndex)?.let { category ->
                selectorState.selectedCategoryId = category.id
                selectorState.wordPage = 0
                state.menu.page = MenuPage.DDI_WORD_ENTRIES
            }
        }
    }
}

internal fun MenuComponent.registerDDIWordEntrySelector(
    position: Vector3d,
    width: Double,
    selectorState: DDIWordCatalogMenuState,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
) {
    val options = state.options
    fun catalog() = ddiWordCatalogSnapshot()
    fun selectedCategory(): DDIWordCatalogCategory? {
        val categories = catalog()?.categories.orEmpty()
        return categories.firstOrNull { it.id == selectorState.selectedCategoryId }
            ?: categories.firstOrNull()
    }
    fun words(): List<DDIWordCatalogEntry> {
        val category = selectedCategory() ?: return emptyList()
        return catalog()?.entries
            .orEmpty()
            .filter { it.categoryId == category.id }
            .sortedWith(compareBy(DDIWordCatalogEntry::displayText).thenBy(DDIWordCatalogEntry::id))
    }
    fun pageCount() = ceil(words().size / WORD_CATALOG_PAGE_SIZE.toDouble()).toInt().coerceAtLeast(1)

    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = width,
        titleProp = computedProperty {
            val category = selectedCategory()
            if (category == null) {
                text.literal("DDI 词条目录不可用")
            } else {
                val categoryWords = words()
                val enabled = categoryWords.count { it.id !in options.ddiDisabledWordIds }
                text.string(
                    StringKey.DdiMenuWordEntrySelectionTitle,
                    ddiWordCategoryText(category),
                    selectorState.wordPage + 1,
                    pageCount(),
                    enabled,
                    categoryWords.size,
                )
            }
        },
    )

    registerTileButton(
        position = position + Vector3d(-width / 2 + 0.25, -0.5, 0.0),
        width = 0.5,
        height = MENU_LINE_HEIGHT,
        text = text.literal("<"),
        isActiveProp = computedProperty { selectorState.wordPage > 0 },
        affectsOptions = false,
    ) {
        selectorState.wordPage = (selectorState.wordPage - 1).coerceAtLeast(0)
        markDirty()
    }
    registerTileButton(
        position = position + Vector3d(width / 2 - 0.25, -0.5, 0.0),
        width = 0.5,
        height = MENU_LINE_HEIGHT,
        text = text.literal(">"),
        isActiveProp = computedProperty { selectorState.wordPage < pageCount() - 1 },
        affectsOptions = false,
    ) {
        selectorState.wordPage = (selectorState.wordPage + 1).coerceAtMost(pageCount() - 1)
        markDirty()
    }

    val columnGap = 0.1
    val itemWidth = (width - columnGap) / 2
    repeat(WORD_CATALOG_PAGE_SIZE) { slot ->
        val itemIndex by computedProperty {
            selectorState.wordPage * WORD_CATALOG_PAGE_SIZE + slot
        }
        val column = slot / WORD_CATALOG_ROWS
        val row = slot % WORD_CATALOG_ROWS
        val x = if (column == 0) {
            -itemWidth / 2 - columnGap / 2
        } else {
            itemWidth / 2 + columnGap / 2
        }
        val y = -0.95 - row * 0.4

        registerTileButton(
            position = position + Vector3d(x, y, 0.0),
            width = itemWidth,
            height = 0.32,
            text = text.empty(),
            textProp = computedProperty {
                words().getOrNull(itemIndex)?.let { entry ->
                    text.literal(entry.displayText)
                } ?: text.empty()
            },
            tooltipProp = computedProperty {
                words().getOrNull(itemIndex)?.let { entry ->
                    buildList {
                        add(text.literal(entry.id))
                        if (selectedCategory()?.id in options.ddiDisabledWordCategories) {
                            add(text.literal("该分类当前已停用"))
                        }
                    }
                }
            },
            isActiveProp = computedProperty {
                words().getOrNull(itemIndex)?.let { entry ->
                    entry.id !in options.ddiDisabledWordIds
                } ?: false
            },
        ) { player ->
            words().getOrNull(itemIndex)?.let { entry ->
                optionsService.setDDIWordEnabled(
                    ctx = OptionsService.Context(player),
                    wordId = entry.id,
                    enabled = entry.id in options.ddiDisabledWordIds,
                )
            }
        }
    }
}

internal fun MenuComponent.registerDDIVoiceEnable(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
    dialogManager: IDialogManager = koinScope.get(),
) {
    val options = state.options
    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_VOICE_ENABLE_WIDTH,
        title = text.string(StringKey.DdiMenuVoiceKeywords),
    )
    registerTileButton(
        position = position + Vector3d(0.0, -1.35, 0.0),
        width = MENU_DDI_VOICE_ENABLE_WIDTH,
        height = 0.75,
        text = text.string(StringKey.DdiMenuEnabled),
        isActiveProp = computedProperty { options.ddiVoiceKeywordsEnabled },
        tooltip = buildTooltip(StringKey.DdiOptionVoiceKeywords),
    ) { player ->
        optionsService.setDDIVoiceKeywordsEnabled(
            OptionsService.Context(player),
            !options.ddiVoiceKeywordsEnabled,
        )
    }
    registerTileButton(
        position = position + Vector3d(0.0, -2.3, 0.0),
        width = MENU_DDI_VOICE_ENABLE_WIDTH,
        height = 0.75,
        text = text.literal("准备服务器模型"),
        tooltip = buildTooltip(StringKey.DdiOptionVoiceKeywords),
        affectsOptions = false,
    ) { player ->
        val command = "/bingo ddi voice model download"
        val builder = dialogManager.confirmationBuilder()
        if (builder == null) {
            player.sendMessage(
                text.literal("§e[不要做·语音] §f模型只会下载到房主/服务器。点击 §a[准备模型]§f 开始。")
                    .apply { setClickEvent(TextAction.RunCommand(command)) }
            )
            return@registerTileButton
        }
        builder.title = text.literal("准备服务器语音模型")
        builder.addText(
            text.literal(
                "将由房主/服务器下载约 43.9 MiB 的离线中文模型；其他玩家不会下载模型。" +
                    "下载、校验、解压和加载状态可在游戏内查看。"
            )
        )
        builder.setYes(text.literal("下载并准备"), IDialogAction.RunCommand(command))
        builder.setNo(text.literal("暂不"), IDialogAction.None)
        dialogManager.showDialog(player, builder.build())
    }
}

internal fun MenuComponent.registerDDIVoiceKeywordEditor(
    position: Vector3d,
    state: BingoState = koinScope.get(),
    optionsService: OptionsService = koinScope.get(),
    dialogManager: IDialogManager = koinScope.get(),
) {
    val options = state.options
    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_VOICE_KEYWORDS_WIDTH,
        title = text.string(StringKey.DdiMenuVoiceCustomKeywords),
    )
    registerTileButton(
        position = position + Vector3d(0.0, -1.45, 0.0),
        width = MENU_DDI_VOICE_KEYWORDS_WIDTH,
        height = 0.8,
        text = text.empty(),
        textProp = computedProperty {
            text.string(
                StringKey.DdiMenuVoiceKeywordAddWithCount,
                options.ddiVoiceCustomKeywords.size,
            )
        },
        tooltip = buildTooltip(StringKey.DdiMenuVoiceKeywordAdd),
        affectsOptions = false,
    ) { player ->
        val builder = dialogManager.noticeBuilder()
        if (builder == null) {
            val command = "/bingo options ddi voice keyword add "
            player.sendMessage(
                text.string(StringKey.DdiMenuVoiceDialogUnavailable, command).apply {
                    setClickEvent(TextAction.SuggestCommand(command))
                }
            )
            return@registerTileButton
        }
        builder.title = text.string(StringKey.DdiMenuVoiceKeywordDialogTitle)
        builder.addText(text.string(StringKey.DdiMenuVoiceKeywordDialogDescription))
        builder.addInput(
            IDialogInput.Text(
                key = "keyword",
                label = text.string(StringKey.DdiMenuVoiceKeywordDialogLabel),
                maxLength = DDIVoiceKeywordOptions.MAX_KEYWORD_CODE_POINTS,
            )
        )
        builder.setAction(
            text.string(StringKey.DdiMenuVoiceKeywordAddAction),
            IDialogAction.DynamicRunCommand(
                "bingo options ddi voice keyword add \$(keyword)"
            ),
        )
        dialogManager.showDialog(player, builder.build())
    }
    registerTileButton(
        position = position + Vector3d(0.0, -2.35, 0.0),
        width = MENU_DDI_VOICE_KEYWORDS_WIDTH,
        height = 0.7,
        text = text.string(StringKey.DdiMenuVoiceKeywordReset),
        tooltip = buildTooltip(StringKey.DdiMenuVoiceKeywordReset),
    ) { player ->
        optionsService.resetDDIVoiceKeywords(OptionsService.Context(player))
    }
}

internal fun MenuComponent.registerDDIVoiceConsentHelp(
    position: Vector3d,
    dialogManager: IDialogManager = koinScope.get(),
) {
    registerTitlePanel(
        position = position + Vector3d(0.0, -0.5, 0.0),
        width = MENU_DDI_VOICE_HELP_WIDTH,
        title = text.string(StringKey.DdiMenuVoiceConsent),
    )
    registerTileButton(
        position = position + Vector3d(0.0, PANEL_BOTTOM, 0.0),
        width = MENU_DDI_VOICE_HELP_WIDTH,
        height = PANEL_HEIGHT,
        text = text.string(StringKey.DdiMenuVoiceConsentHelp),
        tooltip = buildTooltip(StringKey.DdiMenuVoiceConsent),
        brightness = MENU_BRIGHTNESS_ALT,
        permissionGetter = { true },
        affectsOptions = false,
    ) { player ->
        val command = BingoPrefsCommand.getCommand(PlayerSettings::ddiVoiceConsent, true)
        val builder = dialogManager.confirmationBuilder()
        if (builder == null) {
            player.sendMessage(
                text.string(StringKey.DdiMenuVoiceConsentMessage, command).apply {
                    setClickEvent(TextAction.RunCommand(command))
                }
            )
            return@registerTileButton
        }
        builder.title = text.string(StringKey.DdiMenuVoiceConsent)
        builder.addText(
            text.literal(
                "同意后，服务器仅在本局语音词条期间处理你的语音包进行离线识别。" +
                    "不会上传或保存音频、转写文本；可随时在个人设置撤回。"
            )
        )
        builder.setYes(text.literal("同意并参加"), IDialogAction.RunCommand(command))
        builder.setNo(text.literal("暂不参加"), IDialogAction.None)
        dialogManager.showDialog(player, builder.build())
    }
}

private fun MenuComponent.specialEventText(eventType: DDISpecialEventType): IText =
    text.translatable("bingo.ddi.special_event.${eventType.id}", null)

private fun MenuComponent.specialEventTooltip(eventType: DDISpecialEventType): List<IText> {
    val key = "bingo.ddi.special_event.${eventType.id}"
    return listOf(
        text.translatable(key, null).formatted(Formatting.GREEN),
        text.translatable("$key.tooltip", null),
    )
}

private fun MenuComponent.registerDDIPresets(
    position: Vector3d,
    width: Double,
    values: List<Int>,
    selected: () -> Int,
    format: (Int) -> IText,
    onSelect: (IPlayerHandle, Int) -> Unit,
) {
    val buttonWidth = (width - PRESET_MARGIN * (values.size - 1)) / values.size
    val left = -width / 2 + buttonWidth / 2
    values.forEachIndexed { index, value ->
        registerTileButton(
            position = position + Vector3d(
                left + index * (buttonWidth + PRESET_MARGIN),
                0.0,
                0.0,
            ),
            width = buttonWidth,
            height = PRESET_HEIGHT,
            text = text.empty(),
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
