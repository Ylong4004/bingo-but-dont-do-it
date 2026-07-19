package me.jfenn.bingo.integrations.ddi.special

import net.minecraft.block.Blocks
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import net.minecraft.village.TradeOffer
import net.minecraft.village.TradeOfferList
import net.minecraft.village.TradedItem
import net.minecraft.village.VillagerData
import net.minecraft.village.VillagerProfession
import net.minecraft.village.VillagerType
import java.util.Optional
import java.util.UUID
import kotlin.math.min

internal class DiamondBlessingEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.callbacks.setModifier(DDISpecialEventModifier.DIAMOND_BLESSING, true)
        context.broadcast("§b💎 特殊事件「钻石祝福」已触发！2 分钟内挖掘钻石矿可为目标恢复 1 颗生命！")
    }

    override fun onDiamondOreMined(player: ServerPlayerEntity) {
        val objectiveId = context.objectiveId(player) ?: return
        val result = context.adjustHeart(objectiveId, 1, player)
        val outcome = if (result.appliedDelta > 0) {
            "目标恢复了 1 颗生命"
        } else {
            "目标生命已满"
        }
        context.message(player, "§b💎 钻石祝福！$outcome，当前 §c${result.hearts}/${result.maxHearts} ❤")
    }

    override fun onFinish() = disable()

    override fun onCleanup() = disable()

    private fun disable() {
        context.callbacks.setModifier(DDISpecialEventModifier.DIAMOND_BLESSING, false)
    }
}

internal class PumpkinHeadEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private val originalHelmets = linkedMapOf<UUID, ItemStack>()

    override fun onStart() {
        context.activePlayers().forEach(::equipPumpkin)
        context.broadcast("§6🎃 特殊事件「全员南瓜头」已触发！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        // 重新连接的参与者会再次获得事件头盔；其真实头盔已在断线前恢复，
        // 因此不会丢失。
        context.activePlayers()
            .filterNot { it.uuid in originalHelmets }
            .forEach(::equipPumpkin)
    }

    override fun onFinish() = restore()

    override fun onCleanup() = restore()

    override fun onPlayerLeaving(player: ServerPlayerEntity) {
        restorePlayer(player)
    }

    private fun restore() {
        originalHelmets.keys.toList().forEach { playerId ->
            context.server.playerManager.getPlayer(playerId)?.let(::restorePlayer)
        }
        originalHelmets.clear()
    }

    private fun restorePlayer(player: ServerPlayerEntity) {
        val original = originalHelmets.remove(player.uuid) ?: return
        if (isEventPumpkin(player.getEquippedStack(EquipmentSlot.HEAD))) {
            player.equipStack(EquipmentSlot.HEAD, original)
        } else if (!original.isEmpty) {
            // 事件南瓜可能因死亡或其他机制而被移除；绝不能悄悄删除
            // 已保存的原始头盔。
            player.giveOrDrop(original)
        }
    }


    private fun equipPumpkin(player: ServerPlayerEntity) {
        val enchantments: RegistryWrapper.Impl<Enchantment> = context.server.registryManager
            .getOptional(RegistryKeys.ENCHANTMENT)
            .orElseThrow()
        val bindingCurse: RegistryEntry<Enchantment> = enchantments
            .getOptional(Enchantments.BINDING_CURSE)
            .orElseThrow()
        originalHelmets[player.uuid] = player.getEquippedStack(EquipmentSlot.HEAD).copy()
        val pumpkin = ItemStack(Blocks.CARVED_PUMPKIN)
        pumpkin.addEnchantment(bindingCurse, 1)
        val eventData = NbtCompound().apply { putBoolean(EVENT_PUMPKIN_TAG, true) }
        pumpkin[DataComponentTypes.CUSTOM_DATA] = NbtComponent.of(eventData)
        player.equipStack(EquipmentSlot.HEAD, pumpkin)
        context.message(player, "§6🎃 全员南瓜头！带绑定诅咒，事件结束前无法摘除！")
    }

    private fun isEventPumpkin(stack: ItemStack): Boolean =
        stack[DataComponentTypes.CUSTOM_DATA]
            ?.copyNbt()
            ?.getBoolean(EVENT_PUMPKIN_TAG, false) == true

    private companion object {
        const val EVENT_PUMPKIN_TAG = "bingo_ddi_special_pumpkin"
    }
}

/** 每个目标随机选择一名在线代表，避免队伍人数造成偏差。 */
internal class SkyWaterChallengeEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private data class SavedHand(val slot: Int, val stack: ItemStack)
    private data class SavedOrigin(
        val world: RegistryKey<World>,
        val position: Vec3d,
        val yaw: Float,
        val pitch: Float,
    )

    private val trackedPlayers = linkedSetOf<UUID>()
    private val settledObjectives = mutableSetOf<String>()
    private val originalHands = linkedMapOf<UUID, SavedHand>()
    private val originalPositions = linkedMapOf<UUID, SavedOrigin>()

    override fun onStart() {
        context.activeObjectiveGroups().values.forEach { players ->
            val candidates = players.filter(::hasSafeChallengeColumn)
            if (candidates.isEmpty()) {
                players.forEach {
                    context.message(it, "§e💧 当前维度或位置没有安全的高空落水场地，本队跳过本次挑战。")
                }
                return@forEach
            }
            val player = candidates[context.random.nextInt(candidates.size)]
            val world = player.entityWorld as ServerWorld
            val surfaceY = world.getTopY(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                player.blockPos.x,
                player.blockPos.z,
            )
            val targetY = min(surfaceY + 80.0, world.topYInclusive - 2.0)
            val selectedSlot = player.inventory.selectedSlot
            originalPositions[player.uuid] = SavedOrigin(
                world = world.registryKey,
                position = player.getEntityPos(),
                yaw = player.yaw,
                pitch = player.pitch,
            )
            originalHands[player.uuid] = SavedHand(
                selectedSlot,
                player.inventory.getStack(selectedSlot).copy(),
            )
            player.inventory.setStack(selectedSlot, ItemStack(Items.WATER_BUCKET))
            player.teleport(
                world,
                player.x,
                targetY,
                player.z,
                emptySet(),
                player.yaw,
                player.pitch,
                false,
            )
            trackedPlayers += player.uuid
            context.message(player, "§b💧 你是队伍代表！使用水桶完成高空落水可恢复 1 颗共享生命！")
        }
        context.broadcast("§b💧 特殊事件「高空落水挑战」已触发！每个目标选出一名代表。")
    }

    private fun hasSafeChallengeColumn(player: ServerPlayerEntity): Boolean {
        val world = player.entityWorld as? ServerWorld ?: return false
        if (world.registryKey == World.NETHER) return false
        val surfaceY = world.getTopY(
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            player.blockPos.x,
            player.blockPos.z,
        )
        if (surfaceY <= world.bottomY + 2) return false
        val targetY = min(surfaceY + 80, world.topYInclusive - 2)
        if (targetY <= surfaceY + 10) return false
        val target = BlockPos(player.blockPos.x, targetY, player.blockPos.z)
        return world.isAir(target) && world.isAir(target.up())
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        val completed = mutableListOf<UUID>()
        trackedPlayers.forEach { playerId ->
            val player = context.server.playerManager.getPlayer(playerId)
            if (player == null) {
                completed += playerId
                return@forEach
            }
            if (player.isTouchingWater) {
                val result = context.adjustHeartOnce(settledObjectives, player, 1)
                if (result != null) {
                    context.message(player, "§a💚 高空落水成功！目标生命 §c${result.hearts}/${result.maxHearts} ❤")
                }
                completed += playerId
            }
        }
        trackedPlayers.removeAll(completed.toSet())
    }

    override fun onFinish() {
        trackedPlayers.forEach { playerId ->
            context.server.playerManager.getPlayer(playerId)?.let {
                context.message(it, "§c💧 高空落水挑战结束，你未能成功落水。")
                returnToOrigin(it)
            }
        }
        restoreHands()
        clear()
    }

    override fun onCleanup() {
        trackedPlayers.forEach { playerId ->
            context.server.playerManager.getPlayer(playerId)?.let(::returnToOrigin)
        }
        restoreHands()
        clear()
    }

    override fun onPlayerLeaving(player: ServerPlayerEntity) {
        if (player.uuid in trackedPlayers) returnToOrigin(player)
        restoreHand(player)
        trackedPlayers.remove(player.uuid)
    }

    private fun restoreHands() {
        originalHands.keys.toList().forEach { playerId ->
            context.server.playerManager.getPlayer(playerId)?.let(::restoreHand)
        }
    }

    private fun restoreHand(player: ServerPlayerEntity) {
        val saved = originalHands.remove(player.uuid) ?: return
        val current = player.inventory.getStack(saved.slot).copy()
        if (current.isEmpty || current.isOf(Items.WATER_BUCKET) || current.isOf(Items.BUCKET)) {
            player.inventory.setStack(saved.slot, saved.stack)
            // 若玩家将自己的桶移入事件槽位，绝不能删除这个真实物品。
            // 当物品栏状态无法明确判断时，宁可保留挑战水桶，也不能冒险丢失物品。
            if (!current.isEmpty) player.giveOrDrop(current)
        } else if (!saved.stack.isEmpty) {
            player.giveOrDrop(saved.stack)
        }
    }

    private fun returnToOrigin(player: ServerPlayerEntity) {
        val origin = originalPositions[player.uuid] ?: return
        val world = context.server.getWorld(origin.world) ?: return
        player.teleport(
            world,
            origin.position.x,
            origin.position.y,
            origin.position.z,
            emptySet(),
            origin.yaw,
            origin.pitch,
            false,
        )
    }

    private fun clear() {
        trackedPlayers.clear()
        settledObjectives.clear()
        originalHands.clear()
        originalPositions.clear()
    }
}

internal class DurabilityBlessingEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.callbacks.setModifier(DDISpecialEventModifier.DURABILITY_IMMUNITY, true)
        context.broadcast("§d🛡 特殊事件「豁免祝福」已触发！2 分钟内装备与工具不消耗耐久！")
    }

    override fun onFinish() = disable()

    override fun onCleanup() = disable()

    private fun disable() = context.callbacks.setModifier(DDISpecialEventModifier.DURABILITY_IMMUNITY, false)
}

internal class EquipmentRustEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.callbacks.setModifier(DDISpecialEventModifier.EQUIPMENT_RUST, true)
        context.broadcast("§7🔧 特殊事件「装备锈蚀」已触发！2 分钟内装备与工具耐久损耗变为五倍！")
    }

    override fun onFinish() = disable()

    override fun onCleanup() = disable()

    private fun disable() = context.callbacks.setModifier(DDISpecialEventModifier.EQUIPMENT_RUST, false)
}

internal class HungerDiseaseEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.callbacks.setModifier(DDISpecialEventModifier.HUNGER_DISEASE, true)
        context.broadcast("§c🍖 特殊事件「饥饿疫病」已触发！30 秒内饥饿加剧且食物回复减半！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        context.activePlayers().forEach { player ->
            context.applyTemporaryStatusEffect(
                player,
                StatusEffects.HUNGER,
                40,
                2,
                false,
                false,
                true,
            )
        }
    }

    override fun onFinish() = disable()

    override fun onCleanup() = disable()

    private fun disable() = context.callbacks.setModifier(DDISpecialEventModifier.HUNGER_DISEASE, false)
}

/** 使用不参与序列化的临时修饰符，而不覆盖基础属性。 */
internal class EveryoneBabyEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        applyToActivePlayers()
        context.broadcast("§a👶 特殊事件「全员变幼体」已触发！所有存活玩家缩小到 20%，持续 60 秒！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) = applyToActivePlayers()

    override fun onFinish() = removeFromAllOnlinePlayers()

    override fun onCleanup() = removeFromAllOnlinePlayers()

    private fun applyToActivePlayers() {
        context.activePlayers().forEach { player ->
            listOfNotNull(
                player.getAttributeInstance(EntityAttributes.SCALE),
                player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED),
                player.getAttributeInstance(EntityAttributes.JUMP_STRENGTH),
            ).forEach { attribute ->
                if (!attribute.hasModifier(MODIFIER_ID)) {
                    attribute.addTemporaryModifier(
                        EntityAttributeModifier(
                            MODIFIER_ID,
                            -0.8,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        )
                    )
                }
            }
        }
    }

    private fun removeFromAllOnlinePlayers() {
        context.server.playerManager.playerList.forEach { player ->
            player.getAttributeInstance(EntityAttributes.SCALE)?.removeModifier(MODIFIER_ID)
            player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)?.removeModifier(MODIFIER_ID)
            player.getAttributeInstance(EntityAttributes.JUMP_STRENGTH)?.removeModifier(MODIFIER_ID)
        }
    }

    private companion object {
        val MODIFIER_ID: Identifier = Identifier.of("yet-another-minecraft-bingo", "ddi_everyone_baby")
    }
}

/** 每个目标仅选择一名代表并结算一次；有效生命包含伤害吸收值。 */
internal class ArrowTrialEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private val initialEffectiveHealth = linkedMapOf<UUID, Float>()
    private val damagedByEventArrow = mutableSetOf<UUID>()
    private val arrows = mutableListOf<Entity>()
    private val settledObjectives = mutableSetOf<String>()

    override fun onStart() {
        context.objectiveRepresentatives().forEach { player ->
            initialEffectiveHealth[player.uuid] = player.health + player.absorptionAmount
            context.message(player, "§b🏹 你是队伍代表！在箭雨中无伤坚持 10 秒可恢复生命，受伤则扣除生命！")
        }
        context.broadcast("§b🏹 特殊事件「箭雨试炼」已触发！每个目标选出一名代表。")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        initialEffectiveHealth.keys.toList().forEach { playerId ->
            val player = context.server.playerManager.getPlayer(playerId) ?: return@forEach
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            repeat(3) {
                val arrow = ArrowEntity(EntityType.ARROW, world)
                arrow.setPosition(
                    player.x + (context.random.nextDouble() - 0.5) * 2,
                    player.y + 8 + context.random.nextDouble() * 10,
                    player.z + (context.random.nextDouble() - 0.5) * 2,
                )
                arrow.setVelocity(
                    (context.random.nextDouble() - 0.5) * 0.1,
                    -2.5 - context.random.nextDouble() * 1.5,
                    (context.random.nextDouble() - 0.5) * 0.1,
                )
                arrow.setDamage(2.0)
                context.spawn(world, arrow)
                arrows += arrow
            }
        }
    }

    override fun onPlayerDamaged(player: ServerPlayerEntity, source: DamageSource) {
        if (player.uuid !in initialEffectiveHealth) return
        val directSource = source.source ?: return
        if (directSource.commandTags.any { it == "$DDI_SPECIAL_ENTITY_TAG_PREFIX-${definition.type.id}" }) {
            damagedByEventArrow += player.uuid
        }
    }

    override fun onPlayerLeaving(player: ServerPlayerEntity) {
        if (initialEffectiveHealth.remove(player.uuid) == null) return
        damagedByEventArrow.remove(player.uuid)
        val result = context.adjustHeartOnce(settledObjectives, player, -1) ?: return
        context.message(
            player,
            "§c🏹 你在箭雨试炼中离线，队伍失去 1 颗生命！当前 §c${result.hearts}/${result.maxHearts} ❤",
        )
    }

    override fun onFinish() {
        initialEffectiveHealth.forEach { (playerId, initialHealth) ->
            val player = context.server.playerManager.getPlayer(playerId) ?: return@forEach
            val effectiveHealth = player.health + player.absorptionAmount
            val delta = if (playerId in damagedByEventArrow || effectiveHealth < initialHealth) -1 else 1
            val result = context.adjustHeartOnce(settledObjectives, player, delta) ?: return@forEach
            val outcome = if (delta < 0) "受伤，失去" else "无伤存活，恢复"
            context.message(player, "§b🏹 箭雨结束：$outcome 1 颗生命！当前 §c${result.hearts}/${result.maxHearts} ❤")
        }
        discardArrows()
        clearState()
    }

    override fun onCleanup() {
        discardArrows()
        clearState()
    }

    private fun discardArrows() {
        context.discardTrackedEntities()
        arrows.clear()
    }

    private fun clearState() {
        initialEffectiveHealth.clear()
        damagedByEventArrow.clear()
        settledObjectives.clear()
    }
}

/** 每个目标仅生成一名商人，避免有限交易次数随队伍人数倍增。 */
internal class TradeMerchantEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private val merchants = mutableListOf<VillagerEntity>()

    override fun onStart() {
        context.objectiveRepresentatives().forEach { player -> spawnMerchant(player) }
        context.broadcast("§6⚡ 特殊事件「交易商人」已触发！每个目标附近出现一位神秘商人，持续 30 秒！")
    }

    override fun onFinish() = discardMerchants()

    override fun onCleanup() = discardMerchants()

    private fun spawnMerchant(player: ServerPlayerEntity) {
        val world = player.entityWorld as? ServerWorld ?: return
        val angle = context.random.nextDouble() * Math.PI * 2
        val distance = 3 + context.random.nextDouble() * 2
        val initial = BlockPos(
            (player.x + kotlin.math.cos(angle) * distance).toInt(),
            player.y.toInt(),
            (player.z + kotlin.math.sin(angle) * distance).toInt(),
        )
        val position = findGroundNear(player, initial)
        val merchant = EntityType.VILLAGER.create(world, SpawnReason.EVENT) ?: return
        merchant.refreshPositionAndAngles(
            position.x + 0.5,
            position.y.toDouble(),
            position.z + 0.5,
            context.random.nextFloat() * 360f,
            0f,
        )

        val typeWrapper: RegistryWrapper<VillagerType> = world.registryManager
            .getOptional(RegistryKeys.VILLAGER_TYPE)
            .orElseThrow()
        val typeEntry: RegistryEntry<VillagerType> = typeWrapper
            .getOptional(VillagerType.PLAINS)
            .orElseThrow()
        val professionWrapper: RegistryWrapper<VillagerProfession> = world.registryManager
            .getOptional(RegistryKeys.VILLAGER_PROFESSION)
            .orElseThrow()
        val professionEntry: RegistryEntry<VillagerProfession> = professionWrapper
            .getOptional(VillagerProfession.ARMORER)
            .orElseThrow()
        merchant.villagerData = VillagerData(typeEntry, professionEntry, 5)
        merchant.offers = buildOffers()
        merchant.isInvulnerable = true
        merchant.customName = net.minecraft.text.Text.literal("§6⚡ 神秘商人")
        merchant.isCustomNameVisible = true

        if (context.spawn(world, merchant)) {
            merchants += merchant
            context.message(player, "§6⚡ 神秘商人出现在附近！用钻石换取下界合金装备！")
        }
    }

    private fun buildOffers() = TradeOfferList().apply {
        add(twoInputOffer(Items.STICK, 1, Items.DIAMOND, 2, ItemStack(Items.NETHERITE_SWORD)))
        add(twoInputOffer(Items.STICK, 2, Items.DIAMOND, 3, ItemStack(Items.NETHERITE_AXE)))
        add(twoInputOffer(Items.STICK, 2, Items.DIAMOND, 3, ItemStack(Items.NETHERITE_PICKAXE)))
        add(oneInputOffer(Items.DIAMOND, 5, ItemStack(Items.NETHERITE_HELMET)))
        add(oneInputOffer(Items.DIAMOND, 8, ItemStack(Items.NETHERITE_CHESTPLATE)))
        add(oneInputOffer(Items.DIAMOND, 7, ItemStack(Items.NETHERITE_LEGGINGS)))
        add(oneInputOffer(Items.DIAMOND, 4, ItemStack(Items.NETHERITE_BOOTS)))
    }

    private fun twoInputOffer(
        first: net.minecraft.item.Item,
        firstCount: Int,
        second: net.minecraft.item.Item,
        secondCount: Int,
        result: ItemStack,
    ) = TradeOffer(
        TradedItem(first, firstCount),
        Optional.of(TradedItem(second, secondCount)),
        result,
        1,
        30,
        0.05f,
    )

    private fun oneInputOffer(
        input: net.minecraft.item.Item,
        count: Int,
        result: ItemStack,
    ) = TradeOffer(TradedItem(input, count), result, 1, 30, 0.05f)

    private fun discardMerchants() {
        context.discardTrackedEntities()
        merchants.clear()
    }
}
