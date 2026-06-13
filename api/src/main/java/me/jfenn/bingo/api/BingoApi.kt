package me.jfenn.bingo.api

import me.jfenn.bingo.api.annotations.BingoInternalApi
import java.util.*

object BingoApi {
    @JvmStatic
    private var current: IBingoApi? = null

    @BingoInternalApi
    @JvmStatic
    fun set(api: IBingoApi?) { current = api }

    @JvmStatic
    val game get() = current?.game

    @JvmStatic
    val teams get() = current?.teams

    @JvmStatic
    fun getPlayerStats(id: UUID) = current?.getPlayerStats(id)

    @JvmStatic
    val config get() = current?.config
}