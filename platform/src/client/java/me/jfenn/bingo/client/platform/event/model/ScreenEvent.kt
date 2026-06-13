package me.jfenn.bingo.client.platform.event.model

import me.jfenn.bingo.client.platform.screen.IScreenHelper
import me.jfenn.bingo.platform.event.IEvent

class ScreenEvent(
    val type: ScreenType,
    val screen: IScreenHelper,
) {
    object AfterInit: IEvent<ScreenEvent>
}

enum class ScreenType {
    TitleScreen,
    Other,
}
