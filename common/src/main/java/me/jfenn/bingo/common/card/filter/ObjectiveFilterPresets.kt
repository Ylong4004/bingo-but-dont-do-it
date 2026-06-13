package me.jfenn.bingo.common.card.filter

import kotlinx.serialization.Serializable
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextFactory
import me.jfenn.bingo.platform.text.ITextSerialized
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.formatTitle

@Serializable
class ObjectiveFilterPresets(
    val filters: List<ObjectiveFilterPreset>
) {
    companion object {
        val EMPTY = ObjectiveFilterPresets(emptyList())
    }
}

@Serializable
class ObjectiveFilterPreset(
    val id: String,
    val name: ITextSerialized?,
    val value: ObjectiveFilterList,
) {
    fun name(text: TextProvider) = name?.copy() ?: formatName(text, id)

    companion object {
        fun formatName(textFactory: ITextFactory, name: String): IText =
            textFactory.translatable("bingo.list.$name", name.formatTitle())
    }
}
