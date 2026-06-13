package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.event.model.HudRenderEvent
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.event.IEventBus
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer
import net.minecraft.util.Identifier

class HudCallbackImpl(
    private val eventBus: IEventBus,
) {
    init {
        HudLayerRegistrationCallback.EVENT.register { layeredDrawer ->
            layeredDrawer.attachLayerAfter(
                IdentifiedLayer.SCOREBOARD,
                Identifier.of(MOD_ID_BINGO, "card"),
                { drawContext, renderTickCounter ->
                    val delta = renderTickCounter.getTickProgress(false)
                    eventBus.emit(
                        HudRenderEvent,
                        HudRenderEvent(
                            drawService = DrawService(drawContext)
                                .also { it.delta = delta },
                            delta = renderTickCounter.getTickProgress(false)
                        )
                    )
                }
            )
        }
    }
}