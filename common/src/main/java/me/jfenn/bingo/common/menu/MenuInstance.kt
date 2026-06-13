package me.jfenn.bingo.common.menu

import me.jfenn.bingo.common.event.InteractionEntityEvents
import me.jfenn.bingo.platform.IEntity
import me.jfenn.bingo.platform.IEntityManager
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import org.joml.Matrix4d
import org.joml.Vector3d
import org.koin.core.scope.Scope
import org.slf4j.Logger
import kotlin.math.roundToInt

internal class MenuInstance(
    private val log: Logger,
    koinScope: Scope,
    private val world: ServerWorld,
    private val entityManager: IEntityManager,
    private val interactionEntityEvents: InteractionEntityEvents,
    private val matrix: Matrix4d,
    private val instanceTag: String,
) {

    private val menuComponent = component(koinScope) {
        val translation = Vector3d(0.5, 2.0, 0.05)
        if (matrix.get(0, 0) == -1.0 && matrix.get(1, 1) == 1.0 && matrix.get(2, 2) == -1.0) {
            // Fix for menu position being wacky in this specific rotation
            // (euler angles turn into x=180, y=0 here - even though it should be y=180)
            translation.add(-1.0, 0.0, -1.0)
        }

        registerMenuPage(translation)
    }

    fun tick() {
        menuComponent.tick(this)
    }

    fun markDirty() {
        menuComponent.markDirty()
    }

    fun cleanup() {
        menuComponent.despawn()
    }

    private fun IEntity.resetPos() {
        pos = Vector3d()
        yaw = 0f
    }

    private fun IEntity.transformPos() {
        pos = matrix.transformPosition(pos)

        val angles = Vector3d()
        matrix.getEulerAnglesXYZ(angles)

        var yawDegrees = yaw + Math.toDegrees(-angles.x - angles.y).roundToInt()
        while (yawDegrees < 0) yawDegrees += 360f

        if (matrix.get(0, 0) == 0.0 && matrix.get(1, 1) == 1.0 && matrix.get(2, 2) == 0.0) {
            // Fix for menu position being wacky in this specific rotation
            yawDegrees *= -1
        }

        yaw = yawDegrees
    }

    fun <T: IEntity> spawn(info: MenuEntityHandle<T>): T? {
        @Suppress("UNCHECKED_CAST")
        var entity: T? = entityManager.getEntity(world, info.id)
            ?.takeIf { it.type == info.type }
                as? T

        if (entity == null) {
            entity = entityManager.createEntity(info.type, world)
            entity.uuid = info.id
            entity.commandTags = setOf(instanceTag)
            entity.resetPos()
            info.init.invoke(entity)
            entity.transformPos()

            val chunkPos = ChunkPos.toLong(BlockPos(entity.pos.x.toInt(), 0, entity.pos.z.toInt()))
            val isLoaded = world.isChunkLoaded(chunkPos)
            if (!isLoaded) {
                entity.discard()
                return null
            }

            // Try to spawn the entity into the world...
            val isSuccess = entityManager.spawnEntity(world, entity)
            // If the entity cannot be spawned, discard it and return null
            // (the MenuComponent should then be marked dirty, and will try again on the next tick)
            if (!isSuccess) {
                log.debug("Unable to spawn {} menu entity - discarding...", info.type)
                entity.discard()
                return null
            }
        } else {
            entity.commandTags = setOf(instanceTag)
            entity.resetPos()
            info.init.invoke(entity)
            entity.transformPos()
        }

        if (entity.type != info.type) {
            log.error("Entity type ${info.type} does not match the entity being updated")
            return null
        }

        info.onUpdate(entity)
        return entity
    }

    fun despawn(info: MenuEntityHandle<*>) {
        interactionEntityEvents.INTERACT_LISTENERS.remove(info.id)
        val entity = entityManager.getEntity(world, info.id)
        entity?.discard()
    }

}