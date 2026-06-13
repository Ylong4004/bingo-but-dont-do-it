package me.jfenn.bingo.client.platform

interface IOptionsAccessor {

    fun isDebugEnabled(): Boolean

    fun isPlayerListPressed(): Boolean

    fun isSneakPressed(): Boolean

    fun isHudHidden(): Boolean

}
