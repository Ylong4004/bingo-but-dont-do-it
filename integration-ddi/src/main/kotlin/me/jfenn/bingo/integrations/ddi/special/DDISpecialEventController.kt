package me.jfenn.bingo.integrations.ddi.special

import me.jfenn.bingo.common.options.DDISpecialEventType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.Entity
import java.util.UUID
import kotlin.random.Random

data class DDISpecialEventConfig(
    val enabled: Boolean,
    val intervalSeconds: Int,
    val enabledEvents: Set<DDISpecialEventType>,
) {
    fun validated(): DDISpecialEventConfig = copy(
        intervalSeconds = intervalSeconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS),
        enabledEvents = enabledEvents.toSet(),
    )

    companion object {
        const val MIN_INTERVAL_SECONDS = 10
        const val MAX_INTERVAL_SECONDS = 3600
    }
}

/**
 * 随对局作用域创建、无需注册全局监听器的特殊事件调度器。
 *
 * 持有者通过现有的 Bingo 对局 Tick 调用 [tickServerTick]。这里不会注册
 * 任何 Fabric 全局回调，因此反复创建和销毁对局作用域不会累积监听器。
 */
class DDISpecialEventController(
    private val server: MinecraftServer,
    private val callbacks: DDISpecialEventCallbacks,
    private val random: Random = Random.Default,
) {
    private val selector = DDISpecialEventSelector(random, recentLimit = 3)
    private val entityRegistry = DDISpecialEntityRegistry()
    private val statusEffectRegistry = DDISpecialStatusEffectRegistry(server)

    private var config = DDISpecialEventConfig(false, 300, emptySet())
    private var running = false
    private var serverTickAccumulator = 0
    private var countdownSeconds = 0
    private var active: DDISpecialEvent? = null
    private var activeElapsedSeconds = 0
    private var activeRemainingSeconds = 0
    private var sessionTag: String? = null

    fun start(newConfig: DDISpecialEventConfig) {
        stop()
        entityRegistry.beginSession()
        config = newConfig.validated()
        selector.reset()
        running = config.enabled && config.enabledEvents.isNotEmpty()
        sessionTag = if (running) {
            "$DDI_SPECIAL_SESSION_TAG_PREFIX${UUID.randomUUID()}"
        } else {
            null
        }
        countdownSeconds = if (running) config.intervalSeconds else 0
    }

    /** 每个 Minecraft 服务端 Tick 调用一次。 */
    fun tickServerTick() {
        if (!running) return
        serverTickAccumulator++
        if (serverTickAccumulator < TICKS_PER_SECOND) return
        serverTickAccumulator = 0
        tickSecond()
    }

    /** 对外开放，以供确定性的集成测试和显式的一秒调度器调用。 */
    fun tickSecond() {
        if (!running) return
        val current = active
        if (current == null) {
            if (countdownSeconds > 0) countdownSeconds--
            if (countdownSeconds <= 0) triggerRandom()
            return
        }

        try {
            current.tickSecond(activeElapsedSeconds, activeRemainingSeconds)
        } catch (failure: Throwable) {
            try {
                interruptActive()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            countdownSeconds = config.intervalSeconds
            throw failure
        }
        activeElapsedSeconds++
        activeRemainingSeconds--
        if (activeRemainingSeconds <= 0) finishActive()
    }

    fun triggerRandom(): DDISpecialEventType? {
        if (!running) return null
        val selected = selector.draw(config.enabledEvents) ?: return null
        trigger(selected)
        return selected
    }

    /**
     * 管理员或测试使用的钩子。事件类型不必位于自动事件池中，
     * 但控制器必须已经在某局游戏中运行。
     */
    fun trigger(type: DDISpecialEventType): Boolean {
        if (!running) return false
        val currentSessionTag = sessionTag ?: return false
        interruptActive()
        serverTickAccumulator = 0

        val definition = DDISpecialEventCatalog[type]
        val event = DDISpecialEventFactory.create(
            DDISpecialEventContext(
                server,
                callbacks,
                definition,
                random,
                entityRegistry,
                statusEffectRegistry,
                currentSessionTag,
            )
        )
        try {
            event.start()
        } catch (failure: Throwable) {
            try {
                event.cleanup()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            cleanupDDISpecialEntities(server, type)
            throw failure
        }

        if (definition.isInstant) {
            event.finish()
            entityRegistry.prune()
            statusEffectRegistry.prune()
            countdownSeconds = config.intervalSeconds
            return true
        }

        active = event
        activeElapsedSeconds = 0
        activeRemainingSeconds = definition.durationSeconds
        countdownSeconds = 0
        return true
    }

    /** 仅在服务端确认钻石矿石被挖掘后调用的钩子。 */
    fun onDiamondOreMined(player: ServerPlayerEntity) {
        active?.onDiamondOreMined(player)
    }

    fun onPlayerDamaged(player: ServerPlayerEntity, source: DamageSource) {
        active?.onPlayerDamaged(player, source)
    }

    fun onPlayerLeaving(player: ServerPlayerEntity) {
        try {
            active?.onPlayerLeaving(player)
        } finally {
            statusEffectRegistry.cleanup(playerId = player.uuid)
        }
    }

    /** 仅取消当前事件，并重新开始一次完整的事件间隔倒计时。 */
    fun stopActive(): Boolean {
        if (active == null) return false
        interruptActive()
        serverTickAccumulator = 0
        countdownSeconds = if (running) config.intervalSeconds else 0
        return true
    }

    fun snapshot(): DDISpecialEventRuntimeSnapshot {
        val definition = active?.definition
        return DDISpecialEventRuntimeSnapshot(
            running = running,
            intervalSeconds = config.intervalSeconds,
            countdownSeconds = countdownSeconds,
            enabledEvents = config.enabledEvents,
            activeEvent = definition?.type,
            activeDisplayName = definition?.displayName,
            activeRemainingSeconds = activeRemainingSeconds,
            activeDurationSeconds = definition?.durationSeconds ?: 0,
            activeColor = definition?.color,
            recentEvents = selector.recentEvents(),
        )
    }

    /** 仅当事件实体属于当前正在运行的对局时返回 true。 */
    fun ownsEntity(entity: Entity): Boolean =
        running && !entityRegistry.isTombstoned(entity) &&
            sessionTag?.let(entity.commandTags::contains) == true

    /** 幂等的完整会话清理，包含已经结束的瞬时事件所创建的实体。 */
    fun stop() {
        var failure: Throwable? = null
        val cleanupSteps = listOf<() -> Unit>(
            ::interruptActive,
            { entityRegistry.cleanup() },
            { statusEffectRegistry.cleanup() },
            { cleanupDDISpecialEntities(server) },
            { callbacks.setModifier(DDISpecialEventModifier.DIAMOND_BLESSING, false) },
            { callbacks.setModifier(DDISpecialEventModifier.DURABILITY_IMMUNITY, false) },
            { callbacks.setModifier(DDISpecialEventModifier.EQUIPMENT_RUST, false) },
            { callbacks.setModifier(DDISpecialEventModifier.HUNGER_DISEASE, false) },
        )
        cleanupSteps.forEach { step ->
            try {
                step()
            } catch (stepFailure: Throwable) {
                if (failure == null) failure = stepFailure else failure?.addSuppressed(stepFailure)
            }
        }
        running = false
        serverTickAccumulator = 0
        countdownSeconds = 0
        activeElapsedSeconds = 0
        activeRemainingSeconds = 0
        sessionTag = null
        failure?.let { throw it }
    }

    private fun finishActive() {
        val event = active ?: return
        try {
            event.finish()
        } finally {
            statusEffectRegistry.cleanup(eventType = event.definition.type)
            entityRegistry.prune()
            active = null
            activeElapsedSeconds = 0
            activeRemainingSeconds = 0
            countdownSeconds = config.intervalSeconds
        }
    }

    private fun interruptActive() {
        val event = active ?: return
        val type = event.definition.type
        try {
            event.cleanup()
        } finally {
            statusEffectRegistry.cleanup(eventType = type)
            entityRegistry.cleanup(type)
            cleanupDDISpecialEntities(server, type)
            active = null
            activeElapsedSeconds = 0
            activeRemainingSeconds = 0
        }
    }

    private companion object {
        const val TICKS_PER_SECOND = 20
    }
}
