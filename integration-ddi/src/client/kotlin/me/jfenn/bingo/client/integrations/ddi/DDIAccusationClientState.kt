package me.jfenn.bingo.client.integrations.ddi

import me.jfenn.bingo.integrations.ddi.DDIAccusationVoteView
import java.util.UUID

/** 客户端 Y 页面使用的只读举报投票状态；所有内容来自服务端投影。 */
class DDIAccusationClientState {
    private val votesById = linkedMapOf<UUID, DDIAccusationVoteView>()

    val votes: List<DDIAccusationVoteView>
        get() = votesById.values.sortedWith(
            compareBy<DDIAccusationVoteView> { it.remainingTicks }.thenBy { it.voteId.toString() },
        )

    val activeVoteCount: Int get() = votesById.size

    fun update(votes: List<DDIAccusationVoteView>) {
        votesById.clear()
        votes.forEach { vote -> votesById[vote.voteId] = vote }
    }

    fun tick() {
        votesById.entries.forEach { entry ->
            val vote = entry.value
            if (vote.remainingTicks > 0) {
                entry.setValue(vote.copy(remainingTicks = vote.remainingTicks - 1))
            }
        }
    }

    fun reset() = votesById.clear()
}
