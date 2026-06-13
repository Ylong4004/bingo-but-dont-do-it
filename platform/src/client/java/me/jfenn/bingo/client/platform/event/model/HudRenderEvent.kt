package me.jfenn.bingo.client.platform.event.model

import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.platform.event.IEvent

class HudRenderEvent(
    val drawService: IDrawService,
    val delta: Float,
) {
    companion object : IEvent<HudRenderEvent>
}