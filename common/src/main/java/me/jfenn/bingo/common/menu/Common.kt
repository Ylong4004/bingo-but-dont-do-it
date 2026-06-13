package me.jfenn.bingo.common.menu

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.EventListener
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.EntityType
import me.jfenn.bingo.platform.IDisplayEntity
import me.jfenn.bingo.platform.IEntity
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.util.Formatting
import org.koin.core.scope.Scope
import java.util.*
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

internal const val MENU_TEXT_SCALE = 0.7f

internal const val MENU_LINE_HEIGHT = 0.3
internal const val MENU_LINE_PADDING = 0.1

internal const val MENU_TITLE_MATERIAL = "minecraft:lime_concrete"
internal const val MENU_BUTTON_MATERIAL = "minecraft:white_concrete"
internal const val MENU_BUTTON_OFF_MATERIAL = "minecraft:gray_concrete"

internal val MENU_BRIGHTNESS_ON = IDisplayEntity.Brightness(0, 15)
internal val MENU_BRIGHTNESS_OFF = IDisplayEntity.Brightness(0, 14)
internal val MENU_BRIGHTNESS_ALT = IDisplayEntity.Brightness(0, 8)

internal interface Property<T> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
}

internal interface MutableProperty<T>: Property<T> {
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

internal class ConstantProperty<T>(val value: T): Property<T> {
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }
}

internal class DelegatedProperty<T>(
    private var getter: () -> T,
    private var setter: (T) -> Unit,
): MutableProperty<T> {

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return getter()
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        setter(value)
    }

}

internal data class MenuEntityHandle<T: IEntity>(
    val id: UUID = UUID.randomUUID(),
    val type: EntityType<T>,
    val init: T.() -> Unit = {},
    val onUpdate: EventListener<T> = EventListener(),
)

internal fun component(koinScope: Scope, setup: MenuComponent.() -> Unit): MenuComponent {
    val component = MenuComponent(koinScope)
    component.setup()
    return component
}

internal open class MenuComponent(
    val koinScope: Scope,
) {

    val text: TextProvider by koinScope.inject()
    val eventBus: IEventBus by koinScope.inject()

    private var tracker: MenuInstance? = null
    private val entities: MutableList<MenuEntityHandle<*>> = mutableListOf()

    val onTick = EventListener<MenuInstance>()
    val onUpdate = EventListener<MenuInstance>()
    val onDespawn = EventListener<Unit>()

    private var isDirty = true

    fun markDirty() {
        isDirty = true
    }

    fun <T> propertyRef(ref: KMutableProperty0<T>): MutableProperty<T> {
        return DelegatedProperty(
            getter = {
                ref.get()
            },
            setter = {
                ref.set(it)
                markDirty()
            },
        )
    }

    fun <T> computedProperty(getter: () -> T): Property<T> {
        // this assumes that anything used in the getter is already observed
        // so this does not need to add any listeners
        return DelegatedProperty(getter) {}
    }

    fun <T: IEntity> registerEntity(type: EntityType<T>, init: T.() -> Unit = {}): MenuEntityHandle<T> {
        val handle = MenuEntityHandle(
            id = UUID.randomUUID(),
            type = type,
            init = init,
        )

        entities.add(handle)
        return handle
    }

    fun tick(tracker: MenuInstance) {
        onTick.invoke(tracker)
        if (isDirty)
            spawn(tracker)
    }

    fun spawn(tracker: MenuInstance) {
        this.tracker = tracker
        onUpdate.invoke(tracker)
        val spawned = entities.map {
            tracker.spawn(it)
        }
        isDirty = spawned.any { it == null }
    }

    fun despawn() {
        entities.forEach { tracker?.despawn(it) }
        onDespawn.invoke(Unit)
        this.tracker = null
    }
}

internal fun MenuComponent.buildTooltip(title: StringKey, formatting: Formatting = Formatting.GREEN): List<IText> {
    val description = StringKey.entries.find { it.key == "${title.key}.tooltip" }!!
    return buildList {
        add(text.string(title).formatted(formatting))
        add(text.string(description))
    }
}
