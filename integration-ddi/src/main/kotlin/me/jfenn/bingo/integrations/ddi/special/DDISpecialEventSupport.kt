package me.jfenn.bingo.integrations.ddi.special

import me.jfenn.bingo.common.options.DDISpecialEventType
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import kotlin.math.max

internal fun DDISpecialEventContext.broadcast(message: String) {
    callbacks.broadcast(Text.literal(message))
}

internal fun DDISpecialEventContext.message(
    player: ServerPlayerEntity,
    message: String,
    actionBar: Boolean = true,
) {
    callbacks.message(player, Text.literal(message), actionBar)
}

internal fun DDISpecialEventContext.activeObjectiveGroups(): Map<String, List<ServerPlayerEntity>> =
    activePlayers().mapNotNull { player ->
        objectiveId(player)?.let { it to player }
    }.groupBy(keySelector = { it.first }, valueTransform = { it.second })

internal fun DDISpecialEventContext.objectiveRepresentatives(): List<ServerPlayerEntity> =
    activeObjectiveGroups().values.mapNotNull { players ->
        if (players.isEmpty()) null else players[random.nextInt(players.size)]
    }

internal fun DDISpecialEventContext.adjustHeart(
    objectiveId: String,
    delta: Int,
    actor: ServerPlayerEntity? = null,
): DDISpecialHeartAdjustment = callbacks.adjustHeart(
    objectiveId = objectiveId,
    delta = delta,
    eventType = definition.type,
    actorId = actor?.uuid,
)

internal fun DDISpecialEventContext.adjustHeartOnce(
    settledObjectives: MutableSet<String>,
    player: ServerPlayerEntity,
    delta: Int,
): DDISpecialHeartAdjustment? {
    val objectiveId = objectiveId(player) ?: return null
    if (!settledObjectives.add(objectiveId)) return null
    return adjustHeart(objectiveId, delta, player)
}

internal fun ServerPlayerEntity.countItem(item: Item): Int {
    var count = 0
    for (slot in 0 until inventory.size()) {
        val stack = inventory.getStack(slot)
        if (stack.isOf(item)) count += stack.count
    }
    return count
}

internal fun ServerPlayerEntity.giveOrDrop(stack: ItemStack) {
    val remaining = stack.copy()
    inventory.insertStack(remaining)
    if (!remaining.isEmpty) dropItem(remaining, false)
}

internal fun findGroundNear(
    player: ServerPlayerEntity,
    initial: BlockPos,
    maxDown: Int = 5,
): BlockPos {
    val world = player.entityWorld
    for (offset in 0..max(0, maxDown)) {
        val candidate = initial.down(offset)
        if (world.getBlockState(candidate).isSolidBlock(world, candidate)) return candidate.up()
    }
    return initial
}

internal val DDISpecialEventType.displayName: String
    get() = DDISpecialEventCatalog[this].displayName
