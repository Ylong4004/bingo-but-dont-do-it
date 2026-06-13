package me.jfenn.bingo.common.menu

import kotlinx.serialization.Serializable

@Serializable
class MenuState(
    var page: MenuPage = MenuPage.ROOT
)

@Serializable
enum class MenuPage {
    ROOT,
    FEATURES,
    GOAL,
    ITEMS,
}
