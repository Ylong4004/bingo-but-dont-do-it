package me.jfenn.bingo.integrations.ddi

import kotlin.random.Random

/**
 * DDI 词条池 — 管理所有可用词条，支持随机抽取。
 * 移植自 Dont_do_it mod。
 */
class DDIWordPool {

    data class WordEntry(
        val id: String,
        val displayText: String,
        val triggerType: DDITriggerType,
    )

    private val allWords = mutableListOf<WordEntry>()
    private val random = Random.Default

    init {
        initDefaultWords()
    }

    /** 初始化默认词条（160+ 词条） */
    private fun initDefaultWords() {
        add("sneak_01", "潜行", DDITriggerType.SNEAK)
        add("attack_01", "攻击生物", DDITriggerType.ATTACK)
        add("attack_02", "打怪", DDITriggerType.ATTACK_HOSTILE)
        add("break_01", "破坏方块", DDITriggerType.BLOCK_BREAK)
        add("break_02", "挖矿", DDITriggerType.MINE_ORE)
        add("place_01", "放置方块", DDITriggerType.BLOCK_PLACE)
        add("place_02", "搭方块", DDITriggerType.BLOCK_PLACE)
        add("chat_01", "发送聊天消息", DDITriggerType.CHAT)
        add("chat_02", "打字说话", DDITriggerType.CHAT)
        add("damage_01", "受到伤害", DDITriggerType.TAKE_DAMAGE)
        add("eat_01", "吃东西", DDITriggerType.EAT)
        add("sprint_01", "疾跑", DDITriggerType.SPRINT)
        add("drop_01", "丢弃物品", DDITriggerType.DROP_ITEM)
        add("container_01", "打开容器", DDITriggerType.OPEN_CONTAINER)
        add("pickup_01", "捡起物品", DDITriggerType.PICKUP_ITEM)

        // 挖掘类
        add("mine_wood_01", "挖掘木头", DDITriggerType.MINE_WOOD)
        add("mine_stone_01", "挖掘石头", DDITriggerType.MINE_STONE)
        add("mine_coal_01", "挖掘煤矿", DDITriggerType.MINE_COAL)
        add("mine_iron_01", "挖掘铁矿", DDITriggerType.MINE_IRON)
        add("mine_copper_01", "挖掘铜矿", DDITriggerType.MINE_COPPER)
        add("mine_gold_01", "挖掘金矿", DDITriggerType.MINE_GOLD)
        add("mine_diamond_01", "挖掘钻石矿", DDITriggerType.MINE_DIAMOND)

        // 拾取类
        add("pickup_wood_01", "拾取原木", DDITriggerType.PICKUP_WOOD)

        // 合成类
        add("craft_table_01", "合成工作台", DDITriggerType.CRAFT_CRAFTING_TABLE)
        add("craft_wooden_pickaxe", "合成木镐", DDITriggerType.CRAFT_WOODEN_PICKAXE)
        add("craft_stone_pickaxe", "合成石镐", DDITriggerType.CRAFT_STONE_PICKAXE)
        add("craft_iron_pickaxe", "合成铁镐", DDITriggerType.CRAFT_IRON_PICKAXE)
        add("craft_wooden_axe", "合成木斧", DDITriggerType.CRAFT_WOODEN_AXE)
        add("craft_stone_axe", "合成石斧", DDITriggerType.CRAFT_STONE_AXE)
        add("craft_iron_axe", "合成铁斧", DDITriggerType.CRAFT_IRON_AXE)
        add("craft_wooden_sword", "合成木剑", DDITriggerType.CRAFT_WOODEN_SWORD)
        add("craft_stone_sword", "合成石剑", DDITriggerType.CRAFT_STONE_SWORD)
        add("craft_iron_sword", "合成铁剑", DDITriggerType.CRAFT_IRON_SWORD)

        // 视角方向类
        add("look_down_01", "低头", DDITriggerType.LOOK_DOWN)
        add("look_up_01", "抬头", DDITriggerType.LOOK_UP)
        add("look_east_01", "看向东方", DDITriggerType.LOOK_EAST)
        add("look_south_01", "看向南方", DDITriggerType.LOOK_SOUTH)
        add("look_west_01", "看向西方", DDITriggerType.LOOK_WEST)
        add("look_north_01", "看向北方", DDITriggerType.LOOK_NORTH)

        // 持续行为类
        add("stand_still_01", "禁止不动五秒", DDITriggerType.STAND_STILL_5S)
        add("look_same_dir_01", "持续看向一个方向五秒", DDITriggerType.LOOK_SAME_DIR_5S)

        // 环境状态类
        add("enclosed_01", "自闭", DDITriggerType.ENCLOSED_1X2)
        add("submerged_01", "沉入水中", DDITriggerType.SUBMERGED)

        // 挖掘细分
        add("mine_andesite_01", "挖掘安山岩", DDITriggerType.MINE_ANDESITE)
        add("mine_diorite_01", "挖掘闪长岩", DDITriggerType.MINE_DIORITE)
        add("mine_deepslate_01", "挖掘深板岩", DDITriggerType.MINE_DEEPSLATE)

        // 站立方块类
        add("stand_grass_01", "站在草方块上", DDITriggerType.STAND_ON_GRASS)
        add("stand_leaves_01", "站在树叶上", DDITriggerType.STAND_ON_LEAVES)
        add("stand_stone_01", "站在石头上", DDITriggerType.STAND_ON_STONE)
        add("stand_deepslate_01", "站在深板岩上", DDITriggerType.STAND_ON_DEEPSLATE)
        add("stand_andesite_01", "站在安山岩上", DDITriggerType.STAND_ON_ANDESITE)
        add("stand_diorite_01", "站在闪长岩上", DDITriggerType.STAND_ON_DIORITE)

        // 浮空
        add("floating_01", "浮空", DDITriggerType.FLOATING)

        // 死亡/复活类
        add("death_01", "死亡", DDITriggerType.DEATH)
        add("respawn_01", "复活", DDITriggerType.RESPAWN)
        add("not_respawn_3s", "三秒不复活", DDITriggerType.NOT_RESPAWN_3S)
        add("not_respawn_5s", "五秒不复活", DDITriggerType.NOT_RESPAWN_5S)
        add("not_respawn_10s", "十秒不复活", DDITriggerType.NOT_RESPAWN_10S)

        // 拾取细分
        add("pickup_diamond_01", "获得钻石", DDITriggerType.PICKUP_DIAMOND)

        // 背包物品类
        add("has_coal_01", "背包里有煤炭", DDITriggerType.HAS_COAL)
        add("has_iron_01", "背包里有铁锭", DDITriggerType.HAS_IRON_INGOT)
        add("has_copper_01", "背包里有铜锭", DDITriggerType.HAS_COPPER_INGOT)
        add("has_table_01", "背包里有工作台", DDITriggerType.HAS_CRAFTING_TABLE)
        add("has_furnace_01", "背包里有熔炉", DDITriggerType.HAS_FURNACE)
        add("has_axe_01", "背包里有斧头", DDITriggerType.HAS_AXE)
        add("has_sword_01", "背包里有剑", DDITriggerType.HAS_SWORD)
        add("has_spick_01", "背包里有石镐", DDITriggerType.HAS_STONE_PICKAXE)
        add("has_wpick_01", "背包里有木镐", DDITriggerType.HAS_WOODEN_PICKAXE)
        add("has_ipick_01", "背包里有铁镐", DDITriggerType.HAS_IRON_PICKAXE)
        add("has_flesh_01", "背包里有腐肉", DDITriggerType.HAS_ROTTEN_FLESH)
        add("has_diamond_01", "背包里有钻石", DDITriggerType.HAS_DIAMOND)
        add("has_dirt_01", "背包里有泥土", DDITriggerType.HAS_DIRT)

        // 特殊饮食
        add("eat_rotten_flesh_01", "吃腐肉", DDITriggerType.EAT_ROTTEN_FLESH)

        // 即时效果词条
        add("instant_lose_01", "直接扣一颗心", DDITriggerType.INSTANT_LOSE_HEART)
        add("instant_gain_01", "直接回一颗心", DDITriggerType.INSTANT_GAIN_HEART)

        // 伤害细分
        add("fire_damage_01", "受到火焰伤害", DDITriggerType.TAKE_FIRE_DAMAGE)

        // 饥饿度
        add("hunger_below_01", "饱食度低于18", DDITriggerType.HUNGER_BELOW_18)
        add("hunger_above_01", "饱食度高于18", DDITriggerType.HUNGER_ABOVE_18)

        // Y高度
        add("y_above_01", "玩家高度Y＞70", DDITriggerType.Y_ABOVE_70)
        add("y_below_01", "玩家高度Y＜70", DDITriggerType.Y_BELOW_70)

        // 攻击/造成伤害
        add("attack_player_01", "攻击玩家", DDITriggerType.ATTACK_PLAYER)
        add("deal_damage_01", "造成伤害", DDITriggerType.DEAL_DAMAGE)

        // 持续行为
        add("sprint_30s_01", "连续奔跑30s", DDITriggerType.SPRINT_30S)
        add("sneak_5s_01", "连续潜行5s", DDITriggerType.SNEAK_5S)
        add("jump_10_01", "跳跃10次", DDITriggerType.JUMP_10_TIMES)

        // 距离
        add("far_15m_01", "距离所有玩家15米", DDITriggerType.FAR_FROM_ALL_15M)
        add("too_close_01", "和玩家贴贴", DDITriggerType.TOO_CLOSE_TO_PLAYER)

        // 挖掘/站立方块
        add("mine_granite_01", "挖掘花岗岩", DDITriggerType.MINE_GRANITE)
        add("stand_granite_01", "站在花岗岩", DDITriggerType.STAND_ON_GRANITE)
        add("mine_tuff_01", "挖掘凝灰岩", DDITriggerType.MINE_TUFF)
        add("stand_tuff_01", "站在凝灰岩上", DDITriggerType.STAND_ON_TUFF)

        // 经验/等级
        add("gain_xp_01", "获得经验", DDITriggerType.GAIN_EXPERIENCE)
        add("level_up_01", "升级", DDITriggerType.LEVEL_UP)

        // 穿戴装备
        add("wear_armor_01", "穿装备", DDITriggerType.WEAR_ARMOR)

        // 手持物品
        add("hold_table_01", "手持工作台", DDITriggerType.HOLD_CRAFTING_TABLE)
        add("hold_furnace_01", "手持熔炉", DDITriggerType.HOLD_FURNACE)
        add("hold_wpick_01", "手持木镐", DDITriggerType.HOLD_WOODEN_PICKAXE)
        add("hold_ipick_01", "手持铁镐", DDITriggerType.HOLD_IRON_PICKAXE)
        add("hold_spick_01", "手持石镐", DDITriggerType.HOLD_STONE_PICKAXE)
        add("hold_waxe_01", "手持木斧", DDITriggerType.HOLD_WOODEN_AXE)
        add("hold_saxe_01", "手持石斧", DDITriggerType.HOLD_STONE_AXE)
        add("hold_iaxe_01", "手持铁斧", DDITriggerType.HOLD_IRON_AXE)

        // 快捷栏选择
        add("slot_first_01", "选中快捷栏第一位", DDITriggerType.SELECT_SLOT_FIRST)
        add("slot_last_01", "选中快捷栏最后一位", DDITriggerType.SELECT_SLOT_LAST)

        // 下落高度
        add("fall_5_01", "下降5格高度", DDITriggerType.FALL_5_BLOCKS)

        // 空手攻击
        add("empty_attack_01", "空手打人", DDITriggerType.EMPTY_HAND_ATTACK)

        // 背包物品 — 磨制石材/石头
        add("has_pandesite_01", "背包里有磨制安山岩", DDITriggerType.HAS_POLISHED_ANDESITE)
        add("has_pgranite_01", "背包里有磨制花岗岩", DDITriggerType.HAS_POLISHED_GRANITE)
        add("has_pdiorite_01", "背包里有磨制闪长岩", DDITriggerType.HAS_POLISHED_DIORITE)
        add("has_tuff_01", "背包里有凝灰岩", DDITriggerType.HAS_TUFF)
        add("has_stone_01", "背包里有石头", DDITriggerType.HAS_STONE)
        add("has_sstone_01", "背包里有平滑石头", DDITriggerType.HAS_SMOOTH_STONE)
        add("has_leaves_01", "背包里有树叶", DDITriggerType.HAS_LEAVES)

        // 背包里没有某类物品
        add("no_iron_01", "背包里没有铁质工具或防具", DDITriggerType.NO_IRON_TOOLS_OR_ARMOR)
        add("no_diamond_01", "背包里没有钻石工具或防具", DDITriggerType.NO_DIAMOND_TOOLS_OR_ARMOR)

        // 背包物品 — 杂项
        add("has_bone_01", "背包里有骨头", DDITriggerType.HAS_BONE)
        add("has_string_01", "背包里有线", DDITriggerType.HAS_STRING)
        add("has_pearl_01", "背包里有末影珍珠", DDITriggerType.HAS_ENDER_PEARL)
        add("has_leather_01", "背包里有皮革", DDITriggerType.HAS_LEATHER)
        add("has_wool_01", "背包里有羊毛", DDITriggerType.HAS_WOOL)

        // 副手/容器/挖掘/击杀/计数/倒计时/放置
        add("offhand_shield_01", "副手持盾", DDITriggerType.HOLD_SHIELD_OFFHAND)
        add("open_chest_01", "打开箱子", DDITriggerType.OPEN_CHEST)
        add("open_furnace_01", "与熔炉交互", DDITriggerType.OPEN_FURNACE)
        add("open_table_01", "与工作台交互", DDITriggerType.OPEN_CRAFTING_TABLE)
        add("mine_table_01", "挖掘工作台", DDITriggerType.MINE_CRAFTING_TABLE)
        add("mine_furnace_01", "挖掘熔炉", DDITriggerType.MINE_FURNACE)
        add("kill_golem_01", "杀死铁傀儡", DDITriggerType.KILL_IRON_GOLEM)
        add("trade_01", "村民交易", DDITriggerType.VILLAGER_TRADE)
        add("place_30_01", "放置30个方块", DDITriggerType.PLACE_30_BLOCKS)
        add("drop_30_01", "丢弃30个方块", DDITriggerType.DROP_30_ITEMS)
        add("no_jump_30s_01", "30秒不跳", DDITriggerType.NO_JUMP_30S)
        add("no_sneak_30s_01", "30秒不潜行", DDITriggerType.NO_SNEAK_30S)
        add("no_sprint_30s_01", "30秒不疾跑", DDITriggerType.NO_SPRINT_30S)
        add("no_jump_60s_01", "60秒不跳", DDITriggerType.NO_JUMP_60S)
        add("no_sneak_60s_01", "60秒不潜行", DDITriggerType.NO_SNEAK_60S)
        add("no_sprint_60s_01", "60秒不疾跑", DDITriggerType.NO_SPRINT_60S)
        add("place_dirt_01", "放置泥土", DDITriggerType.PLACE_DIRT)
        add("place_cobble_01", "放置圆石", DDITriggerType.PLACE_COBBLESTONE)
        add("place_cdeep_01", "放置深板岩圆石", DDITriggerType.PLACE_COBBLED_DEEPSLATE)
        add("place_andesite_01", "放置安山岩", DDITriggerType.PLACE_ANDESITE)
        add("place_granite_01", "放置花岗岩", DDITriggerType.PLACE_GRANITE)
        add("place_diorite_01", "放置闪长岩", DDITriggerType.PLACE_DIORITE)
        add("place_tuff_01", "放置凝灰岩", DDITriggerType.PLACE_TUFF)
        add("place_table_01", "放置工作台", DDITriggerType.PLACE_CRAFTING_TABLE)
        add("place_furnace_01", "放置熔炉", DDITriggerType.PLACE_FURNACE)
        add("place_chest_01", "放置箱子", DDITriggerType.PLACE_CHEST)

        // 丢弃特定物品
        add("drop_dirt_01", "丢弃泥土", DDITriggerType.DROP_DIRT)
        add("drop_cobble_01", "丢弃圆石", DDITriggerType.DROP_COBBLESTONE)
        add("drop_cdeep_01", "丢弃深板岩圆石", DDITriggerType.DROP_COBBLED_DEEPSLATE)
        add("drop_andesite_01", "丢弃安山岩", DDITriggerType.DROP_ANDESITE)
        add("drop_granite_01", "丢弃花岗岩", DDITriggerType.DROP_GRANITE)
        add("drop_diorite_01", "丢弃闪长岩", DDITriggerType.DROP_DIORITE)
        add("drop_tuff_01", "丢弃凝灰岩", DDITriggerType.DROP_TUFF)
        add("drop_wpick_01", "丢弃木镐", DDITriggerType.DROP_WOODEN_PICKAXE)

        // 死亡细分
        add("death_fall_01", "摔死", DDITriggerType.DEATH_BY_FALL)
        add("death_lava_01", "岩浆里游泳", DDITriggerType.DEATH_BY_LAVA)
        add("death_suffocate_01", "窒息", DDITriggerType.DEATH_BY_SUFFOCATION)
        add("death_drown_01", "溺死", DDITriggerType.DEATH_BY_DROWN)
        add("death_explode_01", "炸死", DDITriggerType.DEATH_BY_EXPLOSION)

        // 伤害细分
        add("dmg_projectile_01", "弹射物伤害", DDITriggerType.TAKE_PROJECTILE_DAMAGE)
        add("dmg_5_01", "一次性受到5滴血伤害", DDITriggerType.TAKE_5_DAMAGE)

        // 头顶方块
        add("block_above_01", "头顶有方块遮挡", DDITriggerType.BLOCK_ABOVE_HEAD)
        add("no_block_above_01", "头顶无方块遮挡", DDITriggerType.NO_BLOCK_ABOVE_HEAD)

        // 桶类
        add("has_bucket_01", "背包里有桶", DDITriggerType.HAS_BUCKET)
        add("has_water_bucket_01", "背包里有水桶", DDITriggerType.HAS_WATER_BUCKET)
        add("has_lava_bucket_01", "背包里有岩浆桶", DDITriggerType.HAS_LAVA_BUCKET)

        // 桶操作
        add("fill_water_01", "用桶装水", DDITriggerType.FILL_BUCKET_WATER)
        add("empty_water_01", "用桶倒水", DDITriggerType.EMPTY_BUCKET_WATER)
        add("fill_lava_01", "用桶装岩浆", DDITriggerType.FILL_BUCKET_LAVA)
        add("empty_lava_01", "用桶倒岩浆", DDITriggerType.EMPTY_BUCKET_LAVA)

        // 站在基岩上
        add("stand_bedrock_01", "站在基岩上", DDITriggerType.STAND_ON_BEDROCK)
    }

    fun add(id: String, displayText: String, type: DDITriggerType) {
        allWords.add(WordEntry(id, displayText, type))
    }

    /** 为指定数量的玩家各抽取一个不重复的词条 */
    fun drawWords(count: Int): List<WordEntry> {
        if (count > allWords.size) {
            throw IllegalStateException("玩家数量($count)超过词条池大小(${allWords.size})")
        }
        return allWords.shuffled(random).take(count)
    }

    /** 随机抽取一个词条 */
    fun drawSingle(): WordEntry {
        return allWords.random(random)
    }

    /**
     * Draws a replacement that differs from the previous rule whenever the
     * pool has an alternative. This prevents an apparent no-op timer reroll
     * and avoids immediately dealing the same continuous trigger again.
     */
    fun drawReplacement(previous: WordEntry?): WordEntry {
        if (previous == null) return drawSingle()
        val alternatives = allWords.filter { it.triggerType != previous.triggerType }
        return (alternatives.ifEmpty { allWords.filter { it.id != previous.id } })
            .ifEmpty { allWords }
            .random(random)
    }

    /** 根据显示文本查找词条 */
    fun findByDisplayText(displayText: String): WordEntry? {
        return allWords.find { it.displayText == displayText }
    }

    /** 根据 ID 查找词条 */
    fun findById(id: String): WordEntry? {
        return allWords.find { it.id == id }
    }

    fun getAllWords(): List<WordEntry> = allWords.toList()

    fun size(): Int = allWords.size
}
