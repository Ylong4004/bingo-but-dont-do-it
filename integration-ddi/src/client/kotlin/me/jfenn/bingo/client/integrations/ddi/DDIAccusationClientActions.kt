package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.client.platform.IClientNetworking
import me.jfenn.bingo.integrations.ddi.DDIAccusationCandidateView
import me.jfenn.bingo.integrations.ddi.DDIAccusationOpenPacket
import me.jfenn.bingo.integrations.ddi.DDIAccusationVotePacket
import java.util.UUID

/** Y 页面按钮到服务端权威裁决的唯一客户端出口。 */
class DDIAccusationClientActions(clientNetworking: IClientNetworking) {
    private val openPacket = clientNetworking.registerC2S(DDIAccusationOpenPacket.V1)
    private val votePacket = clientNetworking.registerC2S(DDIAccusationVotePacket.V1)

    fun accuse(candidate: DDIAccusationCandidateView): Boolean = openPacket.send(
        DDIAccusationOpenPacket(candidate.accusedPlayerId, candidate.slotIndex),
    )

    fun vote(voteId: UUID, approve: Boolean): Boolean = votePacket.send(DDIAccusationVotePacket(voteId, approve))
}
