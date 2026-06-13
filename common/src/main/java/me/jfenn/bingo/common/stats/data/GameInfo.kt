package me.jfenn.bingo.common.stats.data

import kotlinx.serialization.Serializable
import me.jfenn.bingo.common.utils.DurationType
import me.jfenn.bingo.common.utils.InstantType
import me.jfenn.bingo.platform.utils.UuidAsString
import me.jfenn.bingo.stats.sql.Game
import java.time.Duration
import java.time.Instant
import java.util.*

@Serializable
data class GameInfo(
    val id: UuidAsString,
    val bingoOptions: String,
    val bingoOptionsHash: String,
    val startedAt: InstantType,
    val endedAt: InstantType,
    val duration: DurationType,
    val playerCount: Int,
    /** True if the game ended due to a timeout & was not won by any team */
    val isDraw: Boolean,
    /** True if the game was ended early by "/bingo end" */
    val isForfeit: Boolean,
    /** Identifier unique to the server this was played on */
    val hostId: UuidAsString = UUID.fromString("00000000-0000-0000-0000-000000000000"),
) {
    constructor(game: Game) : this(
        id = UUID.fromString(game.id),
        bingoOptions = game.bingo_options,
        bingoOptionsHash = game.bingo_options_hash,
        startedAt = Instant.parse(game.started_at),
        endedAt = Instant.parse(game.ended_at),
        duration = Duration.ofMillis(game.duration),
        playerCount = game.player_count.toInt(),
        isDraw = game.is_draw == 1L,
        isForfeit = game.is_forfeit == 1L,
        hostId = UUID.fromString(game.host_id),
    )

    fun toGame() = Game(
        id = this.id.toString(),
        bingo_options = this.bingoOptions,
        bingo_options_hash = this.bingoOptionsHash,
        started_at = this.startedAt.toString(),
        ended_at = this.endedAt.toString(),
        duration = this.duration.toMillis(),
        player_count = this.playerCount.toLong(),
        is_draw = if (this.isDraw) 1L else 0L,
        is_forfeit = if (this.isForfeit) 1L else 0L,
        host_id = this.hostId.toString(),
    )
}