package me.jfenn.bingo.client.platform

import me.jfenn.bingo.platform.text.IText

interface IClientPlayer {
    fun sendHotbarMessage(text: IText)
    fun sendCommand(command: String)
}