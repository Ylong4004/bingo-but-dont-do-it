package me.jfenn.bingo.integrations.ddi

/**
 * DDI 词条触发类型枚举 — 每个词条绑定一种可被事件系统检测的行为类型。
 * 移植自 Dont_do_it mod，精简为 Kotlin。
 */
enum class DDITriggerType(val displayName: String) {
    // ---- 基础行为 ----
    SNEAK("潜行"),
    ATTACK("攻击生物"),
    ATTACK_HOSTILE("打怪"),
    BLOCK_BREAK("破坏方块"),
    MINE_ORE("挖矿"),
    BLOCK_PLACE("放置方块"),
    CHAT("发送聊天"),
    TAKE_DAMAGE("受到伤害"),
    EAT("吃东西"),
    SPRINT("疾跑"),
    DROP_ITEM("丢弃物品"),
    OPEN_CONTAINER("打开容器"),
    PICKUP_ITEM("捡起物品"),

    // ---- 挖掘类 ----
    MINE_WOOD("挖掘木头"),
    MINE_STONE("挖掘石头"),
    MINE_COAL("挖掘煤矿"),
    MINE_IRON("挖掘铁矿"),
    MINE_COPPER("挖掘铜矿"),
    MINE_GOLD("挖掘金矿"),
    MINE_DIAMOND("挖掘钻石矿"),

    // ---- 拾取类 ----
    PICKUP_WOOD("拾取原木"),

    // ---- 合成类 ----
    CRAFT_CRAFTING_TABLE("合成工作台"),
    CRAFT_WOODEN_PICKAXE("合成木镐"),
    CRAFT_STONE_PICKAXE("合成石镐"),
    CRAFT_IRON_PICKAXE("合成铁镐"),
    CRAFT_WOODEN_AXE("合成木斧"),
    CRAFT_STONE_AXE("合成石斧"),
    CRAFT_IRON_AXE("合成铁斧"),
    CRAFT_WOODEN_SWORD("合成木剑"),
    CRAFT_STONE_SWORD("合成石剑"),
    CRAFT_IRON_SWORD("合成铁剑"),

    // ---- 视角方向类 ----
    LOOK_DOWN("低头"),
    LOOK_UP("抬头"),
    LOOK_EAST("看向东方"),
    LOOK_SOUTH("看向南方"),
    LOOK_WEST("看向西方"),
    LOOK_NORTH("看向北方"),

    // ---- 持续行为类 ----
    STAND_STILL_5S("禁止不动五秒"),
    LOOK_SAME_DIR_5S("持续看向一个方向五秒"),

    // ---- 环境状态类 ----
    ENCLOSED_1X2("自闭"),
    SUBMERGED("沉入水中"),

    // ---- 挖掘细分 ----
    MINE_ANDESITE("挖掘安山岩"),
    MINE_DIORITE("挖掘闪长岩"),
    MINE_DEEPSLATE("挖掘深板岩"),

    // ---- 站立方块类 ----
    STAND_ON_GRASS("站在草方块上"),
    STAND_ON_LEAVES("站在树叶上"),
    STAND_ON_STONE("站在石头上"),
    STAND_ON_DEEPSLATE("站在深板岩上"),
    STAND_ON_ANDESITE("站在安山岩上"),
    STAND_ON_DIORITE("站在闪长岩上"),

    // ---- 浮空 ----
    FLOATING("浮空"),

    // ---- 死亡/复活类 ----
    DEATH("死亡"),
    RESPAWN("复活"),
    NOT_RESPAWN_3S("三秒不复活"),
    NOT_RESPAWN_5S("五秒不复活"),
    NOT_RESPAWN_10S("十秒不复活"),

    // ---- 拾取细分 ----
    PICKUP_DIAMOND("获得钻石"),

    // ---- 背包物品类 ----
    HAS_COAL("背包里有煤炭"),
    HAS_IRON_INGOT("背包里有铁锭"),
    HAS_COPPER_INGOT("背包里有铜锭"),
    HAS_CRAFTING_TABLE("背包里有工作台"),
    HAS_FURNACE("背包里有熔炉"),
    HAS_AXE("背包里有斧头"),
    HAS_SWORD("背包里有剑"),
    HAS_STONE_PICKAXE("背包里有石镐"),
    HAS_WOODEN_PICKAXE("背包里有木镐"),
    HAS_IRON_PICKAXE("背包里有铁镐"),
    HAS_ROTTEN_FLESH("背包里有腐肉"),
    HAS_DIAMOND("背包里有钻石"),
    HAS_DIRT("背包里有泥土"),

    // ---- 特殊饮食 ----
    EAT_ROTTEN_FLESH("吃腐肉"),

    // ---- 即时效果词条 ----
    INSTANT_LOSE_HEART("直接扣一颗心"),
    INSTANT_GAIN_HEART("直接回一颗心"),

    // ---- 伤害细分 ----
    TAKE_FIRE_DAMAGE("受到火焰伤害"),

    // ---- 饥饿度 ----
    HUNGER_BELOW_18("饱食度低于18"),
    HUNGER_ABOVE_18("饱食度高于18"),

    // ---- Y 高度 ----
    Y_ABOVE_70("玩家高度Y＞70"),
    Y_BELOW_70("玩家高度Y＜70"),

    // ---- 攻击/造成伤害 ----
    ATTACK_PLAYER("攻击玩家"),
    DEAL_DAMAGE("造成伤害"),

    // ---- 持续行为 ----
    SPRINT_30S("连续奔跑30s"),
    SNEAK_5S("连续潜行5s"),
    JUMP_10_TIMES("跳跃10次"),

    // ---- 距离 ----
    FAR_FROM_ALL_15M("距离所有玩家15米"),
    TOO_CLOSE_TO_PLAYER("和玩家贴贴"),

    // ---- 挖掘/站立方块 ----
    MINE_GRANITE("挖掘花岗岩"),
    STAND_ON_GRANITE("站在花岗岩"),
    MINE_TUFF("挖掘凝灰岩"),
    STAND_ON_TUFF("站在凝灰岩上"),

    // ---- 经验/等级 ----
    GAIN_EXPERIENCE("获得经验"),
    LEVEL_UP("升级"),

    // ---- 穿戴装备 ----
    WEAR_ARMOR("穿装备"),

    // ---- 手持物品 ----
    HOLD_CRAFTING_TABLE("手持工作台"),
    HOLD_FURNACE("手持熔炉"),
    HOLD_WOODEN_PICKAXE("手持木镐"),
    HOLD_IRON_PICKAXE("手持铁镐"),
    HOLD_STONE_PICKAXE("手持石镐"),
    HOLD_WOODEN_AXE("手持木斧"),
    HOLD_STONE_AXE("手持石斧"),
    HOLD_IRON_AXE("手持铁斧"),

    // ---- 快捷栏选择 ----
    SELECT_SLOT_FIRST("选中快捷栏第一位"),
    SELECT_SLOT_LAST("选中快捷栏最后一位"),

    // ---- 下落高度 ----
    FALL_5_BLOCKS("下降5格高度"),

    // ---- 空手攻击 ----
    EMPTY_HAND_ATTACK("空手打人"),

    // ---- 背包物品 — 磨制石材 ----
    HAS_POLISHED_ANDESITE("背包里有磨制安山岩"),
    HAS_POLISHED_GRANITE("背包里有磨制花岗岩"),
    HAS_POLISHED_DIORITE("背包里有磨制闪长岩"),
    HAS_TUFF("背包里有凝灰岩"),
    HAS_STONE("背包里有石头"),
    HAS_SMOOTH_STONE("背包里有平滑石头"),
    HAS_LEAVES("背包里有树叶"),

    // ---- 背包里没有某类物品 ----
    NO_IRON_TOOLS_OR_ARMOR("背包里没有铁质工具或防具"),
    NO_DIAMOND_TOOLS_OR_ARMOR("背包里没有钻石工具或防具"),

    // ---- 背包物品 — 杂项 ----
    HAS_BONE("背包里有骨头"),
    HAS_STRING("背包里有线"),
    HAS_ENDER_PEARL("背包里有末影珍珠"),
    HAS_LEATHER("背包里有皮革"),
    HAS_WOOL("背包里有羊毛"),

    // ---- 副手/容器/挖掘/击杀/计数/倒计时/放置 ----
    HOLD_SHIELD_OFFHAND("副手持盾"),
    OPEN_CHEST("打开箱子"),
    OPEN_FURNACE("与熔炉交互"),
    OPEN_CRAFTING_TABLE("与工作台交互"),
    MINE_CRAFTING_TABLE("挖掘工作台"),
    MINE_FURNACE("挖掘熔炉"),
    KILL_IRON_GOLEM("杀死铁傀儡"),
    VILLAGER_TRADE("村民交易"),
    PLACE_30_BLOCKS("放置30个方块"),
    DROP_30_ITEMS("丢弃30个方块"),
    NO_JUMP_30S("30秒不跳"),
    NO_SNEAK_30S("30秒不潜行"),
    NO_SPRINT_30S("30秒不疾跑"),
    NO_JUMP_60S("60秒不跳"),
    NO_SNEAK_60S("60秒不潜行"),
    NO_SPRINT_60S("60秒不疾跑"),
    PLACE_DIRT("放置泥土"),
    PLACE_COBBLESTONE("放置圆石"),
    PLACE_COBBLED_DEEPSLATE("放置深板岩圆石"),
    PLACE_ANDESITE("放置安山岩"),
    PLACE_GRANITE("放置花岗岩"),
    PLACE_DIORITE("放置闪长岩"),
    PLACE_TUFF("放置凝灰岩"),
    PLACE_CRAFTING_TABLE("放置工作台"),
    PLACE_FURNACE("放置熔炉"),
    PLACE_CHEST("放置箱子"),

    // ---- 丢弃特定物品 ----
    DROP_DIRT("丢弃泥土"),
    DROP_COBBLESTONE("丢弃圆石"),
    DROP_COBBLED_DEEPSLATE("丢弃深板岩圆石"),
    DROP_ANDESITE("丢弃安山岩"),
    DROP_GRANITE("丢弃花岗岩"),
    DROP_DIORITE("丢弃闪长岩"),
    DROP_TUFF("丢弃凝灰岩"),
    DROP_WOODEN_PICKAXE("丢弃木镐"),

    // ---- 死亡细分 ----
    DEATH_BY_FALL("摔死"),
    DEATH_BY_LAVA("岩浆里游泳"),
    DEATH_BY_SUFFOCATION("窒息"),
    DEATH_BY_DROWN("溺死"),
    DEATH_BY_EXPLOSION("炸死"),

    // ---- 伤害细分 ----
    TAKE_PROJECTILE_DAMAGE("弹射物伤害"),
    TAKE_5_DAMAGE("一次性受到5滴血伤害"),

    // ---- 头顶方块 ----
    BLOCK_ABOVE_HEAD("头顶有方块遮挡"),
    NO_BLOCK_ABOVE_HEAD("头顶无方块遮挡"),

    // ---- 背包物品 - 桶类 ----
    HAS_BUCKET("背包里有桶"),
    HAS_WATER_BUCKET("背包里有水桶"),
    HAS_LAVA_BUCKET("背包里有岩浆桶"),

    // ---- 桶操作 ----
    FILL_BUCKET_WATER("用桶装水"),
    EMPTY_BUCKET_WATER("用桶倒水"),
    FILL_BUCKET_LAVA("用桶装岩浆"),
    EMPTY_BUCKET_LAVA("用桶倒岩浆"),

    // ---- 站在基岩上 ----
    STAND_ON_BEDROCK("站在基岩上");
}
