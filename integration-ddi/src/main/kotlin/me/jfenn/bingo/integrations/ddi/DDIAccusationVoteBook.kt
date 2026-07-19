package me.jfenn.bingo.integrations.ddi

import java.util.UUID

/** 一次已由服务端冻结身份和词条版本的违规指控。 */
data class DDIAccusationVoteRequest(
    val voteId: UUID,
    val accuserId: UUID,
    val accusedPlayerId: UUID,
    val accusedTeamId: String,
    val objectiveId: String,
    val assignmentRevision: Long,
    /** 仅包含指控创建时合资格的语音对局参与者，且不含被指控队伍。 */
    val eligibleVoterIds: Set<UUID>,
    val startedAtTick: Long,
) {
    init {
        require(accusedTeamId.isNotBlank()) { "Accused team ID cannot be blank" }
        require(objectiveId.isNotBlank()) { "Objective ID cannot be blank" }
        require(assignmentRevision >= 0L) { "Assignment revision cannot be negative" }
        require(startedAtTick >= 0L) { "Vote start tick cannot be negative" }
    }
}

data class DDIAccusationVotePolicy(
    val durationTicks: Long = DEFAULT_DURATION_TICKS,
    val minimumEligibleVoters: Int = DEFAULT_MINIMUM_ELIGIBLE_VOTERS,
) {
    init {
        require(durationTicks > 0L) { "Vote duration must be positive" }
        require(minimumEligibleVoters >= 2) { "A vote requires at least two eligible voters" }
    }

    /** `ceil(2 * voters / 3)`；2 人必须全票，3 人需 2 票，4 人需 3 票。 */
    fun approvalThreshold(eligibleVoterCount: Int): Int =
        (eligibleVoterCount * 2 + 2) / 3

    companion object {
        const val DEFAULT_DURATION_TICKS = 20L * 5L
        const val DEFAULT_MINIMUM_ELIGIBLE_VOTERS = 2
    }
}

/** 可安全同步给聊天提示、命令和未来 Dialog 的不可变投票状态。 */
data class DDIAccusationVoteSnapshot(
    val voteId: UUID,
    val accuserId: UUID,
    val accusedPlayerId: UUID,
    val accusedTeamId: String,
    val objectiveId: String,
    val assignmentRevision: Long,
    val eligibleVoterIds: Set<UUID>,
    val yesVoterIds: Set<UUID>,
    val noVoterIds: Set<UUID>,
    val startedAtTick: Long,
    val deadlineTick: Long,
    val approvalThreshold: Int,
) {
    val remainingVoterCount: Int get() = eligibleVoterIds.size - yesVoterIds.size - noVoterIds.size
}

sealed interface DDIAccusationVoteOpenResult {
    data class Opened(val vote: DDIAccusationVoteSnapshot) : DDIAccusationVoteOpenResult
    data object AccuserIneligible : DDIAccusationVoteOpenResult
    data object InsufficientEligibleVoters : DDIAccusationVoteOpenResult
    data class AlreadyActiveForAccused(val existingVoteId: UUID) : DDIAccusationVoteOpenResult
}

enum class DDIAccusationVoteCastResult {
    ACCEPTED,
    VOTE_NOT_FOUND,
    VOTER_INELIGIBLE,
    ALREADY_VOTED,
}

enum class DDIAccusationVoteOutcome {
    APPROVED,
    REJECTED_INSUFFICIENT_YES,
}

data class DDIAccusationVoteResolution(
    val vote: DDIAccusationVoteSnapshot,
    val outcome: DDIAccusationVoteOutcome,
)

/**
 * 系统指控投票的纯领域模块。
 *
 * Interface：服务端在创建时提供一次冻结后的选民快照，之后只需开票、投票、按游戏刻
 * 推进或取消。Implementation 隐藏了重复投票、每名被指控玩家的并发限制、2/3 阈值与
 * 超时收口；调用方不需要自行维护计数器或计时器。
 */
class DDIAccusationVoteBook(
    private val policy: DDIAccusationVotePolicy = DDIAccusationVotePolicy(),
) {
    private val votesById = linkedMapOf<UUID, ActiveVote>()
    private val activeVoteIdsByAccused = mutableMapOf<UUID, UUID>()

    fun open(request: DDIAccusationVoteRequest): DDIAccusationVoteOpenResult {
        if (request.accuserId !in request.eligibleVoterIds) {
            return DDIAccusationVoteOpenResult.AccuserIneligible
        }
        if (request.eligibleVoterIds.size < policy.minimumEligibleVoters) {
            return DDIAccusationVoteOpenResult.InsufficientEligibleVoters
        }
        activeVoteIdsByAccused[request.accusedPlayerId]?.let {
            return DDIAccusationVoteOpenResult.AlreadyActiveForAccused(it)
        }

        val vote = ActiveVote(
            request = request,
            deadlineTick = request.startedAtTick + policy.durationTicks,
            approvalThreshold = policy.approvalThreshold(request.eligibleVoterIds.size),
            // 发起人必须属于冻结的合资格集合，其主动指控自然计为一张同意票。
            yesVoterIds = linkedSetOf(request.accuserId),
        )
        votesById[request.voteId] = vote
        activeVoteIdsByAccused[request.accusedPlayerId] = request.voteId
        return DDIAccusationVoteOpenResult.Opened(vote.snapshot())
    }

    fun cast(voteId: UUID, voterId: UUID, approve: Boolean): DDIAccusationVoteCastResult {
        val vote = votesById[voteId] ?: return DDIAccusationVoteCastResult.VOTE_NOT_FOUND
        if (voterId !in vote.request.eligibleVoterIds) return DDIAccusationVoteCastResult.VOTER_INELIGIBLE
        if (voterId in vote.yesVoterIds || voterId in vote.noVoterIds) {
            return DDIAccusationVoteCastResult.ALREADY_VOTED
        }
        if (approve) vote.yesVoterIds += voterId else vote.noVoterIds += voterId
        return DDIAccusationVoteCastResult.ACCEPTED
    }

    fun snapshot(voteId: UUID): DDIAccusationVoteSnapshot? = votesById[voteId]?.snapshot()

    /** 仅在投票时限到达后结算；不会因为提前达到门槛而跳过其余选民的窗口。 */
    fun resolveExpired(currentTick: Long): List<DDIAccusationVoteResolution> {
        val expired = votesById.values
            .filter { currentTick >= it.deadlineTick }
            .sortedWith(compareBy<ActiveVote> { it.deadlineTick }.thenBy { it.request.voteId.toString() })
        return expired.map(::resolve)
    }

    fun cancel(voteId: UUID): DDIAccusationVoteSnapshot? {
        val vote = votesById.remove(voteId) ?: return null
        activeVoteIdsByAccused.remove(vote.request.accusedPlayerId, voteId)
        return vote.snapshot()
    }

    fun cancelAll(): List<DDIAccusationVoteSnapshot> = votesById.keys
        .toList()
        .mapNotNull(::cancel)

    private fun resolve(vote: ActiveVote): DDIAccusationVoteResolution {
        votesById.remove(vote.request.voteId)
        activeVoteIdsByAccused.remove(vote.request.accusedPlayerId, vote.request.voteId)
        val snapshot = vote.snapshot()
        val outcome = if (snapshot.yesVoterIds.size >= snapshot.approvalThreshold) {
            DDIAccusationVoteOutcome.APPROVED
        } else {
            DDIAccusationVoteOutcome.REJECTED_INSUFFICIENT_YES
        }
        return DDIAccusationVoteResolution(snapshot, outcome)
    }

    private data class ActiveVote(
        val request: DDIAccusationVoteRequest,
        val deadlineTick: Long,
        val approvalThreshold: Int,
        val yesVoterIds: MutableSet<UUID>,
        val noVoterIds: MutableSet<UUID> = linkedSetOf(),
    ) {
        fun snapshot() = DDIAccusationVoteSnapshot(
            voteId = request.voteId,
            accuserId = request.accuserId,
            accusedPlayerId = request.accusedPlayerId,
            accusedTeamId = request.accusedTeamId,
            objectiveId = request.objectiveId,
            assignmentRevision = request.assignmentRevision,
            eligibleVoterIds = request.eligibleVoterIds.toSet(),
            yesVoterIds = yesVoterIds.toSet(),
            noVoterIds = noVoterIds.toSet(),
            startedAtTick = request.startedAtTick,
            deadlineTick = deadlineTick,
            approvalThreshold = approvalThreshold,
        )
    }
}
