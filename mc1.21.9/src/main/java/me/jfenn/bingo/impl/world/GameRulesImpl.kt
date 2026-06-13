package me.jfenn.bingo.impl.world

import me.jfenn.bingo.platform.world.IGameRule
import me.jfenn.bingo.platform.world.IGameRules
import net.minecraft.server.MinecraftServer
import net.minecraft.world.GameRules

class GameRulesImpl(
    private val server: MinecraftServer,
) : IGameRules {

    override val announceAdvancements = BooleanRule(GameRules.ANNOUNCE_ADVANCEMENTS)
    override val showDeathMessages = BooleanRule(GameRules.SHOW_DEATH_MESSAGES)
    override val keepInventory = BooleanRule(GameRules.KEEP_INVENTORY)
    override val pvp = BooleanRule(GameRules.PVP)

    private inner class Visitor(val name: String) : GameRules.Visitor {
        var ret: IGameRule<*>? = null

        override fun visitBoolean(
            key: GameRules.Key<GameRules.BooleanRule>?,
            type: GameRules.Type<GameRules.BooleanRule>?
        ) {
            if (key?.name == name) {
                ret = BooleanRule(key)
            }
        }

        override fun visitInt(
            key: GameRules.Key<GameRules.IntRule>?,
            type: GameRules.Type<GameRules.IntRule>?
        ) {
            if (key?.name == name) {
                ret = IntRule(key)
            }
        }
    }

    override fun get(name: String): IGameRule<*>? {
        val visitor = Visitor(name)
        server.gameRules.accept(visitor)
        return visitor.ret
    }

    inner class BooleanRule(
        private val key: GameRules.Key<GameRules.BooleanRule>,
    ) : IGameRule<Boolean> {
        override val name: String get() = key.name
        override var value: Boolean
            get() = server.gameRules.get(key).get()
            set(value) {
                server.gameRules.get(key).set(value, server)
            }
    }

    inner class IntRule(
        private val key: GameRules.Key<GameRules.IntRule>,
    ) : IGameRule<Int> {
        override val name: String get() = key.name
        override var value: Int
            get() = server.gameRules.get(key).get()
            set(value) {
                server.gameRules.get(key).set(value, server)
            }
    }
}