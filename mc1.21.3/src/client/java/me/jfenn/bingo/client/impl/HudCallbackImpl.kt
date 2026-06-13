package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.event.model.HudRenderEvent
import me.jfenn.bingo.platform.event.IEventBus
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback

class HudCallbackImpl(
    private val eventBus: IEventBus,
) {
    init {
        HudRenderCallback.EVENT.register { drawContext, renderTickCounter ->
            val delta = renderTickCounter.getTickDelta(false)
            eventBus.emit(
                HudRenderEvent,
                HudRenderEvent(
                    drawService = DrawService(drawContext)
                        .also { it.delta = delta },
                    delta = delta
                )
            )
        }
    }
}