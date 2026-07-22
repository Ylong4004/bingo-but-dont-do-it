package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.util.UUID

class DDIAccusationVoteBookTest {

    private val accuser = id(1)
    private val accused = id(2)
    private val voterThree = id(3)
    private val voterFour = id(4)
    private val voterFive = id(5)

    @Test
    fun `frozen eligible voters and initiator yes vote resolve at two thirds after timeout`() {
        val book = DDIAccusationVoteBook()
        val request = request(
            eligible = setOf(accuser, voterThree, voterFour, voterFive),
            startedAtTick = 50L,
        )

        val opened = book.open(request)
        assertThat(opened is DDIAccusationVoteOpenResult.Opened).isEqualTo(true)
        val initial = (opened as? DDIAccusationVoteOpenResult.Opened)?.vote
            ?: error("Expected vote to open")
        assertThat(initial.yesVoterIds).containsExactlyInAnyOrder(accuser)
        assertThat(initial.approvalThreshold).isEqualTo(3)
        assertThat(initial.deadlineTick).isEqualTo(250L)

        assertThat(book.cast(request.voteId, voterThree, approve = true))
            .isEqualTo(DDIAccusationVoteCastResult.ACCEPTED)
        assertThat(book.cast(request.voteId, voterFour, approve = false))
            .isEqualTo(DDIAccusationVoteCastResult.ACCEPTED)
        assertThat(book.resolveExpired(249L)).isEqualTo(emptyList())

        val resolved = book.resolveExpired(250L).single()
        assertThat(resolved.outcome).isEqualTo(DDIAccusationVoteOutcome.REJECTED_INSUFFICIENT_YES)
        assertThat(resolved.vote.yesVoterIds).containsExactlyInAnyOrder(accuser, voterThree)
        assertThat(resolved.vote.noVoterIds).containsExactlyInAnyOrder(voterFour)
        assertThat(book.snapshot(request.voteId)).isNull()
    }

    @Test
    fun `two eligible voters require both approval votes`() {
        val book = DDIAccusationVoteBook()
        val request = request(eligible = setOf(accuser, voterThree))
        book.open(request)

        assertThat(book.resolveExpired(200L).single().outcome)
            .isEqualTo(DDIAccusationVoteOutcome.REJECTED_INSUFFICIENT_YES)

        val secondRequest = request(
            voteId = id(8),
            accusedPlayerId = id(9),
            eligible = setOf(accuser, voterThree),
        )
        book.open(secondRequest)
        assertThat(book.cast(secondRequest.voteId, voterThree, approve = true))
            .isEqualTo(DDIAccusationVoteCastResult.ACCEPTED)
        assertThat(book.resolveExpired(200L).single().outcome)
            .isEqualTo(DDIAccusationVoteOutcome.APPROVED)
    }

    @Test
    fun `decisive approval and rejection resolve without waiting for timer`() {
        val book = DDIAccusationVoteBook()
        val approval = request(eligible = setOf(accuser, voterThree, voterFour))
        book.open(approval)
        book.cast(approval.voteId, voterThree, approve = true)
        assertThat(book.resolveIfDecisive(approval.voteId)?.outcome)
            .isEqualTo(DDIAccusationVoteOutcome.APPROVED)

        val rejection = request(
            voteId = id(7),
            accusedPlayerId = id(8),
            eligible = setOf(accuser, voterThree, voterFour),
        )
        book.open(rejection)
        book.cast(rejection.voteId, voterThree, approve = false)
        book.cast(rejection.voteId, voterFour, approve = false)
        assertThat(book.resolveIfDecisive(rejection.voteId)?.outcome)
            .isEqualTo(DDIAccusationVoteOutcome.REJECTED_INSUFFICIENT_YES)
    }

    @Test
    fun `ineligible or repeated votes are rejected and concurrent vote for accused is blocked`() {
        val book = DDIAccusationVoteBook()
        val request = request(eligible = setOf(accuser, voterThree, voterFour))
        book.open(request)

        assertThat(book.cast(request.voteId, accused, approve = true))
            .isEqualTo(DDIAccusationVoteCastResult.VOTER_INELIGIBLE)
        assertThat(book.cast(request.voteId, accuser, approve = false))
            .isEqualTo(DDIAccusationVoteCastResult.ALREADY_VOTED)
        assertThat(
            book.open(request.copy(voteId = id(7))) is DDIAccusationVoteOpenResult.AlreadyActiveForAccused,
        ).isEqualTo(true)
    }

    @Test
    fun `opening requires a valid accusing voter and quorum snapshot`() {
        val book = DDIAccusationVoteBook()
        assertThat(book.open(request(eligible = setOf(voterThree, voterFour))))
            .isEqualTo(DDIAccusationVoteOpenResult.AccuserIneligible)
        assertThat(book.open(request(eligible = setOf(accuser))))
            .isEqualTo(DDIAccusationVoteOpenResult.InsufficientEligibleVoters)
    }

    private fun request(
        voteId: UUID = id(6),
        accusedPlayerId: UUID = accused,
        eligible: Set<UUID>,
        startedAtTick: Long = 0L,
    ) = DDIAccusationVoteRequest(
        voteId = voteId,
        accuserId = accuser,
        accusedPlayerId = accusedPlayerId,
        accusedTeamId = "bingo_blue",
        objectiveId = "team:bingo_blue",
        slotIndex = 0,
        assignmentRevision = 4L,
        eligibleVoterIds = eligible,
        startedAtTick = startedAtTick,
    )

    private fun id(value: Long) = UUID(0L, value)
}
