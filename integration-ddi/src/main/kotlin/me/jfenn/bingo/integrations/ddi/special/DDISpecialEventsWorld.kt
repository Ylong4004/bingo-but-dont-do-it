package me.jfenn.bingo.integrations.ddi.special

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.CropBlock
import net.minecraft.block.SaplingBlock
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.ExperienceOrbEntity
import net.minecraft.entity.FallingBlockEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

internal class FoodRainEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private val foods: List<Item> = listOf(Items.COOKED_BEEF, Items.BREAD, Items.GOLDEN_CARROT)

    override fun onStart() {
        context.broadcast("§a🍖 特殊事件「美食雨」已触发！美食将持续降落 10 秒！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            repeat(1 + context.random.nextInt(2)) {
                val entity = ItemEntity(
                    world,
                    player.x + (context.random.nextDouble() - 0.5) * 6,
                    player.y + 8 + context.random.nextDouble() * 4,
                    player.z + (context.random.nextDouble() - 0.5) * 6,
                    ItemStack(foods[context.random.nextInt(foods.size)]),
                )
                entity.setVelocity(0.0, -0.2, 0.0)
                context.spawn(world, entity)
            }
        }
    }
}

internal class XpStormEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.broadcast("§a🌟 特殊事件「经验风暴」已触发！经验球将持续降落 10 秒！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            val count = 6 + if (context.random.nextFloat() < 0.25f) 1 else 0
            repeat(count) {
                val orb = ExperienceOrbEntity(
                    world,
                    player.x + (context.random.nextDouble() - 0.5) * 6,
                    player.y + 6 + context.random.nextDouble() * 4,
                    player.z + (context.random.nextDouble() - 0.5) * 6,
                    3 + context.random.nextInt(8),
                )
                context.spawn(world, orb)
            }
        }
    }
}

internal class LifeBlessingEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.activePlayers().forEach { player ->
            context.applyTemporaryStatusEffect(player, StatusEffects.REGENERATION, 10 * 20, 1)
            context.message(player, "§d💚 生命赐福！获得 10 秒生命恢复 II！")
        }
        context.broadcast("§d💚 特殊事件「生命赐福」已触发！")
    }
}

internal class OreUnderfootEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private val ores: List<Block> = listOf(
        Blocks.COAL_ORE,
        Blocks.DEEPSLATE_COAL_ORE,
        Blocks.IRON_ORE,
        Blocks.DEEPSLATE_IRON_ORE,
        Blocks.COPPER_ORE,
        Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.GOLD_ORE,
        Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.DIAMOND_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.REDSTONE_ORE,
        Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.LAPIS_ORE,
        Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.EMERALD_ORE,
        Blocks.DEEPSLATE_EMERALD_ORE,
    )

    override fun onStart() {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            val origin = player.blockPos
            for (dx in -2..2) for (dy in -2..2) for (dz in -2..2) {
                val pos = origin.add(dx, dy, dz)
                if (world.getBlockState(pos).isAir) continue
                val ore = ores[context.random.nextInt(ores.size)].defaultState
                context.temporaryBlocks.replace(world, pos, ore)
            }
            context.message(player, "§b⛏ 脚下出矿！附近方块暂时变成矿石，10 秒后恢复！")
        }
        context.broadcast("§b⛏ 特殊事件「脚下出矿」已触发！方块实体、基岩和不可破坏方块不会被替换。")
    }

    override fun onFinish() = context.temporaryBlocks.restore()

    override fun onCleanup() = context.temporaryBlocks.restore()
}

internal class AnvilStormEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private val fallingAnvils = mutableListOf<Entity>()

    override fun onStart() {
        context.broadcast("§c🔨 特殊事件「铁砧暴雨」已触发！铁砧将持续降落 10 秒！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            val count = 2 + if (context.random.nextFloat() < 0.5f) 1 else 0
            repeat(count) {
                val initial = BlockPos(
                    (player.x + (context.random.nextDouble() - 0.5) * 10).toInt(),
                    (player.y + 10).toInt(),
                    (player.z + (context.random.nextDouble() - 0.5) * 10).toInt(),
                )
                val pos = (0..10)
                    .asSequence()
                    .map(initial::up)
                    .firstOrNull { world.getBlockState(it).isAir }
                    ?: return@repeat
                val anvil = FallingBlockEntity.spawnFromBlock(world, pos, Blocks.ANVIL.defaultState)
                anvil.setHurtEntities(2f, 40)
                // 防止事件铁砧落地后变成不受追踪的永久方块。
                anvil.setDestroyedOnLanding()
                context.tag(anvil)
                fallingAnvils += anvil
            }
        }
    }

    override fun onFinish() = discardRemaining()

    override fun onCleanup() = discardRemaining()

    private fun discardRemaining() {
        context.discardTrackedEntities()
        fallingAnvils.clear()
    }
}

internal class CaveInEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.broadcast("§8⛏ 特殊事件「地底塌陷」已触发！脚下方块将持续破碎 30 秒！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            val origin = player.blockPos
            for (dx in -1..1) for (dz in -1..1) {
                val pos = origin.add(dx, -1, dz)
                val state = world.getBlockState(pos)
                if (state.isAir || state.isOf(Blocks.BEDROCK)) continue
                if (world.getBlockEntity(pos) != null || state.getHardness(world, pos) < 0f) continue
                world.breakBlock(pos, true)
            }
        }
    }
}

internal class FireTrailEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.broadcast("§c🔥 特殊事件「脚步生火」已触发！火焰将在事件结束时恢复清理。")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            val feet = player.blockPos
            val ground = feet.down()
            if (world.getBlockState(feet).isAir && world.getBlockState(ground).isSolidBlock(world, ground)) {
                context.temporaryBlocks.replace(world, feet, Blocks.FIRE.defaultState)
            }
        }
    }

    override fun onFinish() = context.temporaryBlocks.restore()

    override fun onCleanup() = context.temporaryBlocks.restore()
}

internal class CageTrialEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private val creeperSpawns = linkedMapOf<java.util.UUID, Pair<net.minecraft.registry.RegistryKey<net.minecraft.world.World>, BlockPos>>()

    override fun onStart() {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            val origin = player.blockPos
            for (dy in 0 until 4) for (dx in -1..1) for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val pos = origin.add(dx, dy, dz)
                if (world.getBlockState(pos).isAir) {
                    context.temporaryBlocks.replace(world, pos, Blocks.IRON_BARS.defaultState)
                }
            }
            creeperSpawns[player.uuid] = world.registryKey to origin.add(0, 2, 0).toImmutable()
            context.message(player, "§c🔒 囚笼试炼！5 秒后笼内会出现苦力怕！")
        }
        context.broadcast("§c🔒 特殊事件「囚笼试炼」已触发！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        if (elapsedSeconds + 1 != 5) return
        creeperSpawns.forEach { (playerId, location) ->
            val player = context.server.playerManager.getPlayer(playerId) ?: return@forEach
            if (context.objectiveId(player) == null) return@forEach
            val world = context.server.getWorld(location.first) ?: return@forEach
            val creeper = CreeperEntity(EntityType.CREEPER, world)
            creeper.refreshPositionAndAngles(
                location.second.x + 0.5,
                location.second.y.toDouble(),
                location.second.z + 0.5,
                context.random.nextFloat() * 360f,
                0f,
            )
            context.spawn(world, creeper)
            context.message(player, "§c💥 苦力怕出现了！")
        }
        creeperSpawns.clear()
    }

    override fun onFinish() = restore()

    override fun onCleanup() = restore()

    private fun restore() {
        context.temporaryBlocks.restore()
        creeperSpawns.clear()
    }
}

internal class CropSpeedGrowEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    private val flowers: List<Block> = listOf(
        Blocks.DANDELION,
        Blocks.POPPY,
        Blocks.BLUE_ORCHID,
        Blocks.ALLIUM,
        Blocks.AZURE_BLUET,
        Blocks.RED_TULIP,
        Blocks.ORANGE_TULIP,
        Blocks.WHITE_TULIP,
        Blocks.PINK_TULIP,
        Blocks.OXEYE_DAISY,
        Blocks.CORNFLOWER,
        Blocks.LILY_OF_THE_VALLEY,
    )

    override fun onStart() {
        context.broadcast("§a🌱 特殊事件「作物速成」已触发！附近作物、树苗和花草将快速生长！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            val origin = player.blockPos
            for (dx in -3..3) for (dz in -3..3) for (dy in -1..3) {
                val pos = origin.add(dx, dy, dz)
                val state = world.getBlockState(pos)
                when (val block = state.block) {
                    is CropBlock -> world.setBlockState(pos, block.withAge(block.maxAge))
                    is SaplingBlock -> if (block.isFertilizable(world, pos, state)) {
                        block.grow(world, world.random, pos, state)
                    }
                }
            }
            for (dx in -7..7) for (dz in -7..7) {
                if (context.random.nextFloat() > 0.3f) continue
                val pos = origin.add(dx, 0, dz)
                val ground = world.getBlockState(pos.down()).block
                if (world.getBlockState(pos).isAir && ground in FLOWER_GROUNDS) {
                    world.setBlockState(pos, flowers[context.random.nextInt(flowers.size)].defaultState)
                }
            }
        }
    }

    private companion object {
        val FLOWER_GROUNDS = setOf(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.MYCELIUM)
    }
}

internal class SlimePossessionEvent(context: DDISpecialEventContext) : DDIBaseSpecialEvent(context) {
    override fun onStart() {
        context.broadcast("§a🟢 特殊事件「粘液附身」已触发！脚底方块会暂时变成粘液块！")
    }

    override fun onTickSecond(elapsedSeconds: Int, remainingSeconds: Int) {
        context.activePlayers().forEach { player ->
            val world = player.entityWorld as? ServerWorld ?: return@forEach
            val origin = player.blockPos
            for (dx in 0 until 4) for (dz in 0 until 4) {
                context.temporaryBlocks.replace(
                    world,
                    origin.add(dx - 1, -1, dz - 1),
                    Blocks.SLIME_BLOCK.defaultState,
                )
            }
        }
    }

    override fun onFinish() = context.temporaryBlocks.restore()

    override fun onCleanup() = context.temporaryBlocks.restore()
}
