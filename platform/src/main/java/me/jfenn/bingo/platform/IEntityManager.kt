package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import org.joml.Matrix4f
import org.joml.Vector3d
import java.util.*

interface IEntityManager {
    fun <T: IEntity> createEntity(type: EntityType<T>, world: ServerWorld): T
    fun getEntity(world: ServerWorld, uuid: UUID): IEntity?
    fun spawnEntity(world: ServerWorld, entity: IEntity): Boolean
    fun iterateEntities(world: ServerWorld): Sequence<IEntity>
}

class EntityType<T: IEntity>(val name: String) {
    companion object {
        val TEXT_DISPLAY = EntityType<ITextDisplayEntity>("TEXT_DISPLAY")
        val BLOCK_DISPLAY = EntityType<IBlockDisplayEntity>("BLOCK_DISPLAY")
        val INTERACTION = EntityType<IInteractionEntity>("INTERACTION")
        val ARMOR_STAND = EntityType<IArmorStandEntity>("ARMOR_STAND")
        val TNT = EntityType<ITntEntity>("TNT")
        val BAT = EntityType<IBatEntity>("BAT")
        val OTHER = EntityType<IBatEntity>("OTHER")
    }

    override fun toString(): String {
        return name
    }
}

interface IEntity {
    val type: EntityType<*> get() = EntityType.OTHER
    val entity: Entity
    var uuid: UUID
    var pos: Vector3d
    var commandTags: Set<String>
    var pitch: Float
    var yaw: Float

    fun discard()
}

interface ILivingEntity : IEntity {
    var invulnerable: Boolean
    var noGravity: Boolean
    var silent: Boolean
    var noAI: Boolean
    var persistenceRequired: Boolean

    var health: Float
    val maxHealth: Float
    var air: Int
    val maxAir: Int

    fun addEffect(type: EffectType, duration: Int, amplifier: Int, ambient: Boolean, visible: Boolean): IStatusEffectHandle
    fun getEffects(): List<IStatusEffectHandle>
    fun removeEffect(effect: IStatusEffectHandle)
}

interface IInteractionEntity : IEntity {
    override val type get() = EntityType.INTERACTION
    var width: Float
    var height: Float
}

interface IDisplayEntity : IEntity {
    var transformation: Matrix4f
    var brightness: Brightness?

    data class Brightness(
        val block: Int,
        val sky: Int,
    )
}

interface ITextDisplayEntity : IDisplayEntity {
    enum class Billboard { FIXED, VERTICAL }
    enum class TextAlignment { CENTER, LEFT, RIGHT }

    override val type get() = EntityType.TEXT_DISPLAY
    var value: IText
    var lineWidth: Int
    var billboard: Billboard
    var alignment: TextAlignment
    var background: Int
    var shadow: Boolean
}

interface IBlockDisplayEntity : IDisplayEntity {
    var blockIdentifier: String
}

interface IArmorStandEntity : IEntity {
    override val type get() = EntityType.ARMOR_STAND

    enum class EquipmentSlot { MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD }
    fun equipStack(slot: EquipmentSlot, stack: IItemStack)
}

interface IBatEntity : ILivingEntity {
    override val type get() = EntityType.BAT
}

interface ITntEntity : IEntity {
    override val type get() = EntityType.TNT
    var fuse: Int
}
