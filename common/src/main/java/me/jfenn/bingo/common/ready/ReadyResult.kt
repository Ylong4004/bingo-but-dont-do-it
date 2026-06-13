package me.jfenn.bingo.common.ready

import me.jfenn.bingo.platform.text.IText

sealed interface ReadyResult {
    val message: IText

    data class ChangedTo(val ready: Boolean, override val message: IText) : ReadyResult
    data class Failed(override val message: IText) : ReadyResult
}