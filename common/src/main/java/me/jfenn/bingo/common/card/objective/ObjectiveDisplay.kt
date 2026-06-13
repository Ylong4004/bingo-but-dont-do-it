package me.jfenn.bingo.common.card.objective

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText

class ObjectiveDisplay {
    data class Resolved(
        val name: IText? = null,
        val lore: List<IText>? = null,
        val item: IItemStack? = null,
        val decoration: CardTile.Decoration = CardTile.Decoration.NONE,
        val image: String? = null,
        val mapImage: String? = null,
    ) {
        companion object {
            val EMPTY = Resolved()
        }
    }

    @Serializable
    class Data(
        val name: JsonElement? = null,
        val lore: List<JsonElement>? = null,
        val item: String? = null,
        val itemNbt: String? = null,
        val itemComponents: Map<String, JsonElement>? = null,
        val decoration: CardTile.Decoration? = null,
        val image: String? = null,
        val mapImage: String? = null,
    )

    companion object {
        const val FORMAT_MIN = "%min%"
        const val FORMAT_MAX = "%max%"
        const val FORMAT_COUNT = "%count%"
    }
}