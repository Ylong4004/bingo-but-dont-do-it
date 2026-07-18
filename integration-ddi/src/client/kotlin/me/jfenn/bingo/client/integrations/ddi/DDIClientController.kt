package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.IClientNetworking
import me.jfenn.bingo.client.platform.IOptionsAccessor
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.client.platform.event.model.ClientTickEvent
import me.jfenn.bingo.client.platform.event.model.HudRenderEvent
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.integrations.ddi.DDIStateResetPacket
import me.jfenn.bingo.integrations.ddi.DDITeamSyncPacket
import me.jfenn.bingo.integrations.ddi.DDITeamTriggeredPacket
import me.jfenn.bingo.integrations.ddi.DDITriggeredPacket
import me.jfenn.bingo.integrations.ddi.DDIWordSyncPacket
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.client.gui.screen.ChatScreen

/**
 * 在建立连接前注册 DDI 数据包接收器，并把数据包映射到客户端 HUD 状态。
 */
class DDIClientController(
    clientNetworking: IClientNetworking,
    private val state: DDIHudState,
    private val renderer: DDIHudRenderer,
    private val eventBus: IEventBus,
    private val client: IClient,
    private val optionsAccessor: IOptionsAccessor,
    private val config: BingoConfig,
) {

    private val wordSyncV1 = clientNetworking.registerS2C(DDIWordSyncPacket.V1)
    private val triggeredV1 = clientNetworking.registerS2C(DDITriggeredPacket.V1)
    private val teamSyncV2 = clientNetworking.registerS2C(DDITeamSyncPacket.V2)
    private val teamTriggeredV1 = clientNetworking.registerS2C(DDITeamTriggeredPacket.V1)
    private val stateResetV1 = clientNetworking.registerS2C(DDIStateResetPacket.V1)

    init {
        eventBus.register(wordSyncV1) { clientPacket ->
            val packet = clientPacket.packet
            if (packet.isSelf) {
                // 即使旧版服务端意外在 V1 数据包中包含本地禁做词，也绝不能保留它。
                state.updateSelf(
                    hearts = packet.hearts,
                    maxHearts = packet.maxHearts,
                    timerSeconds = packet.timerSeconds,
                    maxTimerSeconds = packet.maxTimerSeconds,
                    isEliminated = packet.isEliminated,
                )
            } else {
                state.updateOther(
                    playerId = packet.playerId,
                    playerName = packet.playerName,
                    wordText = packet.wordText,
                    hearts = packet.hearts,
                    maxHearts = packet.maxHearts,
                    timerSeconds = packet.timerSeconds,
                    maxTimerSeconds = packet.maxTimerSeconds,
                    isEliminated = packet.isEliminated,
                )
            }
        }

        eventBus.register(triggeredV1) { clientPacket ->
            val packet = clientPacket.packet
            state.addTrigger(
                DDIHudState.TriggerNotification(
                    actorName = packet.playerName,
                    teamName = null,
                    wordText = packet.wordText,
                    remainingHearts = packet.heartsRemaining,
                    isElimination = packet.isElimination,
                    isGain = packet.isGain,
                )
            )
        }

        eventBus.register(teamSyncV2) { clientPacket ->
            val packet = clientPacket.packet
            state.updateTeam(
                teamId = packet.teamId,
                teamName = packet.teamName,
                teamColor = packet.teamColor,
                memberNames = packet.memberNames,
                // DDIHudState 会忽略本队的此字段；这里仍主动清空，使服务端和
                // 客户端两端的隐私边界都清晰可见。
                wordText = if (packet.isOwnTeam) "" else packet.wordText,
                hearts = packet.hearts,
                maxHearts = packet.maxHearts,
                timerSeconds = packet.timerSeconds,
                maxTimerSeconds = packet.maxTimerSeconds,
                isEliminated = packet.isEliminated,
                isOwnTeam = packet.isOwnTeam,
            )
        }

        eventBus.register(teamTriggeredV1) { clientPacket ->
            val packet = clientPacket.packet
            state.addTrigger(
                DDIHudState.TriggerNotification(
                    actorName = packet.actorPlayerName,
                    teamName = packet.teamName,
                    wordText = packet.wordText,
                    remainingHearts = packet.heartsRemaining,
                    isElimination = packet.isElimination,
                    isGain = packet.isGain,
                )
            )
        }

        eventBus.register(stateResetV1) {
            state.reset()
        }

        eventBus.register(ClientServerEvent.Join) {
            state.reset()
        }

        eventBus.register(ClientServerEvent.Disconnect) {
            state.reset()
        }

        eventBus.register(ClientTickEvent.End) {
            if (!client.isPaused) state.tick()
        }

        eventBus.register(HudRenderEvent) {
            if (optionsAccessor.isHudHidden() || !config.client.enableDdiHud) return@register
            val isDebugEnabled = optionsAccessor.isDebugEnabled()
            val isChatOpen = client.screen is ChatScreen
            val isPlayerListOpen = optionsAccessor.isPlayerListPressed()
            val isBingoScreen = client.screen
                ?.javaClass
                ?.packageName
                ?.startsWith("me.jfenn.bingo") == true
            if (isBingoScreen || isPlayerListOpen) return@register
            if (config.client.hideOnF3 && isDebugEnabled) return@register
            if (config.client.hideOnChat && isChatOpen) return@register
            renderer.render(it.drawService)
        }
    }
}
