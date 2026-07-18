package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.mixinhandler.PlayerListNameDecorators
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Formatting
import java.util.UUID

/** 将当前 DDI 状态投影为某个玩家应在 Tab 中看到的生命数。 */
internal object DDITabLivesProjection {
    fun resolve(
        playerId: UUID,
        roundActive: Boolean,
        inactivePlayerIds: Set<UUID>,
        playerObjectiveIds: Map<UUID, String>,
        objectiveStates: Map<String, DDIObjectiveState>,
    ): Int? {
        if (!roundActive) return null
        val objectiveId = playerObjectiveIds[playerId] ?: return null
        val objective = objectiveStates[objectiveId] ?: return null
        return if (playerId in inactivePlayerIds || objective.isEliminated) 0 else objective.hearts
    }
}

/** 在原有 Tab 玩家名后追加服务端权威的 DDI 剩余生命。 */
class DDITabLivesService(
    private val playerManager: IPlayerManager,
    private val text: TextProvider,
) {
    private var livesProvider: ((UUID) -> Int?)? = null
    private var decoratorHandle: AutoCloseable? = null

    /** 开始显示生命；整个过程不会创建、占用或修改计分板目标。 */
    fun start(provider: (UUID) -> Int?) {
        clearDecorator(refreshNames = false)
        livesProvider = provider
        decoratorHandle = PlayerListNameDecorators.register(::decoratePlayerName)
        refresh()
    }

    /** 在生命或玩家状态变化后，向所有在线玩家重发显示名。 */
    fun refresh() {
        if (decoratorHandle == null) return
        refreshPlayerNames()
    }

    /** 移除 DDI 后缀，并立即恢复原始玩家列表显示名。 */
    fun stop() {
        clearDecorator(refreshNames = true)
    }

    private fun decoratePlayerName(playerId: UUID, current: IText): IText? {
        val lives = livesProvider?.invoke(playerId) ?: return null
        val safeLives = lives.coerceAtLeast(0)
        return text.empty()
            .append(current)
            .append(" ")
            .append(
                text.literal("$safeLives♥").formatted(
                    if (safeLives > 0) Formatting.RED else Formatting.DARK_GRAY
                )
            )
    }

    private fun clearDecorator(refreshNames: Boolean) {
        livesProvider = null
        decoratorHandle?.close()
        decoratorHandle = null
        if (refreshNames) refreshPlayerNames()
    }

    private fun refreshPlayerNames() {
        playerManager.getPlayers().forEach(playerManager::updatePlayerListName)
    }
}
