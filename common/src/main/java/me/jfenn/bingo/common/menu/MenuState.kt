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
    DDI,
    DDI_BASIC,
    DDI_SPECIAL_EVENTS,
    DDI_SPECIAL_EVENT_SELECT,
    DDI_VOICE_KEYWORDS,
    GOAL,
    ITEMS,
}
