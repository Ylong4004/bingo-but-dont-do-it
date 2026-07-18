package me.jfenn.bingo.integrations.ddi.special

import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.TntEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.Collections
import kotlin.math.cos
import kotlin.math.sin

internal class MonsterRampageEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        val types = listOf(
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.SPIDER,
            EntityType.CREEPER,
            EntityType.WITCH,
            EntityType.ENDERMAN,
            EntityType.HUSK,
            EntityType.STRAY,
        )
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            repeat(3) {
                val angle = context.random.nextDouble() * Math.PI * 2
                val distance = 3 + context.random.nextDouble() * 7
                val initial = BlockPos(
                    (player.x + cos(angle) * distance).toInt(),
                    player.y.toInt(),
                    (player.z + sin(angle) * distance).toInt(),
                )
                val position = findGroundNear(player, initial)
                val entity = types[context.random.nextInt(types.size)]
                    .create(world, SpawnReason.EVENT) ?: return@repeat
                entity.refreshPositionAndAngles(
                    position.x + 0.5,
                    position.y.toDouble(),
                    position.z + 0.5,
                    context.random.nextFloat() * 360f,
                    0f,
                )
                context.spawn(world, entity)
            }
            context.message(player, "§c⚡ 怪物狂潮！怪物正在你身边生成！")
        }
        context.broadcast("§4☠ 特殊事件「怪物狂潮」已触发！每名存活玩家周围生成了 §c3 只§4怪物！")
    }
}

internal class DiamondGiftEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.activePlayers().forEach { player ->
            player.giveOrDrop(ItemStack(Items.DIAMOND, 15))
            context.message(player, "§b💎 钻石馈赠！你获得了 §e15 颗钻石§b！")
        }
        context.broadcast("§b💎 特殊事件「钻石馈赠」已触发！每名存活玩家获得 §e15 颗钻石§b！")
    }
}

/** 队伍模式适配：汇总目标内所有钻石，再以共享生命数为上限进行扣除。 */
internal class DiamondCurseEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.activeObjectiveGroups().forEach { (objectiveId, players) ->
            val diamondCount = players.sumOf { it.countItem(Items.DIAMOND) }
            if (diamondCount <= 0) {
                players.forEach { context.message(it, "§a😌 钻石诅咒降临，但你的队伍没有钻石！") }
                return@forEach
            }
            val actor = players.firstOrNull()
            val result = context.adjustHeart(objectiveId, -diamondCount, actor)
            val lost = -result.appliedDelta
            players.forEach {
                context.message(
                    it,
                    "§c💀 钻石诅咒！队伍持有 §e$diamondCount 颗钻石§c，失去 $lost 颗共享生命！剩余 §c${result.hearts} ❤",
                )
            }
        }
        context.broadcast("§c💀 特殊事件「钻石诅咒」已触发！持有钻石的目标按钻石数量失去生命！")
    }
}

internal class EclipseCurseEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.activePlayers().forEach { player ->
            context.applyTemporaryStatusEffect(player, StatusEffects.BLINDNESS, 60 * 20)
            context.message(player, "§8🌑 日食诅咒！你暂时什么都看不见了……")
        }
        context.broadcast("§8🌑 特殊事件「日食诅咒」已触发！所有存活玩家失明 60 秒！")
    }
}

internal class CalmEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.broadcast("§7☁ 特殊事件「平静」已触发！无事发生……")
    }
}

internal class CloudEffectEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.activePlayers().forEach { player ->
            context.applyTemporaryStatusEffect(player, StatusEffects.LEVITATION, 30 * 20)
            context.message(player, "§f☁ 唉，云朵？你飘起来了！")
        }
        context.broadcast("§f☁ 特殊事件「唉，云朵？」已触发！所有存活玩家漂浮 30 秒！")
    }
}

internal class TntRainEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        var total = 0
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            repeat(20) {
                val tnt = TntEntity(
                    world,
                    player.x + (context.random.nextDouble() - 0.5) * 12,
                    player.y + 15 + context.random.nextDouble() * 10,
                    player.z + (context.random.nextDouble() - 0.5) * 12,
                    player,
                )
                tnt.fuse = 40 + context.random.nextInt(40)
                if (context.spawn(world, tnt)) total++
            }
            context.message(player, "§c💣 TNT 降雨！小心头顶！")
        }
        context.broadcast("§c💣 特殊事件「TNT降雨」已触发！生成了 §e$total 个§c点燃的 TNT！")
    }
}

internal class InventoryShuffleEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.activePlayers().forEach { player ->
            val items = MutableList(9) { slot -> player.inventory.getStack(slot).copy() }
            Collections.shuffle(items, java.util.Random(context.random.nextLong()))
            items.forEachIndexed { slot, stack -> player.inventory.setStack(slot, stack) }
            context.message(player, "§d🔀 物品栏洗牌！你的快捷栏已被随机打乱！")
        }
        context.broadcast("§d🔀 特殊事件「物品栏洗牌」已触发！")
    }
}

internal class ChickenRainEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        var total = 0
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            repeat(50) {
                val chicken = EntityType.CHICKEN.create(world, SpawnReason.EVENT) ?: return@repeat
                chicken.refreshPositionAndAngles(
                    player.x + (context.random.nextDouble() - 0.5) * 12,
                    player.y + 8 + context.random.nextDouble() * 12,
                    player.z + (context.random.nextDouble() - 0.5) * 12,
                    context.random.nextFloat() * 360f,
                    0f,
                )
                if (context.spawn(world, chicken)) total++
            }
            context.message(player, "§f🐔 小鸡天降！")
        }
        context.broadcast("§f🐔 特殊事件「小鸡天降」已触发！生成了 §e$total 只§f小鸡！")
    }
}

internal class PlayerSwapEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private data class Destination(
        val world: ServerWorld,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
    )

    override fun onStart() {
        val players = context.activePlayers()
        if (players.size < 2) {
            context.broadcast("§d🔄 特殊事件「玩家互换位置」已触发，但存活玩家不足 2 人。")
            return
        }
        val destinations = players.map { player ->
            Destination(
                world = player.entityWorld as ServerWorld,
                x = player.x,
                y = player.y,
                z = player.z,
                yaw = player.yaw,
                pitch = player.pitch,
            )
        }
        players.forEachIndexed { index, player ->
            val destination = destinations[(index + players.size - 1) % players.size]
            player.teleport(
                destination.world,
                destination.x,
                destination.y,
                destination.z,
                emptySet(),
                destination.yaw,
                destination.pitch,
                false,
            )
            context.message(player, "§d🔄 你被传送到了另一名玩家的位置！")
        }
        context.broadcast("§d🔄 特殊事件「玩家互换位置」已触发！§e${players.size} 名§f玩家已跨维度正确交换位置！")
    }
}

internal class InventoryMigrationEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        var totalStacks = 0
        context.activePlayers().forEach { player ->
            for (slot in 0 until player.inventory.size()) {
                val stack = player.inventory.getStack(slot)
                if (stack.isEmpty) continue
                val dropped = stack.copy()
                player.inventory.setStack(slot, ItemStack.EMPTY)
                player.dropItem(dropped, false)
                totalStacks++
            }
            context.message(player, "§c📦 物资迁徙！背包物品全部掉落在身边！")
        }
        context.broadcast("§c📦 特殊事件「物资迁徙」已触发！共迁徙 §e$totalStacks 组§c物品！")
    }
}
