package me.jfenn.bingo.platform.scoreboard

import net.minecraft.text.Text

sealed class ScoreChange {

    abstract val name: String
    abstract val text: Text?
    abstract val value: Int

    data class Create(
        override val name: String,
        override val text: Text,
        override val value: Int,
    ): ScoreChange()

    data class Update(
        override val name: String,
        override val text: Text,
        override val value: Int,
    ): ScoreChange()

    data class Remove(
        override val name: String,
        override val text: Text? = null,
        override val value: Int,
    ): ScoreChange()

}