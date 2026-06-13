package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState

interface IObjectiveManager {
    /**
     * List all objective ids available
     */
    fun list(): Iterable<String>

    /**
     * List objectives that should be excluded when generating a new card
     */
    fun listExcludedIds(): Iterable<String> = emptyList()
    fun find(id: String, state: CardGeneratorState): BingoObjective?
    fun init(card: BingoCard)
    fun tick(card: BingoCard)
}
