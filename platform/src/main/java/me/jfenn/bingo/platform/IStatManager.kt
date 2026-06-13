package me.jfenn.bingo.platform

interface IStatManager {
    fun list(): List<IStatHandle>
    fun getById(type: String, name: String?): IStatHandle?
}

interface IStatHandle {
    fun getForPlayer(player: IPlayerHandle): Int
    fun reset(player: IPlayerHandle)
}
