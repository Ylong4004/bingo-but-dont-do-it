package me.jfenn.bingo.common.card.tag

interface ITagProvider {
    fun listTags(): Map<String, TagData>
}