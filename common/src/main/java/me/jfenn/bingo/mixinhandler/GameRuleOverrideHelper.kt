package me.jfenn.bingo.mixinhandler

import me.jfenn.bingo.platform.world.IGameRule

object GameRuleOverrideHelper {
    val gameRuleOverrides = mutableMapOf<String, Any>()

    internal fun <T: Any> setOverride(gameRule: IGameRule<T>, value: T?) {
        if (value != null) {
            gameRuleOverrides[gameRule.name] = value
        } else {
            gameRuleOverrides.remove(gameRule.name)
        }
    }
}