package me.jfenn.bingo.common.options

import kotlinx.serialization.Serializable

/**
 * DDI 可用特殊事件的稳定可序列化标识符。
 *
 * 玩法实现在 integration-ddi 中。把标识符放在这里，可以让预设、命令和大厅
 * 设置墙共享同一数据源，同时不反转 common -> integration 的依赖方向。
 */
@Serializable
enum class DDISpecialEventType {
    MONSTER_RAMPAGE,
    DIAMOND_GIFT,
    DIAMOND_BLESSING,
    DIAMOND_CURSE,
    ECLIPSE_CURSE,
    CALM,
    CLOUD_EFFECT,
    FOOD_RAIN,
    XP_STORM,
    LIFE_BLESSING,
    ORE_UNDERFOOT,
    ANVIL_STORM,
    TNT_RAIN,
    CAVE_IN,
    PUMPKIN_HEAD,
    INVENTORY_SHUFFLE,
    CHICKEN_RAIN,
    PLAYER_SWAP,
    FIRE_TRAIL,
    CAGE_TRIAL,
    SKY_WATER_CHALLENGE,
    CROP_SPEED_GROW,
    DURABILITY_BLESSING,
    EQUIPMENT_RUST,
    HUNGER_DISEASE,
    INVENTORY_MIGRATION,
    EVERYONE_BABY,
    SLIME_POSSESSION,
    ARROW_TRIAL,
    TRADE_MERCHANT;

    val id: String get() = name.lowercase()

    companion object {
        fun fromId(id: String): DDISpecialEventType? = entries.firstOrNull {
            it.id == id.trim().lowercase()
        }

        /**
         * 默认事件池避开不可逆的世界破坏和对 Bingo 资源影响最强的事件。
         * 所有省略的事件仍可通过自定义选择器或“全部”预设启用。
         */
        val BALANCED: Set<DDISpecialEventType> = setOf(
            MONSTER_RAMPAGE,
            DIAMOND_GIFT,
            DIAMOND_BLESSING,
            ECLIPSE_CURSE,
            CALM,
            CLOUD_EFFECT,
            FOOD_RAIN,
            XP_STORM,
            LIFE_BLESSING,
            ANVIL_STORM,
            PUMPKIN_HEAD,
            INVENTORY_SHUFFLE,
            PLAYER_SWAP,
            CAGE_TRIAL,
            DURABILITY_BLESSING,
            EQUIPMENT_RUST,
            HUNGER_DISEASE,
            EVERYONE_BABY,
            ARROW_TRIAL,
        )
    }
}

enum class DDISpecialEventPreset(
    val eventTypes: Set<DDISpecialEventType>,
) {
    BALANCED(DDISpecialEventType.BALANCED),
    RESOURCE(
        setOf(
            DDISpecialEventType.DIAMOND_GIFT,
            DDISpecialEventType.DIAMOND_BLESSING,
            DDISpecialEventType.FOOD_RAIN,
            DDISpecialEventType.XP_STORM,
            DDISpecialEventType.LIFE_BLESSING,
            DDISpecialEventType.ORE_UNDERFOOT,
            DDISpecialEventType.CROP_SPEED_GROW,
            DDISpecialEventType.DURABILITY_BLESSING,
            DDISpecialEventType.TRADE_MERCHANT,
        )
    ),
    CHALLENGE(
        setOf(
            DDISpecialEventType.MONSTER_RAMPAGE,
            DDISpecialEventType.DIAMOND_CURSE,
            DDISpecialEventType.ECLIPSE_CURSE,
            DDISpecialEventType.CLOUD_EFFECT,
            DDISpecialEventType.ANVIL_STORM,
            DDISpecialEventType.CAVE_IN,
            DDISpecialEventType.PUMPKIN_HEAD,
            DDISpecialEventType.EQUIPMENT_RUST,
            DDISpecialEventType.HUNGER_DISEASE,
            DDISpecialEventType.ARROW_TRIAL,
        )
    ),
    CHAOS(DDISpecialEventType.entries.toSet()),
}
