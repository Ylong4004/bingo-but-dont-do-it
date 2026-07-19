package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.options.DDIWordCatalog
import me.jfenn.bingo.common.options.DDIWordCatalogCategory
import me.jfenn.bingo.common.options.DDIWordCatalogEntry
import me.jfenn.bingo.common.options.DDIWordCatalogSnapshot

/** 把 DDI 的 JSON 词池投影给通用设置墙，而不让 common 反向依赖 DDI 集成。 */
class DDIWordCatalogProvider(
    private val wordPool: DDIWordPool,
    private val options: BingoOptions,
) : DDIWordCatalog {

    private var customKeywords: List<String>? = null

    override fun snapshot(): DDIWordCatalogSnapshot {
        val currentKeywords = options.ddiVoiceCustomKeywords.toList()
        if (customKeywords != currentKeywords) {
            wordPool.setCustomVoiceKeywords(currentKeywords)
            customKeywords = currentKeywords
        }

        val entries = wordPool.getAllWords().map { word ->
            DDIWordCatalogEntry(
                id = word.id,
                displayText = word.displayText,
                categoryId = word.category,
            )
        }
        val categories = entries
            .asSequence()
            .map(DDIWordCatalogEntry::categoryId)
            .distinct()
            .sortedWith(compareBy(::categoryOrder).thenBy { it })
            .map { categoryId ->
                DDIWordCatalogCategory(
                    id = categoryId,
                    translationKey = "bingo.ddi.word_category.$categoryId",
                )
            }
            .toList()

        return DDIWordCatalogSnapshot(categories = categories, entries = entries)
    }

    private fun categoryOrder(categoryId: String): Int = CATEGORY_ORDER.indexOf(categoryId)
        .takeIf { it >= 0 }
        ?: Int.MAX_VALUE

    private companion object {
        val CATEGORY_ORDER = listOf(
            DDIWordPool.LEGACY_CATEGORY,
            "movement",
            "bingo",
            "player_interaction",
            "craft",
            "pickup",
            "hold",
            "place",
            "break",
            "drop",
            "stand",
            DDIWordPool.VOICE_CATEGORY,
        )
    }
}
