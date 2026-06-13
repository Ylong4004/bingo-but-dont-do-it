package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IRegistryEntry
import me.jfenn.bingo.platform.ITagAccessor
import me.jfenn.bingo.platform.ITagContents
import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.world.biome.Biome
import kotlin.jvm.optionals.getOrNull

class TagAccessorImpl(
    private val server: MinecraftServer,
) : ITagAccessor {
    override fun getItemTag(id: String): List<String>? {
        val tag = try {
            TagKey.of(RegistryKeys.ITEM, Identifier.of(id))
        } catch (e: IllegalArgumentException) {
            return null
        }

        return Registries.ITEM.getOptional(tag).getOrNull()
            ?.map { it.idAsString }
    }

    override fun getBlockTag(id: String): ITagContents<IRegistryEntry.Block> {
        val tagKey = TagKey.of(RegistryKeys.BLOCK, Identifier.of(id))
        return BlockTagContentsImpl(tagKey)
    }

    override fun getBiomeTag(id: String): ITagContents<IRegistryEntry.Biome> {
        val registry = server.registryManager.getOrThrow(RegistryKeys.BIOME)
        val tagKey = TagKey.of(RegistryKeys.BIOME, Identifier.of(id))
        return BiomeTagContentsImpl(registry, tagKey)
    }
}

abstract class TagContentsImpl<T: IRegistryEntry, U>(
    private val tagKey: TagKey<U>,
) : ITagContents<T> {
    abstract val registry: Registry<U>
    abstract fun toMinecraftEntry(entry: T): RegistryEntry<U>
    abstract fun toPlatformEntry(entry: RegistryEntry<U>): T

    override fun list(): List<T> {
        return registry.getOptional(tagKey)
            .getOrNull()
            ?.map { toPlatformEntry(it) }
            .orEmpty()
    }

    override fun contains(entry: T): Boolean {
        return toMinecraftEntry(entry).isIn(tagKey)
    }
}

class BlockTagContentsImpl(
    tagKey: TagKey<Block>,
): TagContentsImpl<IRegistryEntry.Block, Block>(tagKey) {
    override val registry: Registry<Block> = Registries.BLOCK
    override fun toMinecraftEntry(entry: IRegistryEntry.Block): RegistryEntry<Block> =
        (entry as BlockRegistryEntry).entry
    override fun toPlatformEntry(entry: RegistryEntry<Block>): BlockRegistryEntry =
        BlockRegistryEntry(entry)
}

class BlockRegistryEntry(
    val entry: RegistryEntry<Block>
): IRegistryEntry.Block

class BiomeTagContentsImpl(
    override val registry: Registry<Biome>,
    tagKey: TagKey<Biome>,
): TagContentsImpl<IRegistryEntry.Biome, Biome>(tagKey) {
    override fun toMinecraftEntry(entry: IRegistryEntry.Biome): RegistryEntry<Biome> =
        (entry as BiomeRegistryEntry).entry
    override fun toPlatformEntry(entry: RegistryEntry<Biome>): BiomeRegistryEntry =
        BiomeRegistryEntry(entry)
}

class BiomeRegistryEntry(
    val entry: RegistryEntry<Biome>
): IRegistryEntry.Biome
