package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.IClientNetworking
import me.jfenn.bingo.client.platform.IOptionsAccessor
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.client.platform.event.model.ClientTickEvent
import me.jfenn.bingo.client.platform.event.model.HudRenderEvent
import me.jfenn.bingo.common.config.BingoConfig
import me.jfenn.bingo.integrations.ddi.DDIStateResetPacket
import me.jfenn.bingo.integrations.ddi.DDIAccusationSyncPacket
import me.jfenn.bingo.integrations.ddi.DDITeamSyncPacket
import me.jfenn.bingo.integrations.ddi.DDITeamTriggeredPacket
import me.jfenn.bingo.integrations.ddi.DDITriggeredPacket
import me.jfenn.bingo.integrations.ddi.DDIWordSyncPacket
import me.jfenn.bingo.integrations.ddi.DDIWordSlotPacket
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.client.gui.screen.ChatScreen

/**
 * 在建立连接前注册 DDI 数据包接收器，并把数据包映射到客户端 HUD 状态。
 */
class DDIClientController(
    clientNetworking: IClientNetworking,
    private val state: DDIHudState,
    private val accusationState: DDIAccusationClientState,
    private val renderer: DDIHudRenderer,
    private val eventBus: IEventBus,
    private val client: IClient,
    private val optionsAccessor: IOptionsAccessor,
    private val config: BingoConfig,
) {

    private val wordSyncV2 = clientNetworking.registerS2C(DDIWordSyncPacket.V2)
    private val triggeredV1 = clientNetworking.registerS2C(DDITriggeredPacket.V1)
    private val teamSyncV3 = clientNetworking.registerS2C(DDITeamSyncPacket.V3)
    private val teamTriggeredV1 = clientNetworking.registerS2C(DDITeamTriggeredPacket.V1)
    private val stateResetV1 = clientNetworking.registerS2C(DDIStateResetPacket.V1)
    private val accusationSyncV1 = clientNetworking.registerS2C(DDIAccusationSyncPacket.V1)

    init {
        eventBus.register(wordSyncV2) { clientPacket ->
            val packet = clientPacket.packet
            val slots = packet.slots.toHudSlots()
            if (packet.isSelf) {
                // 即使版本不匹配的服务端意外包含本地禁做词，也绝不能保留它。
                state.updateSelf(
                    hearts = packet.hearts,
                    maxHearts = packet.maxHearts,
                    slots = slots,
                    isEliminated = packet.isEliminated,
                )
            } else {
                state.updateOther(
                    playerId = packet.playerId,
                    playerName = packet.playerName,
                    slots = slots,
                    hearts = packet.hearts,
                    maxHearts = packet.maxHearts,
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

        eventBus.register(teamSyncV3) { clientPacket ->
            val packet = clientPacket.packet
            state.updateTeam(
                teamId = packet.teamId,
                teamName = packet.teamName,
                teamColor = packet.teamColor,
                memberNames = packet.memberNames,
                // DDIHudState 会清空本队每个槽位的词文本，使服务端和客户端两端的
                // 隐私边界都清晰可见。
                slots = packet.slots.toHudSlots(),
                hearts = packet.hearts,
                maxHearts = packet.maxHearts,
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
            accusationState.reset()
        }

        eventBus.register(accusationSyncV1) { clientPacket ->
            accusationState.update(
                votes = clientPacket.packet.votes,
                candidates = clientPacket.packet.candidates,
            )
        }

        eventBus.register(ClientServerEvent.Join) {
            state.reset()
            accusationState.reset()
        }

        eventBus.register(ClientServerEvent.Disconnect) {
            state.reset()
            accusationState.reset()
        }

        eventBus.register(ClientTickEvent.End) {
            if (!client.isPaused) {
                state.tick()
                accusationState.tick()
            }
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

    private fun List<DDIWordSlotPacket>.toHudSlots(): List<DDIHudState.WordSlotInfo> = map { slot ->
        DDIHudState.WordSlotInfo(
            index = slot.index,
            wordText = slot.wordText,
            timerSeconds = slot.timerSeconds,
            maxTimerSeconds = slot.maxTimerSeconds,
        )
    }
}
