package me.jfenn.bingo.client.platform

import java.util.*

interface ISessionAccessor {
    fun getPlayerUuid(): UUID?
}