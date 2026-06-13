package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.text.IText
import net.minecraft.text.Text

interface IBossBarManager {
    fun get(id: String): IBossBar?
    fun remove(bossBar: IBossBar)
    fun add(id: String, title: Text): IBossBar
    fun list(): List<IBossBar>
}

interface IBossBar {
    enum class Color {
        WHITE
    }
    enum class Style {
        PROGRESS
    }

    val id: String?
    var name: IText
    var color: Color
    var style: Style
    var value: Int
    var maxValue: Int

    fun addPlayer(player: IPlayerHandle)
    fun removePlayer(player: IPlayerHandle)
    fun clearPlayers()
}