package me.jfenn.bingo.common.card

import kotlin.random.Random

class CardGeneratorState(
    val random: Random,
) {
    companion object {
        val DEFAULT = CardGeneratorState(Random.Default)
    }
}
