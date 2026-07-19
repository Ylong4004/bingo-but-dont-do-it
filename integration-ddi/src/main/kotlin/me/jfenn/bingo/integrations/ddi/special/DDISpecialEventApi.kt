package me.jfenn.bingo.integrations.ddi.special

import me.jfenn.bingo.common.options.DDISpecialEventType
import net.minecraft.entity.boss.BossBar
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID

/** 底层实现在事件调度器之外的全局规则钩子。 */
enum class DDISpecialEventModifier {
    DIAMOND_BLESSING,
    DURABILITY_IMMUNITY,
    EQUIPMENT_RUST,
    HUNGER_DISEASE,
}

/** 权威 DDI 目标层变更生命后返回的结果。 */
data class DDISpecialHeartAdjustment(
    val requestedDelta: Int,
    val appliedDelta: Int,
    val hearts: Int,
    val maxHearts: Int,
    val eliminated: Boolean,
)

/**
 * 特殊事件与现有 DDI 权威对局之间的精简适配接口。
 *
 * 活跃玩家接口 [activePlayers] 的实现只能返回在线且尚未淘汰的参与者。
 * 生命调整接口 [adjustHeart] 负责限制变化范围、应用队伍共享归属，
 * 并执行常规 DDI 胜负判断流程。
 */
interface DDISpecialEventCallbacks {
    fun activePlayers(): List<ServerPlayerEntity>

    fun objectiveId(playerId: UUID): String?

    fun adjustHeart(
        objectiveId: String,
        delta: Int,
        eventType: DDISpecialEventType,
        actorId: UUID? = null,
    ): DDISpecialHeartAdjustment

    fun broadcast(message: Text)

    fun message(player: ServerPlayerEntity, message: Text, actionBar: Boolean = true)

    /** 事件开始后向本局参与者播放其语义对应的提示音。 */
    fun playEventSound(eventType: DDISpecialEventType)

    /**
     * 启用或停用由集成事件或 Mixin 实现的钩子。
     * 调用具备幂等性；无论正常结束还是强制清理，每次启用都必须有对应的停用。
     */
    fun setModifier(modifier: DDISpecialEventModifier, enabled: Boolean)
}

data class DDISpecialEventDefinition(
    val type: DDISpecialEventType,
    val displayName: String,
    val durationSeconds: Int,
    val color: BossBar.Color,
    val weight: Int = DEFAULT_WEIGHT,
) {
    init {
        require(durationSeconds >= 0)
        require(weight > 0)
    }

    val isInstant: Boolean get() = durationSeconds == 0

    companion object {
        const val DEFAULT_WEIGHT = 100
    }
}

/** 全部 30 种移植事件运行时元数据的唯一来源。 */
object DDISpecialEventCatalog {
    private fun definition(
        type: DDISpecialEventType,
        name: String,
        seconds: Int,
        color: BossBar.Color,
        weight: Int = DDISpecialEventDefinition.DEFAULT_WEIGHT,
    ) = DDISpecialEventDefinition(type, name, seconds, color, weight)

    val definitions: Map<DDISpecialEventType, DDISpecialEventDefinition> = listOf(
        definition(DDISpecialEventType.MONSTER_RAMPAGE, "怪物狂潮", 0, BossBar.Color.RED),
        definition(DDISpecialEventType.DIAMOND_GIFT, "钻石馈赠", 0, BossBar.Color.YELLOW),
        definition(DDISpecialEventType.DIAMOND_BLESSING, "钻石祝福", 120, BossBar.Color.BLUE),
        definition(DDISpecialEventType.DIAMOND_CURSE, "钻石诅咒", 0, BossBar.Color.RED),
        definition(DDISpecialEventType.ECLIPSE_CURSE, "日食诅咒", 0, BossBar.Color.PURPLE),
        definition(DDISpecialEventType.CALM, "平静", 0, BossBar.Color.WHITE),
        definition(DDISpecialEventType.CLOUD_EFFECT, "唉，云朵？", 0, BossBar.Color.WHITE),
        definition(DDISpecialEventType.FOOD_RAIN, "美食雨", 10, BossBar.Color.GREEN),
        definition(DDISpecialEventType.XP_STORM, "经验风暴", 10, BossBar.Color.GREEN),
        definition(DDISpecialEventType.LIFE_BLESSING, "生命赐福", 10, BossBar.Color.PINK),
        definition(DDISpecialEventType.ORE_UNDERFOOT, "脚下出矿", 10, BossBar.Color.BLUE),
        definition(DDISpecialEventType.ANVIL_STORM, "铁砧暴雨", 10, BossBar.Color.RED),
        definition(DDISpecialEventType.TNT_RAIN, "TNT降雨", 0, BossBar.Color.RED),
        definition(DDISpecialEventType.CAVE_IN, "地底塌陷", 30, BossBar.Color.PURPLE),
        definition(DDISpecialEventType.PUMPKIN_HEAD, "全员南瓜头", 60, BossBar.Color.YELLOW),
        definition(DDISpecialEventType.INVENTORY_SHUFFLE, "物品栏洗牌", 0, BossBar.Color.BLUE),
        definition(DDISpecialEventType.CHICKEN_RAIN, "小鸡天降", 0, BossBar.Color.WHITE),
        definition(DDISpecialEventType.PLAYER_SWAP, "玩家互换位置", 0, BossBar.Color.PURPLE),
        definition(DDISpecialEventType.FIRE_TRAIL, "脚步生火", 30, BossBar.Color.RED),
        definition(DDISpecialEventType.CAGE_TRIAL, "囚笼试炼", 10, BossBar.Color.RED),
        definition(DDISpecialEventType.SKY_WATER_CHALLENGE, "高空落水挑战", 30, BossBar.Color.BLUE),
        definition(DDISpecialEventType.CROP_SPEED_GROW, "作物速成", 15, BossBar.Color.GREEN),
        definition(DDISpecialEventType.DURABILITY_BLESSING, "豁免祝福", 120, BossBar.Color.PINK),
        definition(DDISpecialEventType.EQUIPMENT_RUST, "装备锈蚀", 120, BossBar.Color.RED),
        definition(DDISpecialEventType.HUNGER_DISEASE, "饥饿疫病", 30, BossBar.Color.RED),
        definition(DDISpecialEventType.INVENTORY_MIGRATION, "物资迁徙", 0, BossBar.Color.RED),
        definition(DDISpecialEventType.EVERYONE_BABY, "全员变幼体", 60, BossBar.Color.GREEN),
        definition(DDISpecialEventType.SLIME_POSSESSION, "粘液附身", 30, BossBar.Color.GREEN),
        definition(DDISpecialEventType.ARROW_TRIAL, "箭雨试炼", 10, BossBar.Color.RED),
        definition(DDISpecialEventType.TRADE_MERCHANT, "交易商人", 30, BossBar.Color.YELLOW, weight = 1),
    ).associateBy { it.type }.also { definitions ->
        check(definitions.keys == DDISpecialEventType.entries.toSet()) {
            "Every DDI special event type must have runtime metadata"
        }
    }

    operator fun get(type: DDISpecialEventType): DDISpecialEventDefinition =
        definitions.getValue(type)
}

data class DDISpecialEventRuntimeSnapshot(
    val running: Boolean,
    val intervalSeconds: Int,
    val countdownSeconds: Int,
    val enabledEvents: Set<DDISpecialEventType>,
    val activeEvent: DDISpecialEventType?,
    val activeDisplayName: String?,
    val activeRemainingSeconds: Int,
    val activeDurationSeconds: Int,
    val activeColor: BossBar.Color?,
    val recentEvents: List<DDISpecialEventType>,
)
