package me.jfenn.bingo.impl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.mojang.serialization.JsonOps
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.item.*
import net.minecraft.component.ComponentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.*
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.RawFilteredPair
import net.minecraft.util.Identifier
import net.minecraft.util.Unit
import org.slf4j.Logger

class ItemStackFactory(
    private val logger: Logger,
    server: MinecraftServer?
) : IItemStackFactory {

    private val jsonOps = server?.registryManager?.getOps(JsonOps.INSTANCE) ?: JsonOps.INSTANCE
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    override val emptyStack: IItemStack = ItemStackImpl(ItemStack.EMPTY)

    override fun listItems(server: MinecraftServer): List<String> {
        return Registries.ITEM.streamEntries()
            .map { it.registryKey().value.toString() }
            .filter { isEnabledInWorld(it, server) }
            .toList()
    }

    override fun isEnabledInWorld(item: String, server: MinecraftServer): Boolean {
        val itemInstance = try {
            createStack(item, 1).item
        } catch (e: Throwable) {
            return false
        }

        val features = listOf(FeatureFlags.TRADE_REBALANCE, FeatureFlags.BUNDLE)
        val requiredFeatures = when {
            // hack: when requesting bundle, pretend that it's behind the BUNDLE feature flag (as it should be)
            item.equals("minecraft:bundle", ignoreCase = true) -> {
                itemInstance.requiredFeatures.combine(FeatureSet.of(FeatureFlags.BUNDLE))
            }
            else -> itemInstance.requiredFeatures
        }
        return features.all {
            when {
                requiredFeatures.contains(it) -> server.overworld.enabledFeatures.contains(it)
                else -> true
            }
        }
    }

    override fun createStack(item: String, count: Int): IItemStack {
        return createStack(Identifier.of(item), count)
    }

    override fun createStack(item: Identifier, count: Int): IItemStack {
        val itemInstance = Registries.ITEM.get(item)
        if (itemInstance == Items.AIR && !item.path.equals("air", ignoreCase = true))
            throw IllegalArgumentException("[ItemStackFactory] Item $item not found")
        return createStack(itemInstance, count)
    }

    override fun createStack(item: Item, count: Int): IItemStack {
        return forStack(ItemStack(item, count))
    }

    override fun forStack(stack: ItemStack?): IItemStack {
        return stack?.let(::ItemStackImpl) ?: emptyStack
    }

    override fun createFilledMap(): IFilledMap = createStack(Items.FILLED_MAP, 1).asFilledMap()!!

    override fun createFireworkRocket(): IFireworkRocket = createStack(Items.FIREWORK_ROCKET, 1).asFireworkRocket()!!

    override fun createWrittenBook(): IWrittenBook = createStack(Items.WRITTEN_BOOK, 1).asWrittenBook()!!

    override fun createPlayerHead(): IPlayerHead = createStack(Items.PLAYER_HEAD, 1).asPlayerHead()!!

    open inner class ItemStackImpl(
        override val stack: ItemStack,
    ) : IItemStack {
        override val item: Item
            get() = stack.item

        override val identifier: Identifier
            get() = Registries.ITEM.getId(item)

        override val displayName: IText
            get() = stack.name.let { TextImpl(it.copy()) }

        override val lore: List<IText>
            get() = stack[DataComponentTypes.LORE]?.lines?.map { TextImpl(it.copy()) } ?: emptyList()

        override var count: Int
            get() = stack.count
            set(value) {
                stack.count = value
            }

        override val maxCount: Int
            get() = stack.maxCount

        override fun addCustomTag(tag: String) {
            val nbt = stack[DataComponentTypes.CUSTOM_DATA]?.copyNbt() ?: NbtCompound()
            nbt.putBoolean(tag, true)
            stack[DataComponentTypes.CUSTOM_DATA] = NbtComponent.of(nbt)
        }

        override fun removeCustomTag(tag: String) {
            val nbt = stack[DataComponentTypes.CUSTOM_DATA]?.copyNbt() ?: NbtCompound()
            nbt.remove(tag)
            if (nbt.isEmpty) {
                stack.remove(DataComponentTypes.CUSTOM_DATA)
            } else {
                stack[DataComponentTypes.CUSTOM_DATA] = NbtComponent.of(nbt)
            }
        }

        override fun hasCustomTag(tag: String): Boolean {
            return stack[DataComponentTypes.CUSTOM_DATA]?.copyNbt()?.contains(tag) == true
        }

        override fun setDisplay(name: IText?, lore: List<IText>?) {
            stack[DataComponentTypes.CUSTOM_NAME] = name?.value
            stack[DataComponentTypes.LORE] = lore?.let { LoreComponent(it.map { t -> t.value }) }
        }

        override fun setUnbreakable(value: Boolean) {
            stack[DataComponentTypes.UNBREAKABLE] = UnbreakableComponent(true)
        }

        override fun setHideFlags(hideFlags: Int) {
            stack[DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP] = Unit.INSTANCE
            stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).let {
                stack[DataComponentTypes.ENCHANTMENTS] = it.withShowInTooltip(false)
            }
            stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT).let {
                stack[DataComponentTypes.ATTRIBUTE_MODIFIERS] = it.withShowInTooltip(false)
            }
        }

        override fun setNbtString(nbt: String?) = true

        override fun getNbtString(): String? = null

        override fun setComponentsString(components: Map<String, String?>): Boolean {
            var isSuccessful = true

            for ((key, value) in components) {
                val id = Identifier.tryParse(key)
                val type = id?.let { Registries.DATA_COMPONENT_TYPE.get(it) }

                if (id == null || type == null) {
                    logger.error("[ItemStackFactory] Error parsing item component $key for ${item.translationKey}: not a valid identifier")
                    isSuccessful = false
                    continue
                }

                if (value == null) {
                    stack.remove(type)
                    continue
                }

                val jsonElement = gson.fromJson(value, JsonElement::class.java)
                if (jsonElement.isJsonNull) {
                    stack.remove(type)
                    continue
                }

                val component = try {
                    type.codecOrThrow
                        .decode(jsonOps, jsonElement)
                        .getOrThrow()
                        .first
                } catch (e: Throwable) {
                    logger.error("[ItemStackFactory] Error parsing item component $key for ${item.translationKey}", e)
                    isSuccessful = false
                    null
                }

                stack.set(@Suppress("UNCHECKED_CAST") (type as ComponentType<Any>), component)
            }

            return isSuccessful
        }

        override fun getComponentsString(): Map<String, String?>? {
            val components = mutableMapOf<String, String?>()

            for (component in stack.componentChanges.entrySet()) {
                if (component.key.shouldSkipSerialization()) continue
                val id = Registries.DATA_COMPONENT_TYPE.getId(component.key) ?: continue

                val jsonElement = try {
                    stack.components
                        .find { it.type == component.key }
                        ?.encode(jsonOps)
                        ?.getOrThrow()
                        ?.let { gson.toJson(it) }
                } catch (e: Throwable) {
                    logger.warn("Unable to encode component '${component.key}'", e)
                    null
                }

                if (jsonElement != null) {
                    components[id.toString()] = jsonElement
                }
            }

            return components.takeIf { it.isNotEmpty() }
        }

        private fun gsonOverlapsRecursive(predicate: JsonElement, actual: JsonElement): Boolean {
            if (predicate.isJsonNull && actual.isJsonNull) {
                return true
            }
            if (predicate.isJsonPrimitive && actual.isJsonPrimitive) {
                return predicate.asString == actual.asString
            }
            if (predicate.isJsonArray && actual.isJsonArray) {
                val predicateArr = predicate.asJsonArray
                val actualArr = actual.asJsonArray
                return predicateArr.all { predicateItem ->
                    actualArr.any { actualItem -> gsonOverlapsRecursive(predicateItem, actualItem) }
                }
            }
            if (predicate.isJsonObject && actual.isJsonObject) {
                val predicateObj = predicate.asJsonObject
                val actualObj = actual.asJsonObject
                return predicateObj.keySet().all {
                    gsonOverlapsRecursive(predicateObj.get(it) ?: JsonNull.INSTANCE, actualObj.get(it) ?: JsonNull.INSTANCE)
                }
            }
            return false
        }

        override fun isDataOverlapping(nbt: String?, components: Map<String, String?>?): Boolean {
            if (components == null) return true

            val itemComponents = getComponentsString()
                ?.mapValues { (_, value) -> value?.let { gson.fromJson(it, JsonElement::class.java) } }
                ?: return components.isEmpty()

            val predicateComponents = try {
                components.mapValues { (_, value) ->
                    value?.let { gson.fromJson(it, JsonElement::class.java) }
                }
            } catch (e: Throwable) {
                logger.error("[ItemStackFactory] Error parsing item components for ${item.translationKey} predicate", e)
                return false
            }

            return predicateComponents.all { (key, predicateComponent) ->
                val itemComponent = itemComponents[key]?.takeIf { !it.isJsonNull }
                when {
                    predicateComponent == null -> itemComponent != null
                    itemComponent == null -> false
                    else -> gsonOverlapsRecursive(predicateComponent, itemComponent)
                }
            }
        }

        override fun copy(): IItemStack {
            return ItemStackImpl(stack.copy())
        }

        override fun asFilledMap(): IFilledMap? {
            return (this as? FilledMapImpl)
                ?: takeIf { item == Items.FILLED_MAP }
                    ?.let { FilledMapImpl(stack) }
        }

        override fun asFireworkRocket(): IFireworkRocket? {
            return (this as? FireworkRocketImpl)
                ?: takeIf { item == Items.FIREWORK_ROCKET }
                    ?.let { FireworkRocketImpl(stack) }
        }

        override fun asWrittenBook(): IWrittenBook? {
            return (this as? WrittenBookImpl)
                ?: takeIf { item == Items.WRITTEN_BOOK }
                    ?.let { WrittenBookImpl(stack) }
        }

        override fun asPlayerHead(): IPlayerHead? {
            return (this as? PlayerHeadImpl)
                ?: takeIf { item == Items.PLAYER_HEAD }
                    ?.let { PlayerHeadImpl(stack) }
        }

    }

    inner class WrittenBookImpl(
        override val stack: ItemStack,
    ) : ItemStackImpl(stack), IWrittenBook {
        override var title: String?
            get() = stack[DataComponentTypes.WRITTEN_BOOK_CONTENT]?.title?.raw
            set(value) {
                val original = stack[DataComponentTypes.WRITTEN_BOOK_CONTENT]
                stack[DataComponentTypes.WRITTEN_BOOK_CONTENT] = WrittenBookContentComponent(
                    value?.let { RawFilteredPair.of(value) }
                        ?: RawFilteredPair.of(""),
                    original?.author ?: "",
                    original?.generation ?: 0,
                    original?.pages() ?: emptyList(),
                    original?.resolved ?: true
                )
            }

        override var author: String?
            get() = stack[DataComponentTypes.WRITTEN_BOOK_CONTENT]?.author
            set(value) {
                val original = stack[DataComponentTypes.WRITTEN_BOOK_CONTENT]
                stack[DataComponentTypes.WRITTEN_BOOK_CONTENT] = WrittenBookContentComponent(
                    original?.title ?: RawFilteredPair.of(""),
                    value ?: "",
                    original?.generation ?: 0,
                    original?.pages() ?: emptyList(),
                    original?.resolved ?: true
                )
            }

        override fun setPages(pages: List<IText>) {
            val original = stack[DataComponentTypes.WRITTEN_BOOK_CONTENT]
            stack[DataComponentTypes.WRITTEN_BOOK_CONTENT] = WrittenBookContentComponent(
                original?.title ?: RawFilteredPair.of(""),
                original?.author ?: "",
                original?.generation ?: 0,
                pages.map { RawFilteredPair.of(it.value) },
                original?.resolved ?: true
            )
        }
    }

    inner class FilledMapImpl(
        override val stack: ItemStack,
    ) : ItemStackImpl(stack), IFilledMap {
        override var mapId: Int?
            get() = stack[DataComponentTypes.MAP_ID]?.id
            set(value) {
                stack[DataComponentTypes.MAP_ID] = value?.let { MapIdComponent(it) }
            }

        override var mapColor: Int?
            get() = stack[DataComponentTypes.MAP_COLOR]?.rgb
            set(value) {
                stack[DataComponentTypes.MAP_COLOR] = value?.let { MapColorComponent(it) }
            }
    }

    inner class FireworkRocketImpl(
        override val stack: ItemStack,
    ) : ItemStackImpl(stack), IFireworkRocket {
        override var fireworks: List<kotlin.Unit>?
            get() = emptyList()
            set(value) {
                stack[DataComponentTypes.FIREWORKS] = value?.let { FireworksComponent(0, emptyList()) }
            }
    }

    inner class PlayerHeadImpl(
        override val stack: ItemStack,
    ) : ItemStackImpl(stack), IPlayerHead {
        override fun setSkullOwner(player: ServerPlayerEntity) {
            stack[DataComponentTypes.PROFILE] = ProfileComponent(player.gameProfile)
        }
    }
}
