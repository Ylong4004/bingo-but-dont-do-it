package me.jfenn.bingo.integrations.ddi

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.mob.Monster
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.stat.Stats
import net.minecraft.util.ActionResult
import net.minecraft.util.Identifier
import net.minecraft.world.Heightmap
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DDI 词条触发检测器。
 * 类实例模式，回调注入。注册到 Fabric API 事件中。
 */
class DDITriggerDetector(
    private val server: MinecraftServer,
) {

    var onSignalHandler: ((ServerPlayerEntity, DDISignal) -> Boolean)? = null
    var activePlayerIdsHandler: (() -> Set<UUID>)? = null
    var currentTriggerHandler: ((UUID) -> DDITriggerType?)? = null
    var currentRuleHandler: ((UUID) -> DDIRuleDefinition?)? = null
    var isEnemyPlayerHandler: ((UUID, UUID) -> Boolean)? = null
    var onEnemyPlayerDamageHandler:
        ((ServerPlayerEntity, ServerPlayerEntity) -> Pair<Boolean, Boolean>)? = null

    private enum class CardinalDirection {
        SOUTH,
        WEST,
        NORTH,
        EAST,
    }

    companion object {
        private val callbacksRegistered = AtomicBoolean(false)
        private val activeDetectors = Collections.synchronizedMap(
            IdentityHashMap<MinecraftServer, DDITriggerDetector>()
        )

        private fun detectorFor(server: MinecraftServer): DDITriggerDetector? = activeDetectors[server]

        private fun detectorFor(player: ServerPlayerEntity): DDITriggerDetector? =
            player.entityWorld.server?.let(::detectorFor)

        @JvmStatic
        fun reportAction(player: ServerPlayerEntity, type: DDITriggerType) {
            detectorFor(player)?.fire(player, type)
        }

        @JvmStatic
        fun reportCrafted(player: ServerPlayerEntity, item: Item) {
            val detector = detectorFor(player) ?: return
            val itemId = Registries.ITEM.getId(item).toString()
            val type = when (vanillaPath(itemId)) {
                "crafting_table" -> DDITriggerType.CRAFT_CRAFTING_TABLE
                "wooden_pickaxe" -> DDITriggerType.CRAFT_WOODEN_PICKAXE
                "stone_pickaxe" -> DDITriggerType.CRAFT_STONE_PICKAXE
                "iron_pickaxe" -> DDITriggerType.CRAFT_IRON_PICKAXE
                "wooden_axe" -> DDITriggerType.CRAFT_WOODEN_AXE
                "stone_axe" -> DDITriggerType.CRAFT_STONE_AXE
                "iron_axe" -> DDITriggerType.CRAFT_IRON_AXE
                "wooden_sword" -> DDITriggerType.CRAFT_WOODEN_SWORD
                "stone_sword" -> DDITriggerType.CRAFT_STONE_SWORD
                "iron_sword" -> DDITriggerType.CRAFT_IRON_SWORD
                else -> null
            }
            detector.emitItemSignal(
                player = player,
                kind = DDISignalKind.ITEM_CRAFTED,
                item = item,
                legacyAliases = setOfNotNull(type),
            )
        }

        @JvmStatic
        fun reportPickedUp(player: ServerPlayerEntity, item: Item, itemCount: Int) {
            val detector = detectorFor(player) ?: return
            val aliases = linkedSetOf(DDITriggerType.PICKUP_ITEM)
            when (val vanillaItemId = vanillaPath(Registries.ITEM.getId(item).toString())) {
                null -> Unit
                "diamond" -> aliases += DDITriggerType.PICKUP_DIAMOND
                else -> if (vanillaItemId.endsWith("_log")) aliases += DDITriggerType.PICKUP_WOOD
            }
            detector.emitItemSignal(
                player = player,
                kind = DDISignalKind.ITEM_PICKED_UP,
                item = item,
                quantity = itemCount,
                legacyAliases = aliases,
            )
        }

        /** 仅在原版完成食物消耗后调用。 */
        @JvmStatic
        fun reportConsumed(player: ServerPlayerEntity, itemId: String) {
            val detector = detectorFor(player) ?: return
            detector.fire(player, DDITriggerType.EAT)
            if (vanillaPath(itemId) == "rotten_flesh") {
                detector.fire(player, DDITriggerType.EAT_ROTTEN_FLESH)
            }
        }

        /** 根据权威的 JUMP 统计调用，而非根据腾空状态调用。 */
        @JvmStatic
        fun reportJump(player: ServerPlayerEntity) {
            val detector = detectorFor(player) ?: return
            if (!detector.isActive(player)) return

            val id = player.uuid
            detector.lastJumpTick[id] = detector.server.ticks.toLong()
            if (detector.currentTrigger(player) != DDITriggerType.JUMP_10_TIMES) return

            val count = detector.jumpCount.merge(id, 1) { current, increment ->
                (current + increment).coerceAtMost(10)
            } ?: 1
            if (count >= 10 && detector.jump10Triggered.getOrDefault(id, false).not()) {
                detector.jump10Triggered[id] = true
                detector.fire(player, DDITriggerType.JUMP_10_TIMES)
            }
        }

        @JvmStatic
        fun reportDropped(
            player: ServerPlayerEntity,
            item: Item,
            itemCount: Int,
            isBlockItem: Boolean,
        ) {
            val detector = detectorFor(player) ?: return
            if (!detector.isActive(player)) return
            val aliases = linkedSetOf(DDITriggerType.DROP_ITEM)
            when (vanillaPath(Registries.ITEM.getId(item).toString())) {
                "dirt" -> DDITriggerType.DROP_DIRT
                "cobblestone" -> DDITriggerType.DROP_COBBLESTONE
                "cobbled_deepslate" -> DDITriggerType.DROP_COBBLED_DEEPSLATE
                "andesite" -> DDITriggerType.DROP_ANDESITE
                "granite" -> DDITriggerType.DROP_GRANITE
                "diorite" -> DDITriggerType.DROP_DIORITE
                "tuff" -> DDITriggerType.DROP_TUFF
                "wooden_pickaxe" -> DDITriggerType.DROP_WOODEN_PICKAXE
                else -> null
            }?.let(aliases::add)
            detector.emitItemSignal(
                player = player,
                kind = DDISignalKind.ITEM_DROPPED,
                item = item,
                quantity = itemCount,
                isBlockItem = isBlockItem,
                legacyAliases = aliases,
            )
        }

        @JvmStatic
        fun reportPlaced(player: ServerPlayerEntity, block: Block) {
            val detector = detectorFor(player) ?: return
            if (!detector.isActive(player)) return
            val aliases = linkedSetOf(DDITriggerType.BLOCK_PLACE)
            vanillaPath(Registries.BLOCK.getId(block).toString())
                ?.let(detector::legacyPlaceAlias)
                ?.let(aliases::add)
            detector.emitBlockSignal(
                player = player,
                kind = DDISignalKind.BLOCK_PLACED,
                block = block,
                legacyAliases = aliases,
            )
        }

        @JvmStatic
        fun beginBlockInteraction(player: ServerPlayerEntity, blockId: String) {
            detectorFor(player)?.interactingBlockIds?.set(player.uuid, blockId)
        }

        @JvmStatic
        fun endBlockInteraction(player: ServerPlayerEntity) {
            detectorFor(player)?.interactingBlockIds?.remove(player.uuid)
        }

        @JvmStatic
        fun reportContainerOpened(player: ServerPlayerEntity) {
            val detector = detectorFor(player) ?: return
            // OPEN_CONTAINER 包括实体物品栏（矿车/马）以及其他成功打开的容器界面。
            // 范围更窄的箱子、熔炉和工作台规则仍要求点击对应的原版方块。
            detector.fire(player, DDITriggerType.OPEN_CONTAINER)
            val blockId = detector.interactingBlockIds[player.uuid] ?: return
            vanillaPath(blockId)?.let { detector.detectOpenContainer(player, it) }
        }

        private fun vanillaPath(id: String): String? {
            val separator = id.indexOf(':')
            if (separator < 0) return id
            return if (id.substring(0, separator) == Identifier.DEFAULT_NAMESPACE) {
                id.substring(separator + 1)
            } else {
                null
            }
        }

        /**
         * 报告原版应用盾牌和伤害冷却调整后的伤害。非致命调用来自 Fabric AFTER_DAMAGE；
         * 致命调用则使用紧邻 LivingEntity.onDeath 之前、同一方法内的最终值。
         */
        @JvmStatic
        fun reportDamage(entity: LivingEntity, source: DamageSource, damageTaken: Float) {
            if (damageTaken <= 0f) return

            val attacker = source.attacker as? ServerPlayerEntity
            val attackerDetector = attacker?.let(::detectorFor)
            if (attacker != null) {
                if (entity is Monster) attackerDetector?.fire(attacker, DDITriggerType.ATTACK_HOSTILE)
                attackerDetector?.fire(attacker, DDITriggerType.DEAL_DAMAGE)
            }

            if (entity is ServerPlayerEntity) {
                val victimDetector = detectorFor(entity)
                victimDetector?.fire(entity, DDITriggerType.TAKE_DAMAGE)
                if (victimDetector != null && victimDetector.isFireDamage(source)) {
                    victimDetector.fire(entity, DDITriggerType.TAKE_FIRE_DAMAGE)
                }
                if (source.isIn(DamageTypeTags.IS_PROJECTILE)) {
                    victimDetector?.fire(entity, DDITriggerType.TAKE_PROJECTILE_DAMAGE)
                }
            }
        }

        /**
         * 报告 RETURN Mixin 发出的唯一最终伤害结果。
         * 生命损失沿用原有的五点伤害规则；消耗伤害吸收生命也会让敌对玩家的攻击
         * 对新的交互规则视为有效。
         */
        @JvmStatic
        fun reportFinalDamage(
            entity: LivingEntity,
            source: DamageSource,
            healthLost: Float,
            absorptionLost: Float,
        ) {
            val victim = entity as? ServerPlayerEntity ?: return
            val victimDetector = detectorFor(victim) ?: return
            if (healthLost >= 5f) {
                victimDetector.fire(victim, DDITriggerType.TAKE_5_DAMAGE)
            }

            val attacker = source.attacker as? ServerPlayerEntity ?: return
            val attackerDetector = detectorFor(attacker) ?: return
            if (attackerDetector !== victimDetector) return

            val effectiveDamage = DDITriggerRules.effectiveDamageLoss(
                healthLost = healthLost,
                absorptionLost = absorptionLost,
            )
            val areEnemies = attackerDetector.isEnemyPlayerHandler
                ?.invoke(attacker.uuid, victim.uuid)
                ?: false
            if (!DDITriggerRules.isQualifyingEnemyPlayerDamage(
                    effectiveDamage = effectiveDamage,
                    attackerId = attacker.uuid,
                    victimId = victim.uuid,
                    areEnemies = areEnemies,
                )
            ) return

            val batchHandler = attackerDetector.onEnemyPlayerDamageHandler
            if (batchHandler != null) {
                val (attackerAccepted, victimAccepted) = batchHandler(attacker, victim)
                val tick = attackerDetector.server.ticks.toLong()
                if (attackerAccepted) attackerDetector.lastTriggeredTick[attacker.uuid] = tick
                if (victimAccepted) victimDetector.lastTriggeredTick[victim.uuid] = tick
            } else {
                attackerDetector.emitSignal(
                    attacker,
                    DDISignal(
                        kind = DDISignalKind.ENEMY_PLAYER_DAMAGED,
                        legacyAliases = setOf(DDITriggerType.DAMAGE_ENEMY_PLAYER),
                    ),
                )
                victimDetector.emitSignal(
                    victim,
                    DDISignal(
                        kind = DDISignalKind.DAMAGED_BY_ENEMY_PLAYER,
                        legacyAliases = setOf(DDITriggerType.DAMAGED_BY_ENEMY_PLAYER),
                    ),
                )
            }
        }
    }

    private val wasSneaking = ConcurrentHashMap<UUID, Boolean>()
    private val wasSprinting = ConcurrentHashMap<UUID, Boolean>()
    private val wasLookingDown = ConcurrentHashMap<UUID, Boolean>()
    private val wasLookingUp = ConcurrentHashMap<UUID, Boolean>()
    private val lastCardinalDirection = ConcurrentHashMap<UUID, CardinalDirection>()
    private val lastX = ConcurrentHashMap<UUID, Double>()
    private val lastY = ConcurrentHashMap<UUID, Double>()
    private val lastZ = ConcurrentHashMap<UUID, Double>()
    private val standStillTicks = ConcurrentHashMap<UUID, Int>()
    private val standStillTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val lastYaw = ConcurrentHashMap<UUID, Float>()
    private val lastPitch = ConcurrentHashMap<UUID, Float>()
    private val lookSameDirTicks = ConcurrentHashMap<UUID, Int>()
    private val lookSameDirTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val wasEnclosed = ConcurrentHashMap<UUID, Boolean>()
    private val wasSubmerged = ConcurrentHashMap<UUID, Boolean>()
    private val wasFloating = ConcurrentHashMap<UUID, Boolean>()
    private val wasBlockAboveHead = ConcurrentHashMap<UUID, Boolean>()
    private val wasNoBlockAboveHead = ConcurrentHashMap<UUID, Boolean>()
    private val deathTick = ConcurrentHashMap<UUID, Long>()
    private val notRespawn3sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val notRespawn5sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val notRespawn10sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val sprintStartTick = ConcurrentHashMap<UUID, Long>()
    private val sneakStartTick = ConcurrentHashMap<UUID, Long>()
    private val jumpCount = ConcurrentHashMap<UUID, Int>()
    private val sprint30sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val sneak5sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val jump10Triggered = ConcurrentHashMap<UUID, Boolean>()
    private val prevExperienceLevel = ConcurrentHashMap<UUID, Int>()
    private val prevExperienceProgress = ConcurrentHashMap<UUID, Float>()
    private val lastJumpTick = ConcurrentHashMap<UUID, Long>()
    private val lastSneakActionTick = ConcurrentHashMap<UUID, Long>()
    private val lastSprintActionTick = ConcurrentHashMap<UUID, Long>()
    private val noJump30sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noSneak30sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noSprint30sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noJump60sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noSneak60sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noSprint60sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val lastTriggeredTick = ConcurrentHashMap<UUID, Long>()
    private val interactingBlockIds = ConcurrentHashMap<UUID, String>()
    private val travelStatSampler = DDITravelStatSampler()

    private val travelStats = mapOf(
        DDISignalKind.DISTANCE_WALKED_CM to Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM),
        DDISignalKind.DISTANCE_SPRINTED_CM to Stats.CUSTOM.getOrCreateStat(Stats.SPRINT_ONE_CM),
        DDISignalKind.DISTANCE_SWUM_CM to Stats.CUSTOM.getOrCreateStat(Stats.SWIM_ONE_CM),
        DDISignalKind.DISTANCE_BOAT_CM to Stats.CUSTOM.getOrCreateStat(Stats.BOAT_ONE_CM),
    )
    private val travelLegacyAliases = mapOf(
        DDISignalKind.DISTANCE_WALKED_CM to DDITriggerType.WALK_DISTANCE,
        DDISignalKind.DISTANCE_SPRINTED_CM to DDITriggerType.SPRINT_DISTANCE,
        DDISignalKind.DISTANCE_SWUM_CM to DDITriggerType.SWIM_DISTANCE,
        DDISignalKind.DISTANCE_BOAT_CM to DDITriggerType.BOAT_DISTANCE,
    )

    // ---- 方块名称常量 ----
    private val stoneNames = setOf("stone", "cobblestone", "mossy_cobblestone")
    private val deepslateBlockNames = setOf("deepslate")
    private val mineSingleNames = linkedMapOf(
        "andesite" to DDITriggerType.MINE_ANDESITE,
        "diorite" to DDITriggerType.MINE_DIORITE,
        "granite" to DDITriggerType.MINE_GRANITE,
        "tuff" to DDITriggerType.MINE_TUFF,
        "crafting_table" to DDITriggerType.MINE_CRAFTING_TABLE,
        "furnace" to DDITriggerType.MINE_FURNACE,
    )
    private val standSingleNames = linkedMapOf(
        "andesite" to DDITriggerType.STAND_ON_ANDESITE,
        "diorite" to DDITriggerType.STAND_ON_DIORITE,
        "granite" to DDITriggerType.STAND_ON_GRANITE,
        "tuff" to DDITriggerType.STAND_ON_TUFF,
    )
    private val heldItemTriggers = mapOf(
        "crafting_table" to DDITriggerType.HOLD_CRAFTING_TABLE,
        "furnace" to DDITriggerType.HOLD_FURNACE,
        "wooden_pickaxe" to DDITriggerType.HOLD_WOODEN_PICKAXE,
        "iron_pickaxe" to DDITriggerType.HOLD_IRON_PICKAXE,
        "stone_pickaxe" to DDITriggerType.HOLD_STONE_PICKAXE,
        "wooden_axe" to DDITriggerType.HOLD_WOODEN_AXE,
        "stone_axe" to DDITriggerType.HOLD_STONE_AXE,
        "iron_axe" to DDITriggerType.HOLD_IRON_AXE,
    )
    private val inventoryItemTriggers = mapOf(
        "coal" to DDITriggerType.HAS_COAL,
        "iron_ingot" to DDITriggerType.HAS_IRON_INGOT,
        "copper_ingot" to DDITriggerType.HAS_COPPER_INGOT,
        "crafting_table" to DDITriggerType.HAS_CRAFTING_TABLE,
        "furnace" to DDITriggerType.HAS_FURNACE,
        "rotten_flesh" to DDITriggerType.HAS_ROTTEN_FLESH,
        "diamond" to DDITriggerType.HAS_DIAMOND,
        "dirt" to DDITriggerType.HAS_DIRT,
        "stone_pickaxe" to DDITriggerType.HAS_STONE_PICKAXE,
        "wooden_pickaxe" to DDITriggerType.HAS_WOODEN_PICKAXE,
        "iron_pickaxe" to DDITriggerType.HAS_IRON_PICKAXE,
        "bone" to DDITriggerType.HAS_BONE,
        "string" to DDITriggerType.HAS_STRING,
        "ender_pearl" to DDITriggerType.HAS_ENDER_PEARL,
        "leather" to DDITriggerType.HAS_LEATHER,
        "bucket" to DDITriggerType.HAS_BUCKET,
        "water_bucket" to DDITriggerType.HAS_WATER_BUCKET,
        "lava_bucket" to DDITriggerType.HAS_LAVA_BUCKET,
        "stone" to DDITriggerType.HAS_STONE,
        "smooth_stone" to DDITriggerType.HAS_SMOOTH_STONE,
        "tuff" to DDITriggerType.HAS_TUFF,
        "polished_andesite" to DDITriggerType.HAS_POLISHED_ANDESITE,
        "polished_granite" to DDITriggerType.HAS_POLISHED_GRANITE,
        "polished_diorite" to DDITriggerType.HAS_POLISHED_DIORITE,
    )
    private val equipmentSuffixes = setOf(
        "_sword", "_pickaxe", "_axe", "_shovel", "_hoe",
        "_helmet", "_chestplate", "_leggings", "_boots",
    )
    private val inventoryStateTriggers = inventoryItemTriggers.values.toSet() + setOf(
        DDITriggerType.HAS_AXE,
        DDITriggerType.HAS_SWORD,
        DDITriggerType.HAS_LEAVES,
        DDITriggerType.HAS_WOOL,
        DDITriggerType.NO_IRON_TOOLS_OR_ARMOR,
        DDITriggerType.NO_DIAMOND_TOOLS_OR_ARMOR,
        DDITriggerType.HOLD_SHIELD_OFFHAND,
    )
    private val standingBlockTriggers = setOf(
        DDITriggerType.STAND_ON_GRASS,
        DDITriggerType.STAND_ON_LEAVES,
        DDITriggerType.STAND_ON_STONE,
        DDITriggerType.STAND_ON_DEEPSLATE,
        DDITriggerType.STAND_ON_ANDESITE,
        DDITriggerType.STAND_ON_DIORITE,
        DDITriggerType.STAND_ON_GRANITE,
        DDITriggerType.STAND_ON_TUFF,
        DDITriggerType.STAND_ON_BEDROCK,
    )

    fun register() {
        clearAllState()
        activeDetectors[server] = this
        if (!callbacksRegistered.compareAndSet(false, true)) return

        AttackEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            val detector = if (player is ServerPlayerEntity && world is ServerWorld) world.server?.let(::detectorFor) else null
            if (detector != null && player is ServerPlayerEntity) {
                if (entity is LivingEntity && entity !is ServerPlayerEntity) {
                    detector.fire(player, DDITriggerType.ATTACK)
                }
                if (entity is ServerPlayerEntity) {
                    detector.fire(player, DDITriggerType.ATTACK_PLAYER)
                    if (player.mainHandStack.isEmpty) detector.fire(player, DDITriggerType.EMPTY_HAND_ATTACK)
                }
            }
            ActionResult.PASS
        }

        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, damageTaken, _ ->
            if (entity is ServerPlayerEntity && damageTaken > 0f) {
                DDISpecialEventHooks.reportPlayerDamaged(entity, source)
            }
            reportDamage(entity, source, damageTaken)
        }

        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            world.server?.let { DDISpecialEventHooks.reportEntityLoaded(it, entity) }
        }

        PlayerBlockBreakEvents.AFTER.register { world, player, pos, state, _ ->
            val detector = if (player is ServerPlayerEntity && world is ServerWorld) world.server?.let(::detectorFor) else null
            if (detector != null && player is ServerPlayerEntity) {
                val blockId = Registries.BLOCK.getId(state.block)
                if (blockId.namespace == Identifier.DEFAULT_NAMESPACE &&
                    (blockId.path == "diamond_ore" || blockId.path == "deepslate_diamond_ore")
                ) {
                    DDISpecialEventHooks.reportDiamondOreMined(player)
                }
                val aliases = linkedSetOf(DDITriggerType.BLOCK_BREAK)
                if (blockId.namespace == Identifier.DEFAULT_NAMESPACE) {
                    val id = blockId.path
                    if (id.endsWith("_ore") || id == "ancient_debris") {
                        aliases += DDITriggerType.MINE_ORE
                    }
                    aliases += detector.legacyMineAliases(id)
                }
                detector.emitBlockSignal(
                    player = player,
                    kind = DDISignalKind.BLOCK_BROKEN,
                    block = state.block,
                    legacyAliases = aliases,
                )
            }
        }

        ServerMessageEvents.CHAT_MESSAGE.register { _, sender, _ ->
            detectorFor(sender)?.fire(sender, DDITriggerType.CHAT)
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            val detector = entity.entityWorld.server?.let(::detectorFor)
            if (entity is ServerPlayerEntity) {
                detector?.fire(entity, DDITriggerType.DEATH)
                val id = entity.uuid
                if (source.isOf(DamageTypes.FALL)) detector?.fire(entity, DDITriggerType.DEATH_BY_FALL)
                if (source.isOf(DamageTypes.LAVA)) detector?.fire(entity, DDITriggerType.DEATH_BY_LAVA)
                if (source.isOf(DamageTypes.IN_WALL)) detector?.fire(entity, DDITriggerType.DEATH_BY_SUFFOCATION)
                if (source.isOf(DamageTypes.DROWN)) detector?.fire(entity, DDITriggerType.DEATH_BY_DROWN)
                if (source.isIn(DamageTypeTags.IS_EXPLOSION))
                    detector?.fire(entity, DDITriggerType.DEATH_BY_EXPLOSION)

                // 必须在每个死亡触发器之后执行。成功触发会重置每词条状态，
                // 若提前写入，则新分配的词条为 NOT_RESPAWN_* 时会清除该计时器。
                if (detector != null) detector.deathTick[id] = detector.server.ticks.toLong()
                detector?.notRespawn3sTriggered?.remove(id)
                detector?.notRespawn5sTriggered?.remove(id)
                detector?.notRespawn10sTriggered?.remove(id)
            }
            val attacker = source.attacker as? ServerPlayerEntity
            val attackerDetector = attacker?.let(::detectorFor)
            if (entity is IronGolemEntity && attacker != null)
                attackerDetector?.fire(attacker, DDITriggerType.KILL_IRON_GOLEM)
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            val detector = detectorFor(newPlayer)
            val id = newPlayer.uuid
            detector?.deathTick?.remove(id)
            detector?.notRespawn3sTriggered?.remove(id)
            detector?.notRespawn5sTriggered?.remove(id)
            detector?.notRespawn10sTriggered?.remove(id)
            detector?.fire(newPlayer, DDITriggerType.RESPAWN)
        }

        ServerTickEvents.END_SERVER_TICK.register { tickingServer ->
            detectorFor(tickingServer)?.onServerTick(tickingServer)
        }
    }

    fun unregister() {
        synchronized(activeDetectors) {
            if (activeDetectors[server] === this) activeDetectors.remove(server)
        }
        clearAllState()
    }

    private fun currentTrigger(player: ServerPlayerEntity): DDITriggerType? =
        currentTriggerHandler?.invoke(player.uuid)

    private fun currentRule(player: ServerPlayerEntity): DDIRuleDefinition? =
        currentRuleHandler?.invoke(player.uuid)

    private fun isActive(player: ServerPlayerEntity): Boolean =
        currentRule(player) != null

    private fun fire(player: ServerPlayerEntity, type: DDITriggerType) {
        emitSignal(player, DDISignal.legacy(type))
    }

    private fun emitSignal(player: ServerPlayerEntity, signal: DDISignal) {
        val rule = currentRule(player) ?: return
        if (!rule.matches(signal)) return
        val handler = onSignalHandler ?: return
        val tick = server.ticks.toLong()
        if (lastTriggeredTick[player.uuid] == tick) return
        if (handler(player, signal)) lastTriggeredTick[player.uuid] = tick
    }

    private fun emitItemSignal(
        player: ServerPlayerEntity,
        kind: DDISignalKind,
        item: Item,
        quantity: Int = 1,
        isBlockItem: Boolean = false,
        legacyAliases: Set<DDITriggerType> = emptySet(),
    ) {
        if (quantity <= 0) return
        emitSignal(
            player,
            DDISignal(
                kind = kind,
                subjectId = Registries.ITEM.getId(item).toString(),
                subjectTags = Registries.ITEM.getEntry(item).streamTags().iterator().asSequence()
                    .map { it.id.toString() }
                    .toSet(),
                quantity = quantity,
                isBlockItem = isBlockItem,
                legacyAliases = legacyAliases,
            ),
        )
    }

    private fun emitBlockSignal(
        player: ServerPlayerEntity,
        kind: DDISignalKind,
        block: Block,
        legacyAliases: Set<DDITriggerType> = emptySet(),
    ) {
        emitSignal(
            player,
            DDISignal(
                kind = kind,
                subjectId = Registries.BLOCK.getId(block).toString(),
                subjectTags = Registries.BLOCK.getEntry(block).streamTags().iterator().asSequence()
                    .map { it.id.toString() }
                    .toSet(),
                legacyAliases = legacyAliases,
            ),
        )
    }

    private fun onServerTick(server: MinecraftServer) {
        if (server !== this.server) return
        val activePlayerIds = activePlayerIdsHandler?.invoke() ?: return
        for (player in server.playerManager.playerList) {
            val id = player.uuid; val tick = server.ticks.toLong()
            if (id !in activePlayerIds) continue
            val currentRule = currentRule(player) ?: continue
            val currentTrigger = currentTrigger(player)
            // 动作回调可能已在同一服务端游戏刻稍早时更换该玩家的词条。
            // 不要用本刻剩余数据预热刚刚重置的边沿/进度映射。
            if (lastTriggeredTick[id] == tick) continue

            // 移动分类由原版负责，这些厘米统计会排除传送、重生和维度切换。
            // 移动规则没有其他匹配信号，因此采样其唯一相关统计后跳过旧版逐刻检查。
            val travelStat = travelStats[currentRule.signalKind]
            if (travelStat != null) {
                val travelledCentimetres = travelStatSampler.sample(
                    playerId = id,
                    signalKind = currentRule.signalKind,
                    currentCentimetres = player.statHandler.getStat(travelStat),
                )
                if (travelledCentimetres > 0) {
                    emitSignal(
                        player,
                        DDISignal(
                            kind = currentRule.signalKind,
                            quantity = travelledCentimetres,
                            legacyAliases = setOfNotNull(travelLegacyAliases[currentRule.signalKind]),
                        ),
                    )
                }
                continue
            }

            val sneaking = player.isSneaking
            val prevSneak = wasSneaking.put(id, sneaking)
            if (sneaking && (prevSneak == null || !prevSneak)) fire(player, DDITriggerType.SNEAK)
            if (lastTriggeredTick[id] == tick) continue

            val sprinting = player.isSprinting
            val prevSprint = wasSprinting.put(id, sprinting)
            if (sprinting && (prevSprint == null || !prevSprint)) fire(player, DDITriggerType.SPRINT)
            if (lastTriggeredTick[id] == tick) continue

            // 注视方向
            val lookingDown = player.pitch > 60
            val previousLookingDown = wasLookingDown.put(id, lookingDown)
            if (lookingDown && previousLookingDown != true) fire(player, DDITriggerType.LOOK_DOWN)
            val lookingUp = player.pitch < -60
            val previousLookingUp = wasLookingUp.put(id, lookingUp)
            if (lookingUp && previousLookingUp != true) fire(player, DDITriggerType.LOOK_UP)

            val yaw = ((player.yaw % 360) + 360) % 360
            val direction = when {
                yaw < 45f || yaw >= 315f -> CardinalDirection.SOUTH
                yaw < 135f -> CardinalDirection.WEST
                yaw < 225f -> CardinalDirection.NORTH
                else -> CardinalDirection.EAST
            }
            if (lastCardinalDirection.put(id, direction) != direction) {
                val trigger = when (direction) {
                    CardinalDirection.SOUTH -> DDITriggerType.LOOK_SOUTH
                    CardinalDirection.WEST -> DDITriggerType.LOOK_WEST
                    CardinalDirection.NORTH -> DDITriggerType.LOOK_NORTH
                    CardinalDirection.EAST -> DDITriggerType.LOOK_EAST
                }
                fire(player, trigger)
            }
            if (lastTriggeredTick[id] == tick) continue

            // 保持静止：以同一锚点比较完整的五秒窗口。
            // 若比较相邻游戏刻，缓慢行走会被无限期误判为保持静止。
            if (currentTrigger == DDITriggerType.STAND_STILL_5S) {
                val cx = player.x; val cy = player.y; val cz = player.z
                val ax = lastX[id]; val ay = lastY[id]; val az = lastZ[id]
                if (ax == null || ay == null || az == null) {
                    lastX[id] = cx; lastY[id] = cy; lastZ[id] = cz
                    standStillTicks[id] = 0
                } else if (DDITriggerRules.isWithinStationaryAnchor(ax, ay, az, cx, cy, cz)) {
                    val t = standStillTicks.getOrDefault(id, 0) + 1
                    standStillTicks[id] = t
                    if (t >= 100 && standStillTriggered.getOrDefault(id, false).not()) {
                        standStillTriggered[id] = true
                        fire(player, DDITriggerType.STAND_STILL_5S)
                    }
                } else {
                    lastX[id] = cx; lastY[id] = cy; lastZ[id] = cz
                    standStillTicks[id] = 0
                    standStillTriggered[id] = false
                }
            }
            if (lastTriggeredTick[id] == tick) continue

            // 注视检测同理：以窗口起点为锚点，防止玩家每刻旋转几度来规避检测。
            if (currentTrigger == DDITriggerType.LOOK_SAME_DIR_5S) {
                val cyaw = player.yaw; val cp = player.pitch
                val anchorYaw = lastYaw[id]; val anchorPitch = lastPitch[id]
                if (anchorYaw == null || anchorPitch == null) {
                    lastYaw[id] = cyaw; lastPitch[id] = cp
                    lookSameDirTicks[id] = 0
                } else if (DDITriggerRules.isWithinLookAnchor(anchorYaw, anchorPitch, cyaw, cp)) {
                    val t = lookSameDirTicks.getOrDefault(id, 0) + 1
                    lookSameDirTicks[id] = t
                    if (t >= 100 && lookSameDirTriggered.getOrDefault(id, false).not()) {
                        lookSameDirTriggered[id] = true
                        fire(player, DDITriggerType.LOOK_SAME_DIR_5S)
                    }
                } else {
                    lastYaw[id] = cyaw; lastPitch[id] = cp
                    lookSameDirTicks[id] = 0
                    lookSameDirTriggered[id] = false
                }
            }
            if (lastTriggeredTick[id] == tick) continue

            // 密闭状态
            if (currentTrigger == DDITriggerType.ENCLOSED_1X2) {
                val enclosed = isPlayerEnclosed(player)
                val previousEnclosed = wasEnclosed.put(id, enclosed)
                if (enclosed && previousEnclosed != true) fire(player, DDITriggerType.ENCLOSED_1X2)
            }
            if (lastTriggeredTick[id] == tick) continue

            // 浸没状态
            if (currentTrigger == DDITriggerType.SUBMERGED) {
                val submerged = player.isSubmergedInWater
                val previousSubmerged = wasSubmerged.put(id, submerged)
                if (submerged && previousSubmerged != true) fire(player, DDITriggerType.SUBMERGED)
            }
            if (lastTriggeredTick[id] == tick) continue

            // 漂浮状态
            if (currentTrigger == DDITriggerType.FLOATING) {
                val floating = player.entityWorld.getBlockState(player.blockPos.down()).isAir
                val previousFloating = wasFloating.put(id, floating)
                if (floating && previousFloating != true) fire(player, DDITriggerType.FLOATING)
            }
            if (lastTriggeredTick[id] == tick) continue

            // 使用原版选定的碰撞支撑方块。blockPos.down() 会错误判断台阶、楼梯、
            // 床、地毯和方块边界。
            if ((currentRule.signalKind == DDISignalKind.BLOCK_STOOD_ON ||
                    currentTrigger in standingBlockTriggers) &&
                player.isOnGround && !player.hasVehicle()
            ) {
                val steppingBlock = player.steppingBlockState.block
                val steppingId = Registries.BLOCK.getId(steppingBlock)
                val aliases = if (steppingId.namespace == Identifier.DEFAULT_NAMESPACE) {
                    legacyStandingAliases(steppingId.path)
                } else {
                    emptySet()
                }
                emitBlockSignal(
                    player = player,
                    kind = DDISignalKind.BLOCK_STOOD_ON,
                    block = steppingBlock,
                    legacyAliases = aliases,
                )
            }
            if (lastTriggeredTick[id] == tick) continue

            // 头顶方块
            if (currentTrigger == DDITriggerType.BLOCK_ABOVE_HEAD ||
                currentTrigger == DDITriggerType.NO_BLOCK_ABOVE_HEAD
            ) {
                val hasAbove = hasBlockAboveHead(player)
                val previousHasAbove = wasBlockAboveHead.put(id, hasAbove)
                val noBlockAbove = !hasAbove
                val previousNoBlockAbove = wasNoBlockAboveHead.put(id, noBlockAbove)
                if (hasAbove && previousHasAbove != true) fire(player, DDITriggerType.BLOCK_ABOVE_HEAD)
                if (noBlockAbove && previousNoBlockAbove != true) fire(player, DDITriggerType.NO_BLOCK_ABOVE_HEAD)
            }
            if (lastTriggeredTick[id] == tick) continue

            // 死亡计时器
            val dt = deathTick[id]
            if (dt != null && player.isDead) {
                val elapsed = server.ticks - dt
                if (elapsed >= 60 && notRespawn3sTriggered.getOrDefault(id, false).not()) { notRespawn3sTriggered[id] = true; fire(player, DDITriggerType.NOT_RESPAWN_3S) }
                if (elapsed >= 100 && notRespawn5sTriggered.getOrDefault(id, false).not()) { notRespawn5sTriggered[id] = true; fire(player, DDITriggerType.NOT_RESPAWN_5S) }
                if (elapsed >= 200 && notRespawn10sTriggered.getOrDefault(id, false).not()) { notRespawn10sTriggered[id] = true; fire(player, DDITriggerType.NOT_RESPAWN_10S) }
            }
            if (lastTriggeredTick[id] == tick) continue

            // 饥饿值/高度
            val hunger = player.hungerManager.foodLevel
            if (hunger < 18) fire(player, DDITriggerType.HUNGER_BELOW_18)
            if (hunger > 18) fire(player, DDITriggerType.HUNGER_ABOVE_18)
            if (player.y > 70) fire(player, DDITriggerType.Y_ABOVE_70)
            if (player.y < 70) fire(player, DDITriggerType.Y_BELOW_70)
            if (lastTriggeredTick[id] == tick) continue

            // 距离
            if (currentTrigger == DDITriggerType.FAR_FROM_ALL_15M ||
                currentTrigger == DDITriggerType.TOO_CLOSE_TO_PLAYER
            ) {
                val opponents = server.playerManager.playerList.filter {
                    it.uuid != id && it.uuid in activePlayerIds
                }
                if (opponents.isNotEmpty()) {
                    val sameWorldOpponents = opponents.filter { it.entityWorld === player.entityWorld }
                    val minDist = sameWorldOpponents.minOfOrNull(player::squaredDistanceTo)
                    if (minDist == null || minDist > 225) fire(player, DDITriggerType.FAR_FROM_ALL_15M)
                    if (minDist != null && minDist < 4) fire(player, DDITriggerType.TOO_CLOSE_TO_PLAYER)
                }
            }
            if (lastTriggeredTick[id] == tick) continue

            // 持续行为
            if (sprinting) { sprintStartTick.putIfAbsent(id, tick); if ((tick - sprintStartTick[id]!!) / 20 >= 30 && sprint30sTriggered.getOrDefault(id, false).not()) { sprint30sTriggered[id] = true; fire(player, DDITriggerType.SPRINT_30S) } }
            else { sprintStartTick.remove(id); sprint30sTriggered.remove(id) }
            if (sneaking) { sneakStartTick.putIfAbsent(id, tick); if ((tick - sneakStartTick[id]!!) / 20 >= 5 && sneak5sTriggered.getOrDefault(id, false).not()) { sneak5sTriggered[id] = true; fire(player, DDITriggerType.SNEAK_5S) } }
            else { sneakStartTick.remove(id); sneak5sTriggered.remove(id) }
            if (lastTriggeredTick[id] == tick) continue

            // 经验值
            val cl = player.experienceLevel; val cpr = player.experienceProgress
            val pl = prevExperienceLevel.put(id, cl)
            val previousProgress = prevExperienceProgress.put(id, cpr)
            if (pl != null && cl > pl) fire(player, DDITriggerType.LEVEL_UP)
            if (previousProgress != null && (cpr > previousProgress || cl > (pl ?: cl)))
                fire(player, DDITriggerType.GAIN_EXPERIENCE)
            if (lastTriggeredTick[id] == tick) continue

            // 盔甲
            if (currentTrigger == DDITriggerType.WEAR_ARMOR) {
                val armorSlots = arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
                if (armorSlots.any { !player.getEquippedStack(it).isEmpty }) fire(player, DDITriggerType.WEAR_ARMOR)
            }
            if (lastTriggeredTick[id] == tick) continue

            // 手持物品（既定语义为任意一只手）
            if (currentRule.signalKind == DDISignalKind.ITEM_HELD ||
                currentTrigger in heldItemTriggers.values
            ) {
                sequenceOf(player.mainHandStack, player.offHandStack)
                    .filterNot { it.isEmpty }
                    .forEach { stack ->
                        val itemId = Registries.ITEM.getId(stack.item)
                        val alias = if (itemId.namespace == Identifier.DEFAULT_NAMESPACE) {
                            heldItemTriggers[itemId.path]
                        } else {
                            null
                        }
                        emitItemSignal(
                            player = player,
                            kind = DDISignalKind.ITEM_HELD,
                            item = stack.item,
                            legacyAliases = setOfNotNull(alias),
                        )
                    }
            }
            if (lastTriggeredTick[id] == tick) continue

            // 快捷栏
            if (player.inventory.selectedSlot == 0) fire(player, DDITriggerType.SELECT_SLOT_FIRST)
            if (player.inventory.selectedSlot == 8) fire(player, DDITriggerType.SELECT_SLOT_LAST)
            if (lastTriggeredTick[id] == tick) continue

            // 原版 fallDistance 记录从腾空最高点开始的距离，
            // 可避免将走下边缘错误判断为跳跃。
            if (player.fallDistance >= 5.0) fire(player, DDITriggerType.FALL_5_BLOCKS)
            if (lastTriggeredTick[id] == tick) continue

            // 物品栏
            if (currentTrigger in inventoryStateTriggers) {
                val inventoryIds = buildSet {
                    for (slot in 0 until player.inventory.size()) {
                        val stack = player.inventory.getStack(slot)
                        if (!stack.isEmpty) add(Registries.ITEM.getId(stack.item))
                    }
                }
                val vanillaInventoryPaths = inventoryIds
                    .filter { it.namespace == Identifier.DEFAULT_NAMESPACE }
                    .mapTo(mutableSetOf()) { it.path }
                val allInventoryPaths = inventoryIds.mapTo(mutableSetOf()) { it.path }
                for ((itemId, type) in inventoryItemTriggers) {
                    if (itemId in vanillaInventoryPaths) fire(player, type)
                }
                if (allInventoryPaths.any { it.endsWith("_axe") }) fire(player, DDITriggerType.HAS_AXE)
                if (allInventoryPaths.any { it.endsWith("_sword") }) fire(player, DDITriggerType.HAS_SWORD)
                if (allInventoryPaths.any { it.endsWith("_leaves") || it == "leaves" }) fire(player, DDITriggerType.HAS_LEAVES)
                if (allInventoryPaths.any { it.endsWith("_wool") }) fire(player, DDITriggerType.HAS_WOOL)
                if (allInventoryPaths.none { it.startsWith("iron_") && equipmentSuffixes.any(it::endsWith) })
                    fire(player, DDITriggerType.NO_IRON_TOOLS_OR_ARMOR)
                if (allInventoryPaths.none { it.startsWith("diamond_") && equipmentSuffixes.any(it::endsWith) })
                    fire(player, DDITriggerType.NO_DIAMOND_TOOLS_OR_ARMOR)
                val offId = Registries.ITEM.getId(player.offHandStack.item)
                if (offId.namespace == Identifier.DEFAULT_NAMESPACE && offId.path == "shield") {
                    fire(player, DDITriggerType.HOLD_SHIELD_OFFHAND)
                }
            }
            if (lastTriggeredTick[id] == tick) continue

            // 无动作计时器
            lastJumpTick.putIfAbsent(id, tick); lastSneakActionTick.putIfAbsent(id, tick); lastSprintActionTick.putIfAbsent(id, tick)
            // 这些词条描述持续“不做”该动作，因此持续潜行/疾跑时必须每刻重置计时器。
            if (sneaking) lastSneakActionTick[id] = tick
            if (sprinting) lastSprintActionTick[id] = tick
            val sj = (tick - lastJumpTick[id]!!) / 20; val ss = (tick - lastSneakActionTick[id]!!) / 20; val sp = (tick - lastSprintActionTick[id]!!) / 20
            if (sj >= 30 && noJump30sTriggered.getOrDefault(id, false).not()) { noJump30sTriggered[id] = true; fire(player, DDITriggerType.NO_JUMP_30S) }
            if (ss >= 30 && noSneak30sTriggered.getOrDefault(id, false).not()) { noSneak30sTriggered[id] = true; fire(player, DDITriggerType.NO_SNEAK_30S) }
            if (sp >= 30 && noSprint30sTriggered.getOrDefault(id, false).not()) { noSprint30sTriggered[id] = true; fire(player, DDITriggerType.NO_SPRINT_30S) }
            if (sj >= 60 && noJump60sTriggered.getOrDefault(id, false).not()) { noJump60sTriggered[id] = true; fire(player, DDITriggerType.NO_JUMP_60S) }
            if (ss >= 60 && noSneak60sTriggered.getOrDefault(id, false).not()) { noSneak60sTriggered[id] = true; fire(player, DDITriggerType.NO_SNEAK_60S) }
            if (sp >= 60 && noSprint60sTriggered.getOrDefault(id, false).not()) { noSprint60sTriggered[id] = true; fire(player, DDITriggerType.NO_SPRINT_60S) }
        }

        if (server.ticks % 100 == 0) {
            cleanupMaps(server.playerManager.playerList.map { it.uuid }.toSet())
        }
    }

    private fun legacyMineAliases(id: String): Set<DDITriggerType> = buildSet {
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") ||
            id.endsWith("_hyphae") || id == "bamboo_block"
        ) add(DDITriggerType.MINE_WOOD)
        if (id in stoneNames) add(DDITriggerType.MINE_STONE)
        if (id in deepslateBlockNames) add(DDITriggerType.MINE_DEEPSLATE)
        if (id.contains("coal_ore")) add(DDITriggerType.MINE_COAL)
        if (id.contains("iron_ore")) add(DDITriggerType.MINE_IRON)
        if (id.contains("copper_ore")) add(DDITriggerType.MINE_COPPER)
        if (id.contains("gold_ore")) add(DDITriggerType.MINE_GOLD)
        if (id.contains("diamond_ore")) add(DDITriggerType.MINE_DIAMOND)
        mineSingleNames[id]?.let(::add)
    }

    private fun legacyStandingAliases(id: String): Set<DDITriggerType> = buildSet {
        if (id == "grass_block") add(DDITriggerType.STAND_ON_GRASS)
        if (id.endsWith("_leaves")) add(DDITriggerType.STAND_ON_LEAVES)
        if (id in stoneNames) add(DDITriggerType.STAND_ON_STONE)
        if (id in deepslateBlockNames) add(DDITriggerType.STAND_ON_DEEPSLATE)
        if (id == "bedrock") add(DDITriggerType.STAND_ON_BEDROCK)
        standSingleNames[id]?.let(::add)
    }

    private fun detectOpenContainer(player: ServerPlayerEntity, blockId: String) {
        when {
            blockId in setOf("chest", "trapped_chest", "ender_chest") ->
                fire(player, DDITriggerType.OPEN_CHEST)
            blockId in setOf("furnace", "blast_furnace", "smoker") -> fire(player, DDITriggerType.OPEN_FURNACE)
            blockId == "crafting_table" -> fire(player, DDITriggerType.OPEN_CRAFTING_TABLE)
        }
    }

    private fun legacyPlaceAlias(itemId: String): DDITriggerType? {
        val map = mapOf(
            "dirt" to DDITriggerType.PLACE_DIRT, "cobblestone" to DDITriggerType.PLACE_COBBLESTONE,
            "cobbled_deepslate" to DDITriggerType.PLACE_COBBLED_DEEPSLATE, "andesite" to DDITriggerType.PLACE_ANDESITE,
            "granite" to DDITriggerType.PLACE_GRANITE, "diorite" to DDITriggerType.PLACE_DIORITE,
            "tuff" to DDITriggerType.PLACE_TUFF, "crafting_table" to DDITriggerType.PLACE_CRAFTING_TABLE,
            "furnace" to DDITriggerType.PLACE_FURNACE, "chest" to DDITriggerType.PLACE_CHEST,
        )
        return map[itemId]
    }

    private fun isPlayerEnclosed(player: ServerPlayerEntity): Boolean {
        val world = player.entityWorld
        val feet = player.blockPos; val head = feet.up()
        if (!world.getBlockState(feet.down()).isSolidBlock(world, feet.down())) return false
        if (!world.getBlockState(head.up()).isSolidBlock(world, head.up())) return false
        val dirs = arrayOf(feet.east(), feet.west(), feet.south(), feet.north(), head.east(), head.west(), head.south(), head.north())
        return dirs.all { world.getBlockState(it).isSolidBlock(world, it) }
    }

    private fun hasBlockAboveHead(player: ServerPlayerEntity): Boolean {
        val world = player.entityWorld
        val surfaceY = world.getTopY(
            Heightmap.Type.WORLD_SURFACE,
            player.blockPos.x,
            player.blockPos.z,
        )
        return surfaceY > player.blockPos.y + 2
    }

    private fun isFireDamage(source: DamageSource): Boolean =
        source.isIn(DamageTypeTags.IS_FIRE)

    fun resetJumpCount(id: UUID) { jumpCount.remove(id); jump10Triggered.remove(id) }
    fun resetLookSameDir(id: UUID) { lastYaw.remove(id); lastPitch.remove(id); lookSameDirTicks.remove(id); lookSameDirTriggered.remove(id) }
    fun resetNoJumpState(id: UUID) { lastJumpTick.remove(id); noJump30sTriggered.remove(id); noJump60sTriggered.remove(id) }
    fun resetNoSneakState(id: UUID) { lastSneakActionTick.remove(id); noSneak30sTriggered.remove(id); noSneak60sTriggered.remove(id) }
    fun resetNoSprintState(id: UUID) { lastSprintActionTick.remove(id); noSprint30sTriggered.remove(id); noSprint60sTriggered.remove(id) }
    fun resetBlockAboveHeadState(id: UUID) { wasBlockAboveHead.remove(id); wasNoBlockAboveHead.remove(id) }

    fun resetPlayerState(id: UUID, preserveDeathTimer: Boolean = false) {
        val savedDeathTick = deathTick[id]
        stateMaps().forEach { it.remove(id) }
        travelStatSampler.reset(id)
        if (preserveDeathTimer) {
            deathTick[id] = savedDeathTick ?: server.ticks.toLong()
        }
    }

    fun clearAllState() {
        stateMaps().forEach { it.clear() }
        lastTriggeredTick.clear()
        interactingBlockIds.clear()
        travelStatSampler.clear()
    }

    private fun stateMaps(): List<MutableMap<UUID, *>> = buildList {
        addAll(listOf(
            wasSneaking, wasSprinting, wasLookingDown, wasLookingUp,
            lastCardinalDirection, lastX, lastY, lastZ,
            standStillTicks, standStillTriggered, lastYaw, lastPitch, lookSameDirTicks, lookSameDirTriggered,
            wasEnclosed, wasSubmerged, wasFloating, wasBlockAboveHead, wasNoBlockAboveHead,
            deathTick, notRespawn3sTriggered, notRespawn5sTriggered, notRespawn10sTriggered,
            sprintStartTick, sneakStartTick, jumpCount, sprint30sTriggered, sneak5sTriggered, jump10Triggered,
            prevExperienceLevel, prevExperienceProgress,
            lastJumpTick, lastSneakActionTick, lastSprintActionTick,
            noJump30sTriggered, noSneak30sTriggered, noSprint30sTriggered, noJump60sTriggered, noSneak60sTriggered, noSprint60sTriggered,
        ))
    }

    private fun cleanupMaps(online: Set<UUID>) {
        stateMaps().forEach { it.keys.retainAll(online) }
        lastTriggeredTick.keys.retainAll(online)
        interactingBlockIds.keys.retainAll(online)
        travelStatSampler.retainPlayers(online)
    }
}
