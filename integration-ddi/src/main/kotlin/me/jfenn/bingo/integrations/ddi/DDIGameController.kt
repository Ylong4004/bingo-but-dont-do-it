package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.options.BingoOptions
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.platform.packet.IServerNetworking
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

/**
 * DDI 生命周期控制器 — 将 DDI 生命周期绑定到 Bingo 的 GameState 变化。
 *
 * DDI 不自建独立状态机。Bingo 的状态变化是唯一的驱动力：
 * - COUNTDOWN → DDI 分配词条 + 开始检测 + 注册网络包
 * - POSTGAME → DDI 清理状态 + 发送重置包
 * - PREGAME  → DDI 完全重置
 */
class DDIGameController(
    private val state: BingoState,
    private val options: BingoOptions,
    private val server: MinecraftServer,
    private val ddiManager: DDIObjectiveManager,
    private val triggerDetector: DDITriggerDetector,
    private val eventBus: IEventBus,
    serverNetworking: IServerNetworking,
    private val log: Logger,
) {
    private val wordSyncHandler = serverNetworking.registerS2C(DDIWordSyncPacket.V1)
    private val triggeredHandler = serverNetworking.registerS2C(DDITriggeredPacket.V1)
    private val stateResetHandler = serverNetworking.registerS2C(DDIStateResetPacket.V1)

    init {
        // 注入网络包处理器到 DDIObjectiveManager
        ddiManager.wordSyncHandler = wordSyncHandler
        ddiManager.triggeredHandler = triggeredHandler
        ddiManager.stateResetHandler = stateResetHandler

        // 监听 Bingo 状态变化，驱动 DDI 生命周期
        eventBus.register(StateChangedEvent) { event ->
            when (event.to) {
                GameState.COUNTDOWN -> onGameStarting()
                GameState.POSTGAME -> onGameEnding()
                GameState.PREGAME -> onGameReset()
                else -> { /* no-op */ }
            }
        }
        log.info("[DDI] GameController initialized, listening for Bingo state changes")
    }

    private fun onGameStarting() {
        if (!options.enableDDI) {
            log.debug("[DDI] DDI mode disabled, skipping game start")
            return
        }
        log.info("[DDI] Bingo game starting -> DDI starting with {} players", server.playerManager.playerList.size)
        ddiManager.onGameStarting(server)
    }

    private fun onGameEnding() {
        if (!options.enableDDI) return
        log.info("[DDI] Bingo game ending -> DDI ending")
        ddiManager.sendResetToClients(server)
        ddiManager.onGameEnding()
    }

    private fun onGameReset() {
        if (!options.enableDDI) return
        log.debug("[DDI] Bingo game reset -> DDI reset")
        triggerDetector.unregister()
        ddiManager.playerStates.clear()
    }
}
