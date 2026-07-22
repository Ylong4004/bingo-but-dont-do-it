package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.event.ScopedEvents
import net.minecraft.text.Text

/**
 * Y 页面举报/投票按钮的服务端入口。
 *
 * 客户端只发送意图；这里始终转给 [DDIVoiceAccusationService] 重新校验，避免伪造
 * 玩家、槽位、票权或重复投票。
 */
class DDIVoiceAccusationPacketController(
    events: ScopedEvents,
    private val accusations: DDIVoiceAccusationService,
    packets: DDIServerPackets,
) {
    init {
        events.onPacket(packets.accusationOpen) { request ->
            val accuser = request.player.player
            val result = accusations.accuse(accuser, request.packet.accusedPlayerId, request.packet.slotIndex)
            sendFeedback(accuser, result)
            accusations.syncTo(accuser)
        }

        events.onPacket(packets.accusationVote) { request ->
            val voter = request.player.player
            val result = accusations.vote(voter, request.packet.voteId, request.packet.approve)
            sendFeedback(voter, result)
            accusations.syncTo(voter)
        }
    }

    private fun sendFeedback(player: net.minecraft.server.network.ServerPlayerEntity, result: DDIVoiceAccusationActionResult) {
        val color = if (result.success) "§a" else "§c"
        player.sendMessage(Text.literal("$color[不要做·投票] §f${result.message}"), false)
    }
}
