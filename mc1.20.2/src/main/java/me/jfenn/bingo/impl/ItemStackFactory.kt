package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.ITextSerializer
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.item.*
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.*
import net.minecraft.predicate.NbtPredicate
import net.minecraft.registry.Registries
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.slf4j.Logger

class ItemStackFactory(
    private val logger: Logger,
    private val textSerializer: ITextSerializer,
) : IItemStackFactory {

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

        val features = listOf(FeatureFlags.BUNDLE)
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
        return createStack(Identifier(item), count)
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
            get() = stack.nbt
                ?.takeIf { it.getType("Lore") == NbtElement.LIST_TYPE }
                ?.getList("Lore", NbtElement.STRING_TYPE.toInt())
                ?.filterIsInstance<NbtString>()
                ?.mapNotNull { Text.Serializer.fromJson(it.asString()) }
                ?.map { TextImpl(it.copy()) }
                ?: emptyList()

        override var count: Int
            get() = stack.count
            set(value) {
                stack.count = value
            }

        override val maxCount: Int
            get() = stack.maxCount

        override fun addCustomTag(tag: String) {
            stack.getOrCreateNbt().putBoolean(tag, true)
        }

        override fun removeCustomTag(tag: String) {
            stack.nbt?.remove(tag)
        }

        override fun hasCustomTag(tag: String): Boolean {
            return stack.nbt?.contains(tag) == true
        }

        override fun setDisplay(name: IText?, lore: List<IText>?) {
            stack.getOrCreateSubNbt("display").apply {
                if (name != null) {
                    putString("Name", textSerializer.toJson(name.value))
                }

                if (lore != null) {
                    put("Lore", NbtList().apply {
                        for (text in lore) {
                            add(NbtString.of(textSerializer.toJson(text.value)))
                        }
                    })
                }
            }
        }

        override fun setUnbreakable(value: Boolean) {
            stack.getOrCreateNbt().apply {
                putBoolean("Unbreakable", true)
            }
        }

        override fun setHideFlags(hideFlags: Int) {
            stack.getOrCreateNbt().apply {
                putInt("HideFlags", 255)
            }
        }

        override fun setNbtString(nbt: String?): Boolean {
            return try {
                stack.nbt = nbt?.let { StringNbtReader.parse(it) }
                true
            } catch (e: Throwable) {
                logger.error("[ItemStackFactory] Unable to parse NBT for item ${item.translationKey}: '$nbt'", e)
                false
            }
        }

        override fun getNbtString(): String? {
            return stack.nbt?.toString()
        }

        override fun setComponentsString(components: Map<String, String?>) = true
        override fun getComponentsString(): Map<String, String?>? = null

        override fun isDataOverlapping(nbt: String?, components: Map<String, String?>?): Boolean {
            val nbtCompound = nbt?.let { StringNbtReader.parse(it) }
                ?: return true

            return NbtPredicate(nbtCompound).test(stack)
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
            get() = stack.nbt?.getString("title")
            set(value) {
                when {
                    value != null -> stack.getOrCreateNbt().putString("title", value)
                    else -> stack.nbt?.remove("title")
                }
            }

        override var author: String?
            get() = stack.nbt?.getString("author")
            set(value) {
                when {
                    value != null -> stack.getOrCreateNbt().putString("author", value)
                    else -> stack.nbt?.remove("author")
                }
            }

        override fun setPages(pages: List<IText>) {
            stack.getOrCreateNbt().apply {
                put("pages", NbtList().apply {
                    for (page in pages) {
                        add(NbtString.of(textSerializer.toJson(page.value)))
                    }
                })
            }
        }
    }

    inner class FilledMapImpl(
        override val stack: ItemStack,
    ) : ItemStackImpl(stack), IFilledMap {
        override var mapId: Int?
            get() = stack.nbt?.getInt("map")
            set(value) {
                when {
                    value != null -> stack.getOrCreateNbt().putInt("map", value)
                    else -> stack.nbt?.remove("map")
                }
            }

        override var mapColor: Int?
            get() = stack.nbt?.getCompound("display")?.getInt("MapColor")
            set(value) {
                when {
                    value != null -> stack.getOrCreateSubNbt("display").putInt("MapColor", value)
                    else -> stack.nbt?.getCompound("display")?.remove("MapColor")
                }
            }

    }

    inner class FireworkRocketImpl(
        override val stack: ItemStack,
    ) : ItemStackImpl(stack), IFireworkRocket {
        override var fireworks: List<Unit>?
            get() = emptyList()
            set(value) {
                when {
                    value != null -> {
                        stack.getOrCreateNbt().put("Fireworks", NbtCompound())
                    }
                    else -> stack.nbt?.remove("Fireworks")
                }
            }
    }

    inner class PlayerHeadImpl(
        override val stack: ItemStack,
    ) : ItemStackImpl(stack), IPlayerHead {
        override fun setSkullOwner(player: ServerPlayerEntity) {
            stack.getOrCreateNbt().putString("SkullOwner", player.entityName)
        }
    }
}