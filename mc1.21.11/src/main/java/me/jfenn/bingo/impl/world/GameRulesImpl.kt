package me.jfenn.bingo.impl.world

import me.jfenn.bingo.platform.world.IGameRule
import me.jfenn.bingo.platform.world.IGameRules
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.world.rule.GameRule
import net.minecraft.world.rule.GameRuleType
import net.minecraft.world.rule.GameRules

class GameRulesImpl(
    private val server: MinecraftServer,
) : IGameRules {

    override val announceAdvancements = BooleanRule(GameRules.ANNOUNCE_ADVANCEMENTS)
    override val showDeathMessages = BooleanRule(GameRules.SHOW_DEATH_MESSAGES)
    override val keepInventory = BooleanRule(GameRules.KEEP_INVENTORY)
    override val pvp = BooleanRule(GameRules.PVP)

    override fun get(name: String): IGameRule<*>? {
        val rule = Registries.GAME_RULE.get(Identifier.ofVanilla(name))
            ?: return null

        return when (rule.type) {
            GameRuleType.BOOL -> BooleanRule(
                @Suppress("UNCHECKED_CAST") (rule as GameRule<Boolean>)
            )
            GameRuleType.INT -> IntRule(
                @Suppress("UNCHECKED_CAST") (rule as GameRule<Int>)
            )
        }
    }

    inner class BooleanRule(
        private val rule: GameRule<Boolean>,
    ) : IGameRule<Boolean> {
        override val name: String get() = rule.id.path
        override var value: Boolean
            get() = server.overworld.gameRules.getValue(rule)
            set(value) {
                server.worlds.forEach {
                    it.gameRules.setValue(rule, value, server)
                }
            }
    }

    inner class IntRule(
        private val rule: GameRule<Int>,
    ) : IGameRule<Int> {
        override val name: String get() = rule.id.path
        override var value: Int
            get() = server.overworld.gameRules.getValue(rule)
            set(value) {
                server.worlds.forEach {
                    it.gameRules.setValue(rule, value, server)
                }
            }
    }
}