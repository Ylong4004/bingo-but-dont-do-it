package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.impl.draw.DrawService
import me.jfenn.bingo.client.platform.event.model.HudRenderEvent
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.common.config.CardOverlap
import me.jfenn.bingo.platform.event.IEventBus
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.util.Identifier

class HudCallbackImpl(
    private val eventBus: IEventBus,
    private val config: BingoConfig,
) {
    private fun emitRenderEvent(drawContext: DrawContext, renderTickCounter: RenderTickCounter) {
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

    init {
        HudElementRegistry.addLast(Identifier.of(MOD_ID_BINGO, "card_above")) { drawContext, renderTickCounter ->
            if (config.client.cardOverlap == CardOverlap.ABOVE) {
                emitRenderEvent(drawContext, renderTickCounter)
            }
        }

        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Identifier.of(MOD_ID_BINGO, "card_underneath")) { drawContext, renderTickCounter ->
            if (config.client.cardOverlap == CardOverlap.UNDERNEATH) {
                emitRenderEvent(drawContext, renderTickCounter)
            }
        }
    }
}