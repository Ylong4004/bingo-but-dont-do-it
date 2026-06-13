package me.jfenn.bingo.integrations.ddi

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.item.BlockItem
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * DDI 词条触发检测器。
 * 类实例模式，回调注入。注册到 Fabric API 事件中。
 */
class DDITriggerDetector(
    private val server: MinecraftServer,
) {

    var onTriggeredHandler: ((ServerPlayerEntity, DDITriggerType) -> Unit)? = null

    private val wasSneaking = ConcurrentHashMap<UUID, Boolean>()
    private val wasSprinting = ConcurrentHashMap<UUID, Boolean>()
    private val wasUsingFood = ConcurrentHashMap<UUID, Boolean>()
    private val lastEatenFoodId = ConcurrentHashMap<UUID, String>()
    private val wasLookingDown = ConcurrentHashMap<UUID, Boolean>()
    private val wasLookingUp = ConcurrentHashMap<UUID, Boolean>()
    private val wasLookingEast = ConcurrentHashMap<UUID, Boolean>()
    private val wasLookingSouth = ConcurrentHashMap<UUID, Boolean>()
    private val wasLookingWest = ConcurrentHashMap<UUID, Boolean>()
    private val wasLookingNorth = ConcurrentHashMap<UUID, Boolean>()
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
    private val onBlockMaps = (0..9).map { ConcurrentHashMap<UUID, Boolean>() }
    private val wasOnBedrock = ConcurrentHashMap<UUID, Boolean>()
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
    private val wasOnGround = ConcurrentHashMap<UUID, Boolean>()
    private val fallStartY = ConcurrentHashMap<UUID, Double>()
    private val fallTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val placeCount = ConcurrentHashMap<UUID, Int>()
    private val place30Triggered = ConcurrentHashMap<UUID, Boolean>()
    private val dropCount = ConcurrentHashMap<UUID, Int>()
    private val drop30Triggered = ConcurrentHashMap<UUID, Boolean>()
    private val lastJumpTick = ConcurrentHashMap<UUID, Long>()
    private val lastSneakActionTick = ConcurrentHashMap<UUID, Long>()
    private val lastSprintActionTick = ConcurrentHashMap<UUID, Long>()
    private val noJump30sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noSneak30sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noSprint30sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noJump60sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noSneak60sTriggered = ConcurrentHashMap<UUID, Boolean>()
    private val noSprint60sTriggered = ConcurrentHashMap<UUID, Boolean>()

    private var registered = false

    // ---- block name constants ----
    private val grassBlocks = setOf("grass_block")
    private val leafEndings = setOf("_leaves")
    private val stoneNames = setOf("stone", "cobblestone", "mossy_cobblestone")
    private val deepslateBlockNames = setOf("deepslate")
    private val singleNames = linkedMapOf(
        "andesite" to DDITriggerType.MINE_ANDESITE,
        "diorite" to DDITriggerType.MINE_DIORITE,
        "granite" to DDITriggerType.MINE_GRANITE,
        "tuff" to DDITriggerType.MINE_TUFF,
        "crafting_table" to DDITriggerType.MINE_CRAFTING_TABLE,
        "furnace" to DDITriggerType.MINE_FURNACE,
    )

    fun register() {
        if (registered) return
        registered = true

        AttackEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (player is ServerPlayerEntity && entity != null && world is ServerWorld) {
                if (entity !is ServerPlayerEntity) fire(player, DDITriggerType.ATTACK)
                if (entity is ServerPlayerEntity) {
                    fire(player, DDITriggerType.ATTACK_PLAYER)
                    if (player.mainHandStack.isEmpty) fire(player, DDITriggerType.EMPTY_HAND_ATTACK)
                }
            }
            ActionResult.PASS
        }

        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, amount, _, _ ->
            if (amount > 0) {
                if (entity is HostileEntity && source.attacker is ServerPlayerEntity)
                    fire(source.attacker as ServerPlayerEntity, DDITriggerType.ATTACK_HOSTILE)
                if (source.attacker is ServerPlayerEntity)
                    fire(source.attacker as ServerPlayerEntity, DDITriggerType.DEAL_DAMAGE)
            }
        }

        PlayerBlockBreakEvents.AFTER.register { world, player, pos, state, _ ->
            if (player is ServerPlayerEntity && world is ServerWorld) {
                fire(player, DDITriggerType.BLOCK_BREAK)
                val id = Registries.BLOCK.getId(state.block).path
                if (id.contains("_ore") || id == "ancient_debris") fire(player, DDITriggerType.MINE_ORE)
                detectMineBlock(player, id)
            }
        }

        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (player is ServerPlayerEntity && world is ServerWorld) {
                val blockId = Registries.BLOCK.getId(world.getBlockState(hitResult.blockPos).block).path
                detectOpenContainer(player, blockId)
                val stack = player.getStackInHand(hand)
                if (stack.item is BlockItem) {
                    fire(player, DDITriggerType.BLOCK_PLACE)
                    val id = player.uuid
                    val cnt = placeCount.getOrDefault(id, 0) + 1; placeCount[id] = cnt
                    if (cnt >= 30 && place30Triggered.getOrDefault(id, false).not()) {
                        place30Triggered[id] = true; fire(player, DDITriggerType.PLACE_30_BLOCKS)
                    }
                    detectPlaceBlock(player, Registries.ITEM.getId(stack.item).path)
                }
            }
            ActionResult.PASS
        }

        UseItemCallback.EVENT.register { player, world, hand ->
            if (player is ServerPlayerEntity && world is ServerWorld) {
                val itemId = Registries.ITEM.getId(player.getStackInHand(hand).item).path
                when (itemId) {
                    "water_bucket" -> fire(player, DDITriggerType.EMPTY_BUCKET_WATER)
                    "lava_bucket" -> fire(player, DDITriggerType.EMPTY_BUCKET_LAVA)
                    "bucket" -> {
                        val hit = player.raycast(5.0, 0.0f, true)
                        if (hit.type == HitResult.Type.BLOCK) {
                            val blockHit = hit as BlockHitResult
                            val targetId = Registries.BLOCK.getId(world.getBlockState(blockHit.blockPos).block).path
                            if (targetId == "water") fire(player, DDITriggerType.FILL_BUCKET_WATER)
                            if (targetId == "lava") fire(player, DDITriggerType.FILL_BUCKET_LAVA)
                        }
                    }
                }
            }
            ActionResult.PASS
        }

        ServerMessageEvents.CHAT_MESSAGE.register { _, sender, _ ->
            if (sender is ServerPlayerEntity) fire(sender, DDITriggerType.CHAT)
        }

        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, amount, _, _ ->
            if (entity is ServerPlayerEntity && amount > 0) {
                fire(entity, DDITriggerType.TAKE_DAMAGE)
                if (isFireDamage(source)) fire(entity, DDITriggerType.TAKE_FIRE_DAMAGE)
                if (source.isIn(DamageTypeTags.IS_PROJECTILE)) fire(entity, DDITriggerType.TAKE_PROJECTILE_DAMAGE)
                if (amount >= 5) fire(entity, DDITriggerType.TAKE_5_DAMAGE)
            }
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            if (entity is ServerPlayerEntity) {
                fire(entity, DDITriggerType.DEATH)
                val id = entity.uuid; deathTick[id] = server.ticks.toLong()
                notRespawn3sTriggered.remove(id); notRespawn5sTriggered.remove(id); notRespawn10sTriggered.remove(id)
                if (source.isOf(DamageTypes.FALL)) fire(entity, DDITriggerType.DEATH_BY_FALL)
                if (source.isOf(DamageTypes.LAVA)) fire(entity, DDITriggerType.DEATH_BY_LAVA)
                if (source.isOf(DamageTypes.IN_WALL)) fire(entity, DDITriggerType.DEATH_BY_SUFFOCATION)
                if (source.isOf(DamageTypes.DROWN)) fire(entity, DDITriggerType.DEATH_BY_DROWN)
                if (source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.PLAYER_EXPLOSION))
                    fire(entity, DDITriggerType.DEATH_BY_EXPLOSION)
            }
            if (entity is IronGolemEntity && source.attacker is ServerPlayerEntity)
                fire(source.attacker as ServerPlayerEntity, DDITriggerType.KILL_IRON_GOLEM)
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            val id = newPlayer.uuid; deathTick.remove(id)
            notRespawn3sTriggered.remove(id); notRespawn5sTriggered.remove(id); notRespawn10sTriggered.remove(id)
            fire(newPlayer, DDITriggerType.RESPAWN)
        }

        ServerTickEvents.END_SERVER_TICK.register { server -> onServerTick(server) }
    }

    fun unregister() { clearAllState(); registered = false }

    private fun fire(player: ServerPlayerEntity, type: DDITriggerType) {
        onTriggeredHandler?.invoke(player, type)
    }

    private fun onServerTick(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            val id = player.uuid; val tick = server.ticks.toLong()

            val sneaking = player.isSneaking
            val prevSneak = wasSneaking.put(id, sneaking)
            if (sneaking && (prevSneak == null || !prevSneak)) fire(player, DDITriggerType.SNEAK)

            val sprinting = player.isSprinting
            val prevSprint = wasSprinting.put(id, sprinting)
            if (sprinting && (prevSprint == null || !prevSprint)) fire(player, DDITriggerType.SPRINT)

            val usingFood = player.isUsingItem && player.activeItem.contains(DataComponentTypes.FOOD)
            if (usingFood) lastEatenFoodId[id] = Registries.ITEM.getId(player.activeItem.item).path
            val prevUsingFood = wasUsingFood.put(id, usingFood)
            if (!usingFood && prevUsingFood != null && prevUsingFood) {
                fire(player, DDITriggerType.EAT)
                if (lastEatenFoodId[id] == "rotten_flesh") fire(player, DDITriggerType.EAT_ROTTEN_FLESH)
            }

            // look direction
            val lookingDown = player.pitch > 60
            if (lookingDown && wasLookingDown.put(id, lookingDown) != true) fire(player, DDITriggerType.LOOK_DOWN)
            val lookingUp = player.pitch < -60
            if (lookingUp && wasLookingUp.put(id, lookingUp) != true) fire(player, DDITriggerType.LOOK_UP)

            val yaw = ((player.yaw % 360) + 360) % 360
            if (yaw in 225f..315f && wasLookingWest.put(id, true) != true) fire(player, DDITriggerType.LOOK_WEST)
            // ... simplified direction checks
            val lookingSouth = yaw > 315 || yaw < 45
            if (lookingSouth && wasLookingSouth.put(id, true) != true) fire(player, DDITriggerType.LOOK_SOUTH)
            val lookingWest = yaw in 45f..135f
            if (lookingWest && wasLookingWest.put(id, true) != true) fire(player, DDITriggerType.LOOK_WEST)
            val lookingNorth: Boolean = yaw in 135f..225f
            if (lookingNorth && wasLookingNorth.put(id, true) != true) fire(player, DDITriggerType.LOOK_NORTH)
            val lookingEast: Boolean = yaw in 225f..315f
            if (lookingEast && wasLookingEast.put(id, true) != true) fire(player, DDITriggerType.LOOK_EAST)

            // stand still
            val cx = player.x; val cy = player.y; val cz = player.z
            val px = lastX.put(id, cx); val py = lastY.put(id, cy); val pz = lastZ.put(id, cz)
            if (px != null) {
                val still = kotlin.math.abs(cx - px) < 0.1 && kotlin.math.abs(cy - py) < 0.1 && kotlin.math.abs(cz - pz) < 0.1
                if (still) {
                    val t = standStillTicks.getOrDefault(id, 0) + 1; standStillTicks[id] = t
                    if (t >= 100 && standStillTriggered.getOrDefault(id, false).not()) { standStillTriggered[id] = true; fire(player, DDITriggerType.STAND_STILL_5S) }
                } else { standStillTicks[id] = 0; standStillTriggered[id] = false }
            }

            // look same dir
            val cyaw = player.yaw; val cp = player.pitch
            val pyaw = lastYaw.put(id, cyaw); val pp = lastPitch.put(id, cp)
            if (pyaw != null) {
                var diff = kotlin.math.abs(cyaw - pyaw); if (diff > 180) diff = 360 - diff
                if (diff < 10 && kotlin.math.abs(cp - pp) < 10) {
                    val t = lookSameDirTicks.getOrDefault(id, 0) + 1; lookSameDirTicks[id] = t
                    if (t >= 100 && lookSameDirTriggered.getOrDefault(id, false).not()) { lookSameDirTriggered[id] = true; fire(player, DDITriggerType.LOOK_SAME_DIR_5S) }
                } else { lookSameDirTicks[id] = 0; lookSameDirTriggered[id] = false }
            }

            // enclosed
            val enclosed = isPlayerEnclosed(player)
            if (enclosed && wasEnclosed.put(id, true) != true) fire(player, DDITriggerType.ENCLOSED_1X2)

            // submerged
            val submerged = player.isSubmergedInWater
            if (submerged && wasSubmerged.put(id, true) != true) fire(player, DDITriggerType.SUBMERGED)

            // floating
            val floating = player.getServerWorld().getBlockState(player.blockPos.down()).isAir
            if (floating && wasFloating.put(id, true) != true) fire(player, DDITriggerType.FLOATING)

            // standing on blocks
            val belowId = Registries.BLOCK.getId(player.getServerWorld().getBlockState(player.blockPos.down()).block).path
            val blockConditions = linkedMapOf<DDITriggerType, () -> Boolean>(
                DDITriggerType.STAND_ON_GRASS to { belowId == "grass_block" },
                DDITriggerType.STAND_ON_LEAVES to { belowId.endsWith("_leaves") },
                DDITriggerType.STAND_ON_STONE to { belowId in stoneNames },
                DDITriggerType.STAND_ON_DEEPSLATE to { belowId in deepslateBlockNames },
            )
            for ((type, cond) in blockConditions) {
                if (cond()) fire(player, type)
            }
            for ((name, type) in singleNames) {
                if (belowId == name) fire(player, type)
            }
            if (belowId == "bedrock") fire(player, DDITriggerType.STAND_ON_BEDROCK)

            // block above head
            val hasAbove = hasBlockAboveHead(player)
            if (hasAbove && wasBlockAboveHead.put(id, true) != true) fire(player, DDITriggerType.BLOCK_ABOVE_HEAD)
            if (!hasAbove && wasNoBlockAboveHead.put(id, true) != true) fire(player, DDITriggerType.NO_BLOCK_ABOVE_HEAD)

            // death timer
            val dt = deathTick[id]
            if (dt != null && player.isDead) {
                val elapsed = server.ticks - dt
                if (elapsed >= 60 && notRespawn3sTriggered.getOrDefault(id, false).not()) { notRespawn3sTriggered[id] = true; fire(player, DDITriggerType.NOT_RESPAWN_3S) }
                if (elapsed >= 100 && notRespawn5sTriggered.getOrDefault(id, false).not()) { notRespawn5sTriggered[id] = true; fire(player, DDITriggerType.NOT_RESPAWN_5S) }
                if (elapsed >= 200 && notRespawn10sTriggered.getOrDefault(id, false).not()) { notRespawn10sTriggered[id] = true; fire(player, DDITriggerType.NOT_RESPAWN_10S) }
            }

            // hunger / height
            val hunger = player.hungerManager.foodLevel
            if (hunger < 18) fire(player, DDITriggerType.HUNGER_BELOW_18)
            if (hunger > 18) fire(player, DDITriggerType.HUNGER_ABOVE_18)
            if (player.y > 70) fire(player, DDITriggerType.Y_ABOVE_70)
            if (player.y < 70) fire(player, DDITriggerType.Y_BELOW_70)

            // distance
            val allPlayers = server.playerManager.playerList
            var minDist = Double.MAX_VALUE
            for (other in allPlayers) { if (other.uuid != id) { val d = player.squaredDistanceTo(other); if (d < minDist) minDist = d } }
            if (minDist > 225) fire(player, DDITriggerType.FAR_FROM_ALL_15M)
            if (minDist < 4) fire(player, DDITriggerType.TOO_CLOSE_TO_PLAYER)

            // sustained behaviors
            if (sprinting) { sprintStartTick.putIfAbsent(id, tick); if ((tick - sprintStartTick[id]!!) / 20 >= 30 && sprint30sTriggered.getOrDefault(id, false).not()) { sprint30sTriggered[id] = true; fire(player, DDITriggerType.SPRINT_30S) } }
            else { sprintStartTick.remove(id); sprint30sTriggered.remove(id) }
            if (sneaking) { sneakStartTick.putIfAbsent(id, tick); if ((tick - sneakStartTick[id]!!) / 20 >= 5 && sneak5sTriggered.getOrDefault(id, false).not()) { sneak5sTriggered[id] = true; fire(player, DDITriggerType.SNEAK_5S) } }
            else { sneakStartTick.remove(id); sneak5sTriggered.remove(id) }

            // jump count
            val onGround = player.isOnGround
            val prevGround = wasOnGround.put(id, onGround)
            if (!onGround && prevGround != null && prevGround) {
                val j = jumpCount.getOrDefault(id, 0) + 1; jumpCount[id] = j
                if (j >= 10 && jump10Triggered.getOrDefault(id, false).not()) { jump10Triggered[id] = true; fire(player, DDITriggerType.JUMP_10_TIMES) }
            }

            // experience
            val cl = player.experienceLevel; val cpr = player.experienceProgress
            val pl = prevExperienceLevel.put(id, cl)
            if (pl != null && cl > pl) fire(player, DDITriggerType.LEVEL_UP)
            if (prevExperienceProgress.put(id, cpr) != null && (cpr > (prevExperienceProgress.getOrDefault(id, cpr)) || cl > (pl ?: cl)))
                fire(player, DDITriggerType.GAIN_EXPERIENCE)

            // armor
            val armorSlots = arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
            if (armorSlots.any { !player.getEquippedStack(it).isEmpty }) fire(player, DDITriggerType.WEAR_ARMOR)

            // held item
            val heldId = Registries.ITEM.getId(player.mainHandStack.item).path
            val heldMap = mapOf(
                "crafting_table" to DDITriggerType.HOLD_CRAFTING_TABLE,
                "furnace" to DDITriggerType.HOLD_FURNACE,
                "wooden_pickaxe" to DDITriggerType.HOLD_WOODEN_PICKAXE,
                "iron_pickaxe" to DDITriggerType.HOLD_IRON_PICKAXE,
                "stone_pickaxe" to DDITriggerType.HOLD_STONE_PICKAXE,
                "wooden_axe" to DDITriggerType.HOLD_WOODEN_AXE,
                "stone_axe" to DDITriggerType.HOLD_STONE_AXE,
                "iron_axe" to DDITriggerType.HOLD_IRON_AXE,
            )
            heldMap[heldId]?.let { fire(player, it) }

            // hotbar
            if (player.inventory.selectedSlot == 0) fire(player, DDITriggerType.SELECT_SLOT_FIRST)
            if (player.inventory.selectedSlot == 8) fire(player, DDITriggerType.SELECT_SLOT_LAST)

            // fall height
            if (!onGround) {
                fallStartY.putIfAbsent(id, player.y)
                if ((fallStartY[id] ?: 0.0) - player.y >= 5 && fallTriggered.getOrDefault(id, false).not()) { fallTriggered[id] = true; fire(player, DDITriggerType.FALL_5_BLOCKS) }
            } else { fallStartY.remove(id); fallTriggered.remove(id) }

            // inventory
            val invItems = mapOf(
                "coal" to DDITriggerType.HAS_COAL, "iron_ingot" to DDITriggerType.HAS_IRON_INGOT,
                "copper_ingot" to DDITriggerType.HAS_COPPER_INGOT, "crafting_table" to DDITriggerType.HAS_CRAFTING_TABLE,
                "furnace" to DDITriggerType.HAS_FURNACE, "rotten_flesh" to DDITriggerType.HAS_ROTTEN_FLESH,
                "diamond" to DDITriggerType.HAS_DIAMOND, "dirt" to DDITriggerType.HAS_DIRT,
                "stone_pickaxe" to DDITriggerType.HAS_STONE_PICKAXE, "wooden_pickaxe" to DDITriggerType.HAS_WOODEN_PICKAXE,
                "iron_pickaxe" to DDITriggerType.HAS_IRON_PICKAXE, "bone" to DDITriggerType.HAS_BONE,
                "string" to DDITriggerType.HAS_STRING, "ender_pearl" to DDITriggerType.HAS_ENDER_PEARL,
                "leather" to DDITriggerType.HAS_LEATHER, "bucket" to DDITriggerType.HAS_BUCKET,
                "water_bucket" to DDITriggerType.HAS_WATER_BUCKET, "lava_bucket" to DDITriggerType.HAS_LAVA_BUCKET,
                "stone" to DDITriggerType.HAS_STONE, "smooth_stone" to DDITriggerType.HAS_SMOOTH_STONE,
                "tuff" to DDITriggerType.HAS_TUFF, "polished_andesite" to DDITriggerType.HAS_POLISHED_ANDESITE,
                "polished_granite" to DDITriggerType.HAS_POLISHED_GRANITE, "polished_diorite" to DDITriggerType.HAS_POLISHED_DIORITE,
            )
            for ((itemId, type) in invItems) { if (hasItem(player, itemId)) fire(player, type) }
            if (hasItemEnding(player, "_axe")) fire(player, DDITriggerType.HAS_AXE)
            if (hasItemEnding(player, "_sword")) fire(player, DDITriggerType.HAS_SWORD)
            if (hasItemEnding(player, "_leaves") || hasItem(player, "leaves")) fire(player, DDITriggerType.HAS_LEAVES)
            if (hasItemEnding(player, "_wool")) fire(player, DDITriggerType.HAS_WOOL)
            if (!hasItemStarting(player, "iron_")) fire(player, DDITriggerType.NO_IRON_TOOLS_OR_ARMOR)
            if (!hasItemStarting(player, "diamond_")) fire(player, DDITriggerType.NO_DIAMOND_TOOLS_OR_ARMOR)
            val offId = Registries.ITEM.getId(player.offHandStack.item).path
            if (offId == "shield") fire(player, DDITriggerType.HOLD_SHIELD_OFFHAND)

            // drop 30
            if (dropCount.getOrDefault(id, 0) >= 30 && drop30Triggered.getOrDefault(id, false).not()) { drop30Triggered[id] = true; fire(player, DDITriggerType.DROP_30_ITEMS) }

            // no-action timers
            lastJumpTick.putIfAbsent(id, tick); lastSneakActionTick.putIfAbsent(id, tick); lastSprintActionTick.putIfAbsent(id, tick)
            if (!onGround && prevGround != null && prevGround) lastJumpTick[id] = tick
            if (sneaking && (prevSneak == null || !prevSneak)) lastSneakActionTick[id] = tick
            if (sprinting && (prevSprint == null || !prevSprint)) lastSprintActionTick[id] = tick
            val sj = (tick - lastJumpTick[id]!!) / 20; val ss = (tick - lastSneakActionTick[id]!!) / 20; val sp = (tick - lastSprintActionTick[id]!!) / 20
            if (sj >= 30 && noJump30sTriggered.getOrDefault(id, false).not()) { noJump30sTriggered[id] = true; fire(player, DDITriggerType.NO_JUMP_30S) }
            if (ss >= 30 && noSneak30sTriggered.getOrDefault(id, false).not()) { noSneak30sTriggered[id] = true; fire(player, DDITriggerType.NO_SNEAK_30S) }
            if (sp >= 30 && noSprint30sTriggered.getOrDefault(id, false).not()) { noSprint30sTriggered[id] = true; fire(player, DDITriggerType.NO_SPRINT_30S) }
            if (sj >= 60 && noJump60sTriggered.getOrDefault(id, false).not()) { noJump60sTriggered[id] = true; fire(player, DDITriggerType.NO_JUMP_60S) }
            if (ss >= 60 && noSneak60sTriggered.getOrDefault(id, false).not()) { noSneak60sTriggered[id] = true; fire(player, DDITriggerType.NO_SNEAK_60S) }
            if (sp >= 60 && noSprint60sTriggered.getOrDefault(id, false).not()) { noSprint60sTriggered[id] = true; fire(player, DDITriggerType.NO_SPRINT_60S) }
        }

        cleanupMaps(server.playerManager.playerList.map { it.uuid }.toSet())
    }

    private fun detectMineBlock(player: ServerPlayerEntity, id: String) {
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae") || id == "bamboo_block") fire(player, DDITriggerType.MINE_WOOD)
        if (id in stoneNames) fire(player, DDITriggerType.MINE_STONE)
        if (id.contains("deepslate") && !id.contains("_ore")) fire(player, DDITriggerType.MINE_DEEPSLATE)
        if (id.contains("coal_ore")) fire(player, DDITriggerType.MINE_COAL)
        if (id.contains("iron_ore")) fire(player, DDITriggerType.MINE_IRON)
        if (id.contains("copper_ore")) fire(player, DDITriggerType.MINE_COPPER)
        if (id.contains("gold_ore")) fire(player, DDITriggerType.MINE_GOLD)
        if (id.contains("diamond_ore")) fire(player, DDITriggerType.MINE_DIAMOND)
        singleNames[id]?.let { fire(player, it) }
    }

    private fun detectOpenContainer(player: ServerPlayerEntity, blockId: String) {
        when (blockId) {
            "chest", "trapped_chest", "ender_chest" -> fire(player, DDITriggerType.OPEN_CHEST)
            "furnace", "blast_furnace", "smoker" -> fire(player, DDITriggerType.OPEN_FURNACE)
            "crafting_table" -> fire(player, DDITriggerType.OPEN_CRAFTING_TABLE)
        }
    }

    private fun detectPlaceBlock(player: ServerPlayerEntity, itemId: String) {
        val map = mapOf(
            "dirt" to DDITriggerType.PLACE_DIRT, "cobblestone" to DDITriggerType.PLACE_COBBLESTONE,
            "cobbled_deepslate" to DDITriggerType.PLACE_COBBLED_DEEPSLATE, "andesite" to DDITriggerType.PLACE_ANDESITE,
            "granite" to DDITriggerType.PLACE_GRANITE, "diorite" to DDITriggerType.PLACE_DIORITE,
            "tuff" to DDITriggerType.PLACE_TUFF, "crafting_table" to DDITriggerType.PLACE_CRAFTING_TABLE,
            "furnace" to DDITriggerType.PLACE_FURNACE, "chest" to DDITriggerType.PLACE_CHEST,
        )
        map[itemId]?.let { fire(player, it) }
    }

    private fun isPlayerEnclosed(player: ServerPlayerEntity): Boolean {
        val world = player.getServerWorld()
        val feet = player.blockPos; val head = feet.up()
        if (!world.getBlockState(feet.down()).isSolidBlock(world, feet.down())) return false
        if (!world.getBlockState(head.up()).isSolidBlock(world, head.up())) return false
        val dirs = arrayOf(feet.east(), feet.west(), feet.south(), feet.north(), head.east(), head.west(), head.south(), head.north())
        return dirs.all { world.getBlockState(it).isSolidBlock(world, it) }
    }

    private fun hasBlockAboveHead(player: ServerPlayerEntity): Boolean {
        val world = player.getServerWorld(); val fx = player.blockPos.x; val fz = player.blockPos.z
        for (y in (player.blockPos.y + 2)..(world.dimension.height() - 1)) {
            if (!world.getBlockState(BlockPos(fx, y, fz)).isAir) return true
        }
        return false
    }

    private fun isFireDamage(source: DamageSource): Boolean {
        val id = source.type.msgId()
        return id in setOf("inFire", "onFire", "lava", "hotFloor", "campfire") || id.contains("fire") || id.contains("flame") || id.contains("burn") || id.contains("magma")
    }

    private fun hasItem(player: ServerPlayerEntity, itemId: String): Boolean {
        for (i in 0 until player.inventory.size()) { if (Registries.ITEM.getId(player.inventory.getStack(i).item).path == itemId) return true }
        return false
    }
    private fun hasItemEnding(player: ServerPlayerEntity, suffix: String): Boolean {
        for (i in 0 until player.inventory.size()) { if (Registries.ITEM.getId(player.inventory.getStack(i).item).path.endsWith(suffix)) return true }
        return false
    }
    private fun hasItemStarting(player: ServerPlayerEntity, prefix: String): Boolean {
        for (i in 0 until player.inventory.size()) { if (Registries.ITEM.getId(player.inventory.getStack(i).item).path.startsWith(prefix)) return true }
        return false
    }

    fun resetJumpCount(id: UUID) { jumpCount.remove(id); jump10Triggered.remove(id) }
    fun resetLookSameDir(id: UUID) { lastYaw.remove(id); lastPitch.remove(id); lookSameDirTicks.remove(id); lookSameDirTriggered.remove(id) }
    fun resetPlaceCount(id: UUID) { placeCount.remove(id); place30Triggered.remove(id) }
    fun resetDropCount(id: UUID) { dropCount.remove(id); drop30Triggered.remove(id) }
    fun resetNoJumpState(id: UUID) { lastJumpTick.remove(id); noJump30sTriggered.remove(id); noJump60sTriggered.remove(id) }
    fun resetNoSneakState(id: UUID) { lastSneakActionTick.remove(id); noSneak30sTriggered.remove(id); noSneak60sTriggered.remove(id) }
    fun resetNoSprintState(id: UUID) { lastSprintActionTick.remove(id); noSprint30sTriggered.remove(id); noSprint60sTriggered.remove(id) }
    fun resetBlockAboveHeadState(id: UUID) { wasBlockAboveHead.remove(id); wasNoBlockAboveHead.remove(id) }

    fun clearAllState() {
        listOf(
            wasSneaking, wasSprinting, wasUsingFood, lastEatenFoodId, wasLookingDown, wasLookingUp,
            wasLookingEast, wasLookingSouth, wasLookingWest, wasLookingNorth, lastX, lastY, lastZ,
            standStillTicks, standStillTriggered, lastYaw, lastPitch, lookSameDirTicks, lookSameDirTriggered,
            wasEnclosed, wasSubmerged, wasFloating, wasOnBedrock, wasBlockAboveHead, wasNoBlockAboveHead,
            deathTick, notRespawn3sTriggered, notRespawn5sTriggered, notRespawn10sTriggered,
            sprintStartTick, sneakStartTick, jumpCount, sprint30sTriggered, sneak5sTriggered, jump10Triggered,
            prevExperienceLevel, prevExperienceProgress, wasOnGround, fallStartY, fallTriggered,
            placeCount, place30Triggered, dropCount, drop30Triggered, lastJumpTick, lastSneakActionTick, lastSprintActionTick,
            noJump30sTriggered, noSneak30sTriggered, noSprint30sTriggered, noJump60sTriggered, noSneak60sTriggered, noSprint60sTriggered,
        ).forEach { it.clear() }
    }

    private fun cleanupMaps(online: Set<UUID>) {
        listOf(
            wasSneaking, wasSprinting, wasUsingFood, lastEatenFoodId, wasLookingDown, wasLookingUp,
            wasLookingEast, wasLookingSouth, wasLookingWest, wasLookingNorth, lastX, lastY, lastZ,
            standStillTicks, standStillTriggered, lastYaw, lastPitch, lookSameDirTicks, lookSameDirTriggered,
            wasEnclosed, wasSubmerged, wasFloating, wasOnBedrock, wasBlockAboveHead, wasNoBlockAboveHead,
            deathTick, notRespawn3sTriggered, notRespawn5sTriggered, notRespawn10sTriggered,
            sprintStartTick, sneakStartTick, jumpCount, sprint30sTriggered, sneak5sTriggered, jump10Triggered,
            prevExperienceLevel, prevExperienceProgress, wasOnGround, fallStartY, fallTriggered,
            placeCount, place30Triggered, dropCount, drop30Triggered, lastJumpTick, lastSneakActionTick, lastSprintActionTick,
            noJump30sTriggered, noSneak30sTriggered, noSprint30sTriggered, noJump60sTriggered, noSneak60sTriggered, noSprint60sTriggered,
        ).forEach { it.keys.retainAll(online) }
    }
}
