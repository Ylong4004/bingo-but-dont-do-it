package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.IClientNetworking
import me.jfenn.bingo.client.platform.event.model.HudRenderEvent
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.integrations.ddi.DDIStateResetPacket
import me.jfenn.bingo.integrations.ddi.DDITriggeredPacket
import me.jfenn.bingo.integrations.ddi.DDIWordSyncPacket
import org.koin.core.Koin

/**
 * 客户端 DDI 控制器 — 接收服务端 DDI 包并更新 HUD 状态。
 * 类似 Bingo 已有的 BingoHudController 模式。
 */
class DDIClientController(
    clientNetworking: IClientNetworking,
    private val state: DDIHudState,
    private val renderer: DDIHudRenderer,
    private val eventBus: IEventBus,
) {

    private val wordSyncV1 = clientNetworking.registerS2C(DDIWordSyncPacket.V1)
    private val triggeredV1 = clientNetworking.registerS2C(DDITriggeredPacket.V1)
    private val stateResetV1 = clientNetworking.registerS2C(DDIStateResetPacket.V1)

    init {
        // 接收词条同步
        eventBus.register(wordSyncV1) { clientPacket ->
            val packet = clientPacket.packet
            if (packet.isSelf) {
                state.myWordText = packet.wordText
                state.myHearts = packet.hearts
                state.myMaxHearts = packet.maxHearts
                state.myTimerSeconds = packet.timerSeconds
                state.myMaxTimerSeconds = packet.maxTimerSeconds
                state.isMyEliminated = packet.isEliminated
                state.isVisible = true
            } else {
                state.otherPlayers[packet.playerId] = DDIHudState.PlayerDDIInfo(
                    playerName = packet.playerName,
                    wordText = packet.wordText,
                    hearts = packet.hearts,
                    maxHearts = packet.maxHearts,
                    timerSeconds = packet.timerSeconds,
                    isEliminated = packet.isEliminated,
                )
            }
        }

        // 接收触发通知
        eventBus.register(triggeredV1) { clientPacket ->
            val packet = clientPacket.packet
            state.recentTriggers.add(
                DDIHudState.TriggerNotification(
                    playerName = packet.playerName,
                    wordText = packet.wordText,
                    remainingHearts = packet.heartsRemaining,
                    isElimination = packet.isElimination,
                    isGain = packet.isGain,
                )
            )
            // 限制通知列表大小
            if (state.recentTriggers.size > 5) {
                state.recentTriggers.removeAt(0)
            }
        }

        // 状态重置
        eventBus.register(stateResetV1) {
            state.reset()
        }

        // 每帧渲染 DDI HUD
        eventBus.register(HudRenderEvent) {
            val drawService = it.drawService
            drawService.setShaderColor(1f, 1f, 1f, 1f)
            renderer.render(drawService)
        }
    }
}
