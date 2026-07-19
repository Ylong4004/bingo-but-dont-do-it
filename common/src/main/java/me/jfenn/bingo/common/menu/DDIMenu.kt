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

private class DDIEventSelectorState(var page: Int = 0)

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
