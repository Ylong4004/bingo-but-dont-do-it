package me.jfenn.bingo.integrations.ddi.special

import me.jfenn.bingo.common.options.DDISpecialEventType
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.entity.damage.DamageSource
import net.minecraft.registry.RegistryKey
import java.util.ArrayDeque
import java.util.UUID
import kotlin.random.Random

internal const val DDI_SPECIAL_ENTITY_TAG_PREFIX = "bingo-ddi-special-event"
internal const val DDI_SPECIAL_SESSION_TAG_PREFIX = "bingo-ddi-special-session-"

internal data class DDISpecialWorldBlockKey(
    val world: RegistryKey<World>,
    val pos: BlockPos,
)

private data class DDITrackedBlock(
    val original: BlockState,
    var expectedReplacement: BlockState,
)

/**
 * 仅恢复仍是事件所放置方块类型的方块。铁栏杆连接、火焰年龄和红石矿石
 * 发光等运行时属性可能在不替换事件方块的情况下合理变化。每个键都记录
 * 所属维度，因此玩家跨维度移动不会使清理目标发生偏移。
 */
internal class DDITemporaryBlockTracker(
    private val server: MinecraftServer,
) {
    private val blocks = linkedMapOf<DDISpecialWorldBlockKey, DDITrackedBlock>()

    fun replace(world: ServerWorld, pos: BlockPos, replacement: BlockState): Boolean {
        val immutablePos = pos.toImmutable()
        val existing = world.getBlockState(immutablePos)
        if (existing.isOf(Blocks.BEDROCK)) return false
        if (world.getBlockEntity(immutablePos) != null) return false
        if (existing.getHardness(world, immutablePos) < 0f) return false

        val key = DDISpecialWorldBlockKey(world.registryKey, immutablePos)
        val tracked = blocks[key]
        if (tracked == null) {
            blocks[key] = DDITrackedBlock(existing, replacement)
        } else {
            tracked.expectedReplacement = replacement
        }
        return world.setBlockState(immutablePos, replacement)
    }

    fun restore() {
        try {
            blocks.forEach { (key, tracked) ->
                runCatching {
                    val world = server.getWorld(key.world) ?: return@runCatching
                    if (world.getBlockState(key.pos).isOf(tracked.expectedReplacement.block)) {
                        world.setBlockState(key.pos, tracked.original)
                    }
                }
            }
        } finally {
            blocks.clear()
        }
    }

    val size: Int get() = blocks.size
}

internal class DDISpecialEventContext(
    val server: MinecraftServer,
    val callbacks: DDISpecialEventCallbacks,
    val definition: DDISpecialEventDefinition,
    val random: Random,
    private val entityRegistry: DDISpecialEntityRegistry,
    private val statusEffectRegistry: DDISpecialStatusEffectRegistry,
    private val sessionTag: String,
) {
    val temporaryBlocks = DDITemporaryBlockTracker(server)

    fun activePlayers(): List<ServerPlayerEntity> = callbacks.activePlayers()

    fun tag(entity: Entity): Entity {
        entity.addCommandTag(DDI_SPECIAL_ENTITY_TAG_PREFIX)
        entity.addCommandTag("$DDI_SPECIAL_ENTITY_TAG_PREFIX-${definition.type.id}")
        entity.addCommandTag(sessionTag)
        entityRegistry.track(definition.type, entity)
        return entity
    }

    fun spawn(world: ServerWorld, entity: Entity): Boolean {
        tag(entity)
        return world.spawnEntity(entity)
    }

    fun objectiveId(player: ServerPlayerEntity): String? = callbacks.objectiveId(player.uuid)

    /** 将此事件追踪的实体标记为失效，包含当前未加载的实体。 */
    fun discardTrackedEntities() {
        entityRegistry.cleanup(definition.type)
    }

    fun applyTemporaryStatusEffect(
        player: ServerPlayerEntity,
        effect: RegistryEntry<StatusEffect>,
        durationTicks: Int,
        amplifier: Int = 0,
        ambient: Boolean = false,
        showParticles: Boolean = true,
        showIcon: Boolean = true,
    ): Boolean = statusEffectRegistry.apply(
        eventType = definition.type,
        player = player,
        effect = effect,
        durationTicks = durationTicks,
        amplifier = amplifier,
        ambient = ambient,
        showParticles = showParticles,
        showIcon = showIcon,
    )
}

/** 除实体标签外还保留实体引用，使清理也能覆盖未加载区块中的实体。 */
internal class DDISpecialEntityRegistry {
    private data class Entry(
        val type: DDISpecialEventType,
        val entity: Entity,
    )

    private val entries = mutableListOf<Entry>()
    private val tombstones = mutableSetOf<UUID>()

    fun track(type: DDISpecialEventType, entity: Entity) {
        entries += Entry(type, entity)
    }

    fun cleanup(type: DDISpecialEventType? = null) {
        val selected = entries.filter { type == null || it.type == type }
        selected.map(Entry::entity).forEach { entity ->
            tombstones += entity.uuid
            runCatching(entity::discard)
        }
        entries.removeAll(selected.toSet())
    }

    fun prune() {
        entries.removeAll { !it.entity.isAlive }
    }

    fun isTombstoned(entity: Entity): Boolean = entity.uuid in tombstones

    fun beginSession() {
        entries.clear()
        tombstones.clear()
    }
}

/**
 * 仅追踪添加到原本为空的状态效果槽位中的效果。这样 `/bingo end` 可以移除
 * 事件自身添加的效果，而不会删除原本就存在的药水或信标效果，也不会删除
 * 后来由更强效果来源替换的效果。
 */
internal class DDISpecialStatusEffectRegistry(
    private val server: MinecraftServer,
) {
    private data class Key(
        val playerId: UUID,
        val effect: RegistryEntry<StatusEffect>,
    )

    private data class Entry(
        val eventType: DDISpecialEventType,
        val instance: StatusEffectInstance,
        val amplifier: Int,
        var expiresAtTick: Long,
    )

    private val entries = linkedMapOf<Key, Entry>()

    fun apply(
        eventType: DDISpecialEventType,
        player: ServerPlayerEntity,
        effect: RegistryEntry<StatusEffect>,
        durationTicks: Int,
        amplifier: Int,
        ambient: Boolean,
        showParticles: Boolean,
        showIcon: Boolean,
    ): Boolean {
        val key = Key(player.uuid, effect)
        val tracked = entries[key]
        val current = player.getStatusEffect(effect)
        val replacement = StatusEffectInstance(
            effect,
            durationTicks,
            amplifier,
            ambient,
            showParticles,
            showIcon,
        )

        if (tracked != null && current === tracked.instance) {
            player.addStatusEffect(replacement)
            tracked.expiresAtTick = server.ticks.toLong() + durationTicks
            return true
        }
        if (current != null) return false

        if (!player.addStatusEffect(replacement)) return false
        val installed = player.getStatusEffect(effect) ?: return false
        entries[key] = Entry(
            eventType = eventType,
            instance = installed,
            amplifier = amplifier,
            expiresAtTick = server.ticks.toLong() + durationTicks,
        )
        return true
    }

    fun cleanup(
        eventType: DDISpecialEventType? = null,
        playerId: UUID? = null,
    ) {
        val selected = entries.filter { (key, entry) ->
            (eventType == null || entry.eventType == eventType) &&
                (playerId == null || key.playerId == playerId)
        }
        selected.forEach { (key, entry) ->
            runCatching {
                val player = server.playerManager.getPlayer(key.playerId)
                val current = player?.getStatusEffect(key.effect)
                val expectedRemaining = (entry.expiresAtTick - server.ticks.toLong())
                    .coerceAtLeast(0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                if (current === entry.instance &&
                    current.amplifier == entry.amplifier &&
                    current.duration <= expectedRemaining + CLEANUP_GRACE_TICKS
                ) {
                    player.removeStatusEffect(key.effect)
                }
            }
            entries.remove(key)
        }
    }

    fun prune() {
        entries.entries.removeIf { (key, entry) ->
            server.playerManager.getPlayer(key.playerId)?.getStatusEffect(key.effect) !== entry.instance
        }
    }

    private companion object {
        const val CLEANUP_GRACE_TICKS = 5
    }
}

internal interface DDISpecialEvent {
    val definition: DDISpecialEventDefinition

    /** 事件激活时恰好调用一次。 */
    fun start()

    /** 每秒调用一次，而不是每个 Minecraft Tick 调用一次。 */
    fun tickSecond(elapsedSeconds: Int, remainingSeconds: Int)

    /** 仅在事件正常持续时间结束后调用。 */
    fun finish()

    /** 事件中断或整个控制器停止时调用。 */
    fun cleanup()

    /** 钻石祝福集成使用的外部钩子。 */
    fun onDiamondOreMined(player: ServerPlayerEntity) = Unit

    /** 用于区分事件箭矢伤害与其他伤害的外部钩子。 */
    fun onPlayerDamaged(player: ServerPlayerEntity, source: DamageSource) = Unit

    /** 参与者卸载前，撤销其玩家绑定状态的最后机会。 */
    fun onPlayerLeaving(player: ServerPlayerEntity) = Unit
}

internal abstract class DDIBaseSpecialEvent(
    protected val context: DDISpecialEventContext,
) : DDISpecialEvent {
    final override val definition: DDISpecialEventDefinition = context.definition

    private var started = false
    private var closed = false

    final override fun start() {
        check(!started) { "Special event ${definition.type} was started twice" }
        started = true
        onStart()
    }

    final override fun tickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        if (started && !closed) onTickSecond(elapsedSeconds, remainingSeconds)
    }

    final override fun finish() {
        if (!started || closed) return
        try {
            onFinish()
        } catch (failure: Throwable) {
            try {
                onCleanup()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        } finally {
            closed = true
        }
    }

    final override fun cleanup() {
        if (!started || closed) return
        try {
            onCleanup()
        } finally {
            closed = true
        }
    }

    protected abstract fun onStart()

    protected open fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) = Unit

    protected open fun onFinish() = Unit

    protected open fun onCleanup() = Unit
}

/** 在事件池条件允许时避开最近三次结果的加权选择器。 */
class DDISpecialEventSelector(
    private val random: Random = Random.Default,
    private val recentLimit: Int = 3,
) {
    init {
        require(recentLimit >= 0)
    }

    private val recent = ArrayDeque<DDISpecialEventType>()

    fun draw(enabled: Set<DDISpecialEventType>): DDISpecialEventType? {
        if (enabled.isEmpty()) return null
        val ordered = enabled.sortedBy(DDISpecialEventType::ordinal)
        val withoutRecent = ordered.filterNot(recent::contains)
        val candidates = withoutRecent.ifEmpty { ordered }
        val totalWeight = candidates.sumOf { DDISpecialEventCatalog[it].weight }
        var cursor = random.nextInt(totalWeight)
        val selected = candidates.first { type ->
            cursor -= DDISpecialEventCatalog[type].weight
            cursor < 0
        }
        remember(selected)
        return selected
    }

    fun recentEvents(): List<DDISpecialEventType> = recent.toList()

    fun reset() = recent.clear()

    private fun remember(type: DDISpecialEventType) {
        if (recentLimit == 0) return
        recent.remove(type)
        recent.addLast(type)
        while (recent.size > recentLimit) recent.removeFirst()
    }
}

internal fun cleanupDDISpecialEntities(
    server: MinecraftServer,
    eventType: DDISpecialEventType? = null,
) {
    val requiredTag = eventType?.let { "$DDI_SPECIAL_ENTITY_TAG_PREFIX-${it.id}" }
    server.worlds.forEach { world ->
        world.iterateEntities()
            .filter { entity ->
                entity !is ServerPlayerEntity && if (requiredTag != null) {
                    requiredTag in entity.commandTags
                } else {
                    entity.commandTags.any { it.startsWith(DDI_SPECIAL_ENTITY_TAG_PREFIX) }
                }
            }
            .toList()
            .forEach { entity -> runCatching(entity::discard) }
    }
}
