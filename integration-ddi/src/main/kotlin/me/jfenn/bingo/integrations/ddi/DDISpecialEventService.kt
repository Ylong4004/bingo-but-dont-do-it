package me.jfenn.bingo.integrations.ddi

import me.jfenn.bingo.common.options.DDISpecialEventType
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventCallbacks
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventConfig
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventController
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventModifier
import me.jfenn.bingo.integrations.ddi.special.DDISpecialEventRuntimeSnapshot
import me.jfenn.bingo.integrations.ddi.special.DDISpecialHeartAdjustment
import me.jfenn.bingo.integrations.ddi.special.DDI_SPECIAL_ENTITY_TAG_PREFIX
import net.minecraft.entity.Entity
import net.minecraft.entity.boss.BossBar
import net.minecraft.entity.boss.ServerBossBar
import net.minecraft.entity.damage.DamageSource
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import org.slf4j.Logger
import java.util.Collections
import java.util.EnumSet
import java.util.IdentityHashMap
import java.util.UUID

/** 将独立模组的 30 种事件调度器接入由服务端权威管理的一局 DDI 游戏。 */
class DDISpecialEventService(
    private val server: MinecraftServer,
    private val manager: DDIObjectiveManager,
    private val log: Logger,
) : DDISpecialEventCallbacks {
    private val controller = DDISpecialEventController(server, this)
    private val modifiers = EnumSet.noneOf(DDISpecialEventModifier::class.java)
    private var bossBar: ServerBossBar? = null
    private var registered = false

    fun start(config: DDISpecialEventConfig) {
        stop()
        val validated = config.validated()
        try {
            controller.start(validated)
        } catch (failure: Throwable) {
            log.error("[DDI Events] Could not start the special-event scheduler", failure)
            runCatching { controller.stop() }
            return
        }
        if (validated.enabled && validated.enabledEvents.isNotEmpty()) {
            DDISpecialEventHooks.register(server, this)
            registered = true
            log.info(
                "[DDI Events] Started with interval {}s and {} selected events",
                validated.intervalSeconds,
                validated.enabledEvents.size,
            )
        }
        refreshBossBar()
    }

    fun tickServerTick() {
        if (!registered) return
        try {
            controller.tickServerTick()
        } catch (failure: Throwable) {
            log.error("[DDI Events] Event execution failed; disabling events for this round", failure)
            stop()
        } finally {
            // 单次事件回调可能会结算多个目标；等所有生命变化应用完毕后，
            // 再统一判断胜负。
            manager.finalizeSpecialHeartAdjustments()
            runCatching(::refreshBossBar)
                .onFailure { log.warn("[DDI Events] Could not refresh the event boss bar") }
        }
    }

    fun trigger(type: DDISpecialEventType): Boolean {
        var triggered = false
        try {
            triggered = controller.trigger(type)
        } catch (failure: Throwable) {
            log.error("[DDI Events] Forced event {} failed; disabling events", type.id, failure)
            stop()
        } finally {
            manager.finalizeSpecialHeartAdjustments()
            runCatching(::refreshBossBar)
        }
        return triggered
    }

    fun stopActive(): Boolean {
        val stopped = controller.stopActive()
        if (stopped) refreshBossBar()
        return stopped
    }

    fun stop() {
        var cleanupFailure: Throwable? = null
        try {
            controller.stop()
        } catch (failure: Throwable) {
            cleanupFailure = failure
        } finally {
            if (registered) DDISpecialEventHooks.unregister(server, this)
            registered = false
            modifiers.clear()
            runCatching { bossBar?.setVisible(false) }
            runCatching { bossBar?.clearPlayers() }
            bossBar = null
        }
        cleanupFailure?.let {
            // 生命周期清理采用尽力而为的策略，绝不能阻碍 Bingo 或
            // 语音会话完成关闭。
            log.error("[DDI Events] One or more event cleanup operations failed", it)
        }
    }

    fun snapshot(): DDISpecialEventRuntimeSnapshot = controller.snapshot()

    internal fun onDiamondOreMined(player: ServerPlayerEntity) {
        if (!isActiveParticipant(player) ||
            DDISpecialEventModifier.DIAMOND_BLESSING !in modifiers
        ) return
        try {
            controller.onDiamondOreMined(player)
        } catch (failure: Throwable) {
            log.error("[DDI Events] Diamond blessing callback failed", failure)
            stop()
        } finally {
            manager.finalizeSpecialHeartAdjustments()
            runCatching(::refreshBossBar)
        }
    }

    internal fun onPlayerDamaged(player: ServerPlayerEntity, source: DamageSource) {
        if (!isActiveParticipant(player)) return
        try {
            controller.onPlayerDamaged(player, source)
        } catch (failure: Throwable) {
            log.error("[DDI Events] Damage callback failed", failure)
            stop()
        }
    }

    internal fun onPlayerLeaving(player: ServerPlayerEntity) {
        try {
            controller.onPlayerLeaving(player)
        } catch (failure: Throwable) {
            log.error("[DDI Events] Player-leave cleanup failed", failure)
            stop()
        }
    }

    internal fun ownsSpecialEntity(entity: Entity): Boolean = controller.ownsEntity(entity)

    internal fun modifyDurabilityDamage(player: ServerPlayerEntity, amount: Int): Int {
        if (amount <= 0 || !isActiveParticipant(player)) return amount
        return when {
            DDISpecialEventModifier.DURABILITY_IMMUNITY in modifiers -> 0
            DDISpecialEventModifier.EQUIPMENT_RUST in modifiers ->
                amount.toLong().times(5L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            else -> amount
        }
    }

    internal fun hasHungerDisease(player: ServerPlayerEntity): Boolean =
        isActiveParticipant(player) && DDISpecialEventModifier.HUNGER_DISEASE in modifiers

    private fun isActiveParticipant(player: ServerPlayerEntity): Boolean =
        manager.activeObjectiveId(player.uuid) != null

    override fun activePlayers(): List<ServerPlayerEntity> = manager.activePlayers()

    override fun objectiveId(playerId: UUID): String? = manager.activeObjectiveId(playerId)

    override fun adjustHeart(
        objectiveId: String,
        delta: Int,
        eventType: DDISpecialEventType,
        actorId: UUID?,
    ): DDISpecialHeartAdjustment = manager.adjustSpecialHeart(
        objectiveId = objectiveId,
        delta = delta,
        eventType = eventType,
        actorId = actorId,
    )

    override fun broadcast(message: Text) {
        server.playerManager.broadcast(message, false)
    }

    override fun message(player: ServerPlayerEntity, message: Text, actionBar: Boolean) {
        player.sendMessage(message, actionBar)
    }

    override fun playEventSound(eventType: DDISpecialEventType) {
        val soundAndPitch: Pair<SoundEvent, Float> = when (eventType) {
            DDISpecialEventType.MONSTER_RAMPAGE,
            DDISpecialEventType.ANVIL_STORM,
            DDISpecialEventType.TNT_RAIN,
            DDISpecialEventType.CAVE_IN,
            DDISpecialEventType.FIRE_TRAIL,
            DDISpecialEventType.ARROW_TRIAL -> SoundEvents.ENTITY_TNT_PRIMED to 0.72f

            DDISpecialEventType.DIAMOND_GIFT,
            DDISpecialEventType.DIAMOND_BLESSING,
            DDISpecialEventType.LIFE_BLESSING,
            DDISpecialEventType.CROP_SPEED_GROW,
            DDISpecialEventType.DURABILITY_BLESSING,
            DDISpecialEventType.TRADE_MERCHANT -> SoundEvents.ENTITY_PLAYER_LEVELUP to 1.1f

            DDISpecialEventType.DIAMOND_CURSE,
            DDISpecialEventType.ECLIPSE_CURSE,
            DDISpecialEventType.EQUIPMENT_RUST,
            DDISpecialEventType.HUNGER_DISEASE -> SoundEvents.BLOCK_NOTE_BLOCK_BASS.value() to 0.55f

            DDISpecialEventType.CALM,
            DDISpecialEventType.CLOUD_EFFECT,
            DDISpecialEventType.CHICKEN_RAIN,
            DDISpecialEventType.EVERYONE_BABY -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value() to 1.45f

            DDISpecialEventType.FOOD_RAIN,
            DDISpecialEventType.XP_STORM,
            DDISpecialEventType.ORE_UNDERFOOT -> SoundEvents.ITEM_LODESTONE_COMPASS_LOCK to 1.15f

            DDISpecialEventType.PUMPKIN_HEAD,
            DDISpecialEventType.INVENTORY_SHUFFLE,
            DDISpecialEventType.INVENTORY_MIGRATION -> SoundEvents.ENTITY_SHULKER_AMBIENT to 1.15f

            DDISpecialEventType.PLAYER_SWAP,
            DDISpecialEventType.SKY_WATER_CHALLENGE,
            DDISpecialEventType.SLIME_POSSESSION -> SoundEvents.BLOCK_PORTAL_TRAVEL to 0.9f

            DDISpecialEventType.CAGE_TRIAL -> SoundEvents.BLOCK_LEVER_CLICK to 0.8f
        }
        val (sound, pitch) = soundAndPitch
        activePlayers().forEach { player -> player.playSound(sound, 0.85f, pitch) }
    }

    override fun setModifier(modifier: DDISpecialEventModifier, enabled: Boolean) {
        if (enabled) modifiers.add(modifier) else modifiers.remove(modifier)
        log.debug("[DDI Events] Modifier {} = {}", modifier, enabled)
    }

    private fun refreshBossBar() {
        val snapshot = controller.snapshot()
        val activeEvent = snapshot.activeEvent
        if (activeEvent == null) {
            bossBar?.setVisible(false)
            bossBar?.clearPlayers()
            return
        }

        val bar = bossBar ?: ServerBossBar(
            Text.literal("不要做：特殊事件"),
            snapshot.activeColor ?: BossBar.Color.WHITE,
            BossBar.Style.PROGRESS,
        ).also { bossBar = it }
        bar.setName(
            Text.literal("特殊事件：${snapshot.activeDisplayName}（${snapshot.activeRemainingSeconds} 秒）")
        )
        bar.setColor(snapshot.activeColor ?: BossBar.Color.WHITE)
        val duration = snapshot.activeDurationSeconds.coerceAtLeast(1)
        bar.percent = (snapshot.activeRemainingSeconds.toFloat() / duration).coerceIn(0f, 1f)

        val players = activePlayers().toSet()
        bar.players.toList().filterNot(players::contains).forEach(bar::removePlayer)
        players.filterNot(bar.players::contains).forEach(bar::addPlayer)
        bar.setVisible(players.isNotEmpty())
    }
}

/** 供 Fabric 全局回调和底层 Mixin 调用的静态入口。 */
object DDISpecialEventHooks {
    private val services = Collections.synchronizedMap(
        IdentityHashMap<MinecraftServer, DDISpecialEventService>()
    )

    internal fun register(server: MinecraftServer, service: DDISpecialEventService) {
        services[server] = service
    }

    internal fun unregister(server: MinecraftServer, service: DDISpecialEventService) {
        synchronized(services) {
            if (services[server] === service) services.remove(server)
        }
    }

    @JvmStatic
    fun reportDiamondOreMined(player: ServerPlayerEntity) {
        service(player)?.onDiamondOreMined(player)
    }

    @JvmStatic
    fun reportPlayerDamaged(player: ServerPlayerEntity, source: DamageSource) {
        service(player)?.onPlayerDamaged(player, source)
    }

    @JvmStatic
    fun modifyDurabilityDamage(player: ServerPlayerEntity, amount: Int): Int =
        service(player)?.modifyDurabilityDamage(player, amount) ?: amount

    @JvmStatic
    fun hasHungerDisease(player: ServerPlayerEntity): Boolean =
        service(player)?.hasHungerDisease(player) == true

    /**
     * 事件实体可能在区块卸载时被序列化。区块重新加载后，若实体携带的
     * 本局标记不属于当前生效的 DDI 会话，则将其移除。
     */
    @JvmStatic
    fun reportEntityLoaded(server: MinecraftServer, entity: Entity) {
        if (entity is ServerPlayerEntity || DDI_SPECIAL_ENTITY_TAG_PREFIX !in entity.commandTags) return
        if (services[server]?.ownsSpecialEntity(entity) != true) entity.discard()
    }

    private fun service(player: ServerPlayerEntity): DDISpecialEventService? =
        player.entityWorld.server?.let(services::get)
}
