package me.jfenn.bingo.platform.item

import me.jfenn.bingo.platform.text.IText

interface IWrittenBook : IItemStack {
    var title: String?
    var author: String?
    fun setPages(pages: List<IText>)
}