package me.jfenn.bingo.impl

import me.jfenn.bingo.common.utils.toNbt
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextFactory
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.TntEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity
import net.minecraft.entity.decoration.InteractionEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.passive.BatEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.world.ServerWorld
import org.joml.Matrix4f
import org.joml.Vector3d
import java.util.*
import kotlin.reflect.KProperty

class EntityManagerImpl(
    private val textFactory: ITextFactory,
    private val textSerializer: ITextSerializer,
) : IEntityManager {
    private fun EntityType<*>.toMinecraftType(): net.minecraft.entity.EntityType<*> {
        return when (this) {
            EntityType.INTERACTION -> net.minecraft.entity.EntityType.INTERACTION
            EntityType.TEXT_DISPLAY -> net.minecraft.entity.EntityType.TEXT_DISPLAY
            EntityType.BLOCK_DISPLAY -> net.minecraft.entity.EntityType.BLOCK_DISPLAY
            EntityType.ARMOR_STAND -> net.minecraft.entity.EntityType.ARMOR_STAND
            EntityType.TNT -> net.minecraft.entity.EntityType.TNT
            EntityType.BAT -> net.minecraft.entity.EntityType.BAT
            else -> error("Entity type not recognized!")
        }
    }

    private fun forEntity(entity: Entity): IEntity {
        val impl = when (entity) {
            is InteractionEntity -> InteractionEntityImpl(entity)
            is TextDisplayEntity -> TextDisplayEntityImpl(entity, textFactory, textSerializer)
            is BlockDisplayEntity -> BlockDisplayEntityImpl(entity)
            is ArmorStandEntity -> ArmorStandEntityImpl(entity)
            is TntEntity -> TntEntityImpl(entity)
            is BatEntity -> BatEntityImpl(entity)
            is LivingEntity -> LivingEntityImpl(entity)
            else -> EntityImpl(entity)
        }
        return impl
    }

    override fun <T : IEntity> createEntity(type: EntityType<T>, world: ServerWorld): T {
        val mcType = type.toMinecraftType()
        val entity = mcType.create(world)!!
        val impl = forEntity(entity)
        return (@Suppress("UNCHECKED_CAST") (impl as T))
    }

    override fun getEntity(world: ServerWorld, uuid: UUID): IEntity? {
        val entity = world.getEntity(uuid) ?: return null
        return forEntity(entity)
    }

    override fun spawnEntity(world: ServerWorld, entity: IEntity): Boolean {
        return world.spawnEntity(entity.entity)
    }

    override fun iterateEntities(world: ServerWorld): Sequence<IEntity> {
        return world.iterateEntities()
            .asSequence()
            .map { forEntity(it) }
    }
}

private class NbtBooleanDelegate(private val name: String) {
    operator fun getValue(thisRef: EntityImpl, property: KProperty<*>): Boolean = thisRef.nbt.getBoolean(name)
    operator fun setValue(thisRef: EntityImpl, property: KProperty<*>, value: Boolean) {
        return thisRef.patchNbt { putBoolean(name, value) }
    }
}

private class NbtIntDelegate(private val name: String) {
    operator fun getValue(thisRef: EntityImpl, property: KProperty<*>): Int = thisRef.nbt.getInt(name)
    operator fun setValue(thisRef: EntityImpl, property: KProperty<*>, value: Int) {
        return thisRef.patchNbt { putInt(name, value) }
    }
}

private class NbtFloatDelegate(private val name: String) {
    operator fun getValue(thisRef: EntityImpl, property: KProperty<*>): Float = thisRef.nbt.getFloat(name)
    operator fun setValue(thisRef: EntityImpl, property: KProperty<*>, value: Float) {
        return thisRef.patchNbt { putFloat(name, value) }
    }
}

open class EntityImpl(
    override val entity: Entity
) : IEntity {
    override var uuid: UUID
        get() = entity.uuid
        set(value) {
            entity.uuid = value
        }

    override var pos: Vector3d
        get() = entity.pos.let { Vector3d(it.x, it.y, it.z) }
        set(value) {
            entity.setPosition(value.x, value.y, value.z)
        }

    override var commandTags: Set<String>
        get() = entity.commandTags.toSet()
        set(value) {
            entity.commandTags.toSet().forEach { entity.removeScoreboardTag(it) }
            value.forEach { entity.addCommandTag(it) }
        }

    override var pitch: Float
        get() = entity.pitch
        set(value) {
            entity.pitch = value
        }

    override var yaw: Float
        get() = entity.yaw
        set(value) {
            entity.yaw = value
        }

    override fun discard() {
        entity.discard()
    }

    internal var nbt
        get() = NbtCompound().also { entity.writeNbt(it) }
        set(value) {
            entity.readNbt(value)
        }

    internal fun <R> patchNbt(patch: NbtCompound.() -> R): R {
        val compound = nbt
        val ret = patch(compound)
        nbt = compound
        return ret
    }
}

class InteractionEntityImpl(
    override val entity: InteractionEntity
) : IInteractionEntity, EntityImpl(entity) {
    override val type: EntityType<IInteractionEntity>
        get() = EntityType.INTERACTION
    override var width: Float by NbtFloatDelegate("width")
    override var height: Float by NbtFloatDelegate("height")
}

open class DisplayEntityImpl(
    override val entity: DisplayEntity
) : IDisplayEntity, EntityImpl(entity) {
    override var transformation: Matrix4f
        get() = Matrix4f()
        set(value) {
            patchNbt { put("transformation", value.toNbt()) }
        }

    override var brightness: IDisplayEntity.Brightness?
        get() = nbt
            .takeIf { it.contains("brightness") }
            ?.getCompound("brightness")
            ?.let {
                IDisplayEntity.Brightness(
                    block = it.getInt("block"),
                    sky = it.getInt("sky"),
                )
            }
        set(value) {
            patchNbt {
                if (value != null) {
                    put("brightness", NbtCompound().apply {
                        putInt("block", value.block)
                        putInt("sky", value.sky)
                    })
                } else {
                    remove("brightness")
                }
            }
        }
}

class TextDisplayEntityImpl(
    override val entity: TextDisplayEntity,
    private val textFactory: ITextFactory,
    private val textSerializer: ITextSerializer,
) : ITextDisplayEntity, DisplayEntityImpl(entity) {
    override val type: EntityType<ITextDisplayEntity>
        get() = EntityType.TEXT_DISPLAY

    override var value: IText
        get() = nbt.getString("text")
            ?.let { textSerializer.fromJson(it) }
            ?.let { textFactory.from(it) }
            ?: textFactory.empty()
        set(value) {
            patchNbt {
                putString("text", textSerializer.toJson(value.value))
            }
        }

    override var lineWidth: Int by NbtIntDelegate("line_width")

    override var billboard: ITextDisplayEntity.Billboard
        get() = nbt.getString("billboard")
            .uppercase()
            .let { ITextDisplayEntity.Billboard.valueOf(it) }
        set(value) {
            patchNbt { putString("billboard", value.name.lowercase()) }
        }

    override var alignment: ITextDisplayEntity.TextAlignment
        get() = nbt.getString("alignment")
            .uppercase()
            .let { ITextDisplayEntity.TextAlignment.valueOf(it) }
        set(value) {
            patchNbt { putString("alignment", value.name.lowercase()) }
        }

    override var background: Int by NbtIntDelegate("background")
    override var shadow: Boolean by NbtBooleanDelegate("shadow")
}

class BlockDisplayEntityImpl(
    override val entity: BlockDisplayEntity,
) : IBlockDisplayEntity, DisplayEntityImpl(entity) {
    override val type: EntityType<IBlockDisplayEntity>
        get() = EntityType.BLOCK_DISPLAY

    override var blockIdentifier: String
        get() = nbt.getCompound("block_state").getString("Name")
        set(value) {
            patchNbt {
                put("block_state", NbtCompound().apply {
                    putString("Name", value)
                })
            }
        }
}

class ArmorStandEntityImpl(
    override val entity: ArmorStandEntity,
) : IArmorStandEntity, EntityImpl(entity) {
    override fun equipStack(slot: IArmorStandEntity.EquipmentSlot, stack: IItemStack) {
        val mcSlot = when (slot) {
            IArmorStandEntity.EquipmentSlot.MAINHAND -> EquipmentSlot.MAINHAND
            IArmorStandEntity.EquipmentSlot.OFFHAND -> EquipmentSlot.OFFHAND
            IArmorStandEntity.EquipmentSlot.HEAD -> EquipmentSlot.HEAD
            IArmorStandEntity.EquipmentSlot.CHEST -> EquipmentSlot.CHEST
            IArmorStandEntity.EquipmentSlot.LEGS -> EquipmentSlot.LEGS
            IArmorStandEntity.EquipmentSlot.FEET -> EquipmentSlot.FEET
        }
        entity.equipStack(mcSlot, stack.stack)
    }
}

class TntEntityImpl(
    override val entity: TntEntity,
) : ITntEntity, EntityImpl(entity) {
    override var fuse: Int by entity::fuse
}

open class LivingEntityImpl(
    final override val entity: LivingEntity
) : ILivingEntity, EntityImpl(entity) {
    override var invulnerable: Boolean by NbtBooleanDelegate("Invulnerable")
    override var noGravity: Boolean by NbtBooleanDelegate("NoGravity")
    override var silent: Boolean by NbtBooleanDelegate("Silent")
    override var noAI: Boolean by NbtBooleanDelegate("NoAI")
    override var persistenceRequired: Boolean by NbtBooleanDelegate("PersistenceRequired")

    override var health: Float by entity::health
    override val maxHealth: Float by entity::maxHealth

    override var air: Int by entity::air
    override val maxAir: Int by entity::maxAir

    override fun addEffect(
        type: EffectType,
        duration: Int,
        amplifier: Int,
        ambient: Boolean,
        visible: Boolean
    ): IStatusEffectHandle {
        val instance = StatusEffectInstance(
            when (type) {
                EffectType.NIGHT_VISION -> StatusEffects.NIGHT_VISION
                EffectType.SLOWNESS -> StatusEffects.SLOWNESS
                EffectType.JUMP_BOOST -> StatusEffects.JUMP_BOOST
                EffectType.INVISIBILITY -> StatusEffects.INVISIBILITY
                EffectType.OTHER -> throw IllegalArgumentException("[StatusEffectsImpl] OTHER is not a valid effect type!")
            },
            duration,
            amplifier,
            ambient,
            visible
        )
        entity.addStatusEffect(instance)
        return StatusEffectHandle(instance)
    }

    override fun getEffects(): List<IStatusEffectHandle> {
        return entity.statusEffects.map { StatusEffectHandle(it) }
    }

    override fun removeEffect(effect: IStatusEffectHandle) {
        require(effect is StatusEffectHandle)
        entity.removeStatusEffect(effect.instance.effectType)
    }
}

class BatEntityImpl(
    entity: BatEntity
) : IBatEntity, LivingEntityImpl(entity)
