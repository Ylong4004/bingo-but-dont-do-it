package me.jfenn.bingo.common.options

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.menu.*
import me.jfenn.bingo.common.utils.DurationType
import me.jfenn.bingo.common.utils.copyMemberProperties
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.generated.StringKey
import java.security.MessageDigest
import java.util.*

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BingoOptions(
    var winCondition: BingoWinCondition = BingoWinCondition.Cards(1),
    var cards: List<BingoCardOptions> = listOf(BingoCardOptions()),

    var stalemateBehavior: StalemateBehavior = StalemateBehavior.END_GAME,
    var endGameWhen: EndWhen = EndWhen.FirstWin,

    var timeLimit: DurationType? = null,

    // game feature settings
    var isElytra: Boolean = false,
    var isNightVision: Boolean = false,
    var isUnlockRecipes: Boolean = true,
    var isPvpEnabled: Boolean = true,
    var isKeepInventory: Boolean = false,

    // team spawnpoint settings
    var spawnDimension: String = "minecraft:overworld",
    @JsonNames("spreadPlayersDistance")
    var spawnDistance: Int = 25,

    // score info settings
    var showRemainingTime: Boolean = true,
    var showCompletedItems: Boolean = true,
    var showCompletedLines: Boolean = true,
    var showLeadingTeam: Boolean = true,

    // bingo card settings
    var showPreviewCard: Boolean = true,
    var showHiddenTiers: Boolean = false,

    // spawn kit settings
    var isPlayerKit: Boolean = false,
    var isTeamKit: Boolean = false,

    // DDI（不要做挑战）设置
    var enableDDI: Boolean = false,
    var ddiObjectiveMode: DDIObjectiveMode = DDIObjectiveMode.INDIVIDUAL,
    var ddiMaxHearts: Int = 3,
    var ddiWordTimerSeconds: Int = 60,
    var ddiSpecialEventsEnabled: Boolean = false,
    var ddiSpecialEventIntervalSeconds: Int = 300,
    var ddiSpecialEventTypes: Set<DDISpecialEventType> = DDISpecialEventType.BALANCED,
    var ddiVoiceKeywordsEnabled: Boolean = false,
    var ddiVoiceCustomKeywords: List<String> = emptyList(),
    /** 默认全选；保存被主持人关闭的分类和单条 DDI 词条。 */
    var ddiDisabledWordCategories: Set<String> = emptySet(),
    var ddiDisabledWordIds: Set<String> = emptySet(),
) {

    fun isValid(): Boolean {
        return cards.all { it.isValid() } && hasValidDDIOptions()
    }

    fun hasValidDDIOptions(): Boolean {
        if (!enableDDI) return true
        if (ddiMaxHearts !in 1..20 || ddiWordTimerSeconds !in 10..600) return false

        if (ddiSpecialEventsEnabled && (
                ddiSpecialEventIntervalSeconds !in DDI_SPECIAL_EVENT_INTERVAL_RANGE ||
                    ddiSpecialEventTypes.isEmpty()
                )
        ) return false

        if (ddiVoiceKeywordsEnabled) {
            if (!DDIVoiceKeywordOptions.isWithinTotalBudget(ddiVoiceCustomKeywords)) return false
            if (ddiVoiceCustomKeywords.any { DDIVoiceKeywordOptions.validate(it) != it }) return false
            if (ddiVoiceCustomKeywords
                    .map(DDIVoiceKeywordOptions::recognitionKey)
                    .distinct()
                    .size != ddiVoiceCustomKeywords.size
            ) return false
        }

        return true
    }

    fun formatGameMode(card: BingoCard): List<StringKey> {
        val gameMode = buildList {
            if (card.options.isLockoutMode) add(StringKey.OptionsModeLockout)
            if (card.options.isInventoryMode) add(StringKey.OptionsModeInventory)
            if (card.options.isHiddenItemsMode) add(StringKey.OptionsModeHiddenItems)
            if (card.options.isConsumeItemsMode) add(StringKey.OptionsModeConsumeItems)
            if (winCondition is BingoWinCondition.Infinite) add(StringKey.OptionsWinConditionInfinite)
            if (winCondition is BingoWinCondition.ReplaceGoals) add(StringKey.OptionsWinConditionReplaceGoals)
            if (enableDDI) add(StringKey.OptionsModeDdi)
        }

        return gameMode.ifEmpty { listOf(StringKey.OptionsModeStandard) }
    }

    fun formatGameModeIcons(card: BingoCard): List<String> {
        val gameMode = buildList {
            if (card.options.isLockoutMode) add(ICON_LOCKOUT)
            if (card.options.isInventoryMode) add(ICON_INVENTORY)
            if (card.options.isHiddenItemsMode) add(ICON_HIDDEN)
            if (card.options.isConsumeItemsMode) add(ICON_CONSUME)
            if (winCondition is BingoWinCondition.ReplaceGoals) add("☒")
            if (enableDDI) add("🚫")
        }

        return gameMode
    }

    fun formatFeatures(): List<StringKey> {
        val features = buildList {
            if (isElytra) add(StringKey.OptionsElytra)
            if (isNightVision) add(StringKey.OptionsNightVisShort)
            if (isKeepInventory) add(StringKey.OptionsKeepInventoryShort)
            if (isPvpEnabled) add(StringKey.OptionsAllowPvpShort)
        }

        return features.ifEmpty { listOf(StringKey.OptionsFeaturesNone) }
    }

    fun formatFeaturesIcons(): List<String> {
        val features = buildList {
            if (isElytra) add(ICON_ELYTRA)
            if (isNightVision) add(ICON_NIGHTVIS)
            if (isKeepInventory) add(ICON_KEEP_INVENTORY)
            if (isPvpEnabled) add(ICON_PVP)
        }

        return features
    }

    fun copyFrom(defaultOptions: BingoOptions) {
        copyMemberProperties(
            from = defaultOptions,
            to = this,
            excluding = setOf(BingoOptions::cards),
        )
        // since options are passed by reference, these should be copied from the default config
        // to avoid mutation
        cards = defaultOptions.cards.map { it.copy() }
    }

    /**
     * Generates a unique hash reflecting any game settings that might
     * have an effect on its difficulty
     * (excluding purely visual settings, such as showCompletedLines)
     */
    fun getShaHash(): String {
        val md = MessageDigest.getInstance("SHA-512")

        sequence {
            yield(json.encodeToString(winCondition))

            for (card in cards) {
                // card difficulty
                yield(json.encodeToString(card.goal))
                yield(json.encodeToString(card.itemDistribution))
                yield(card.itemFilter.toString())
                // gamemodes
                yield(card.isLockoutMode.toString())
                yield(card.isInventoryMode.toString())
                yield(card.isHiddenItemsMode.toString())
                yield(",")
            }
            // features
            yield(isKeepInventory.toString())
            yield(isElytra.toString())
            yield((isPlayerKit || isTeamKit).toString()) // has starting items enabled
            if (enableDDI) {
                // 保持现有非 DDI 预设的哈希稳定，同时让 DDI 对局使用独立的
                // 难度/统计分类。带分隔符的单段内容还能避免连续写入摘要时
                // (1,160) 与 (11,60) 发生冲突。
                yield("ddi:${ddiObjectiveMode.name}:$ddiMaxHearts:$ddiWordTimerSeconds")
                if (ddiSpecialEventsEnabled) {
                    val eventIds = ddiSpecialEventTypes
                        .map(DDISpecialEventType::id)
                        .sorted()
                        .joinToString(",")
                    yield("ddi-events:$ddiSpecialEventIntervalSeconds:$eventIds")
                }
                if (ddiVoiceKeywordsEnabled) {
                    val keywordIds = ddiVoiceCustomKeywords
                        .map(DDIVoiceKeywordOptions::recognitionKey)
                        .sorted()
                        .joinToString(",")
                    yield("ddi-voice:$keywordIds")
                }
                if (ddiDisabledWordCategories.isNotEmpty() || ddiDisabledWordIds.isNotEmpty()) {
                    val categoryIds = ddiDisabledWordCategories.sorted().joinToString(",")
                    val wordIds = ddiDisabledWordIds.sorted().joinToString(",")
                    yield("ddi-word-filter:$categoryIds:$wordIds")
                }
            }
            // spawning
            yield(spawnDimension)
        }.forEach {
            md.update(it.toByteArray())
        }

        val digest = md.digest()
        return HexFormat.of().formatHex(digest)
    }

}

internal val DDI_SPECIAL_EVENT_INTERVAL_RANGE = 30..3600
