package me.jfenn.bingo.common.card

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.options.BingoCardOptions
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.platform.utils.UuidAsString
import java.util.*
import kotlin.random.Random

@Serializable
class BingoCard(
    val id: UuidAsString = UUID.randomUUID(),
    var nextCardId: UuidAsString? = null,
    val seed: Long = Random.nextLong(),
    val entries: List<BingoCardEntry>,
    val objectives: Map<String, BingoObjective>,
    val options: BingoCardOptions,
    var ticks: Int = 0,
) {

    init {
        if (entries.size != 25) throw IllegalArgumentException("BINGO card was created with less than 25 entries! (${entries.size})")
    }

    @Transient
    var isInitialized = false

    inline fun <reified T : BingoObjective> objectivesByInstance() = objectives.values.filterIsInstance<T>()

    @Transient
    val itemObjectives = objectivesByInstance<BingoObjective.ItemEntry>().groupBy { it.itemId }

    /**
     * Gets the card entry at the provided x/y index.
     *
     * Throws if [x] or [y] arguments are not within 0..4
     */
    fun entry(x: Int, y: Int): BingoCardEntry {
        assert(x in 0 until 5) { "x=$x is not within the required 0..4 range" }
        assert(y in 0 until 5) { "y=$y is not within the required 0..4 range" }

        return entries[x + y * 5]
    }

    /**
     * Gets the card entry at the provided x/y index.
     *
     * Throws if [x] or [y] arguments are not within 0..4
     */
    fun objective(x: Int, y: Int): Pair<BingoCardEntry, BingoObjective>? {
        val entry = entry(x, y)
        return objectives[entry.objectiveId]?.let { Pair(entry, it) }
    }

    fun lines(): Sequence<List<BingoCardEntry>> = sequence {
        for (row in 0..4) {
            yield((0..4).map { entry(row, it) })
        }

        for (col in 0..4) {
            yield((0..4).map { entry(it, col) })
        }

        yield((0..4).map { entry(it, it) })
        yield((0..4).map { entry(it, 4 - it) })
    }

    /**
     * Counts every horizontal, vertical, and diagonal line on the card
     * satisfied by the [predicate], and returns the sum.
     */
    fun countLines(predicate: (BingoObjective) -> Boolean): Int {
        return lines().count { line ->
            line.map { objectives[it.objectiveId] }
                .all { it != null && predicate(it) }
        }
    }

    /**
     * Counts every horizontal, vertical, and diagonal line on the card
     * achieved by the given [team].
     */
    fun countLines(team: BingoTeam): Int = countLines { it.hasAchieved(team.key) }

    fun countItems(predicate: (BingoObjective) -> Boolean): Int = entries
        .mapNotNull { objectives[it.objectiveId] }
        .count(predicate)

    /**
     * Counts the total number of items achieved by the given [team].
     */
    fun countItems(team: BingoTeam): Int = countItems { it.hasAchieved(team.key) }

    fun getTeamScore(team: BingoTeam): TeamScore {
        return TeamScore(
            items = countItems(team),
            lines = countLines(team),
            cards = team.countCards(),
        )
    }

    fun removePlayer(playerId: UUID) {
        objectives.values.forEach { it.removePlayer(playerId) }
    }

    fun removeTeam(team: BingoTeam) {
        objectives.values.forEach { it.removeTeam(team) }
    }

    fun copy(): BingoCard {
        // This is a very silly way to do a deep-clone
        val element = json.encodeToJsonElement(this)
        return json.decodeFromJsonElement(element)
    }

    companion object {
        private val FREE_SPACE get() = BingoObjective.FreeSpace()
        val EMPTY get() = BingoCard(
            entries = List(25) { BingoCardEntry(FREE_SPACE.id, null, null) },
            objectives = mapOf(FREE_SPACE.id to FREE_SPACE),
            options = BingoCardOptions(),
        )
    }

}