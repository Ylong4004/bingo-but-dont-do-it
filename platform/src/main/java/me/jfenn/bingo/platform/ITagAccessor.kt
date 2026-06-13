package me.jfenn.bingo.platform

interface ITagAccessor {

    fun getItemTag(id: String): List<String>?

    fun getBlockTag(id: String): ITagContents<IRegistryEntry.Block>

    fun getBiomeTag(id: String): ITagContents<IRegistryEntry.Biome>

}

interface ITagContents<T: IRegistryEntry> {
    fun list(): List<T>
    fun contains(entry: T): Boolean
}

interface IRegistryEntry {
    interface Block : IRegistryEntry
    interface Biome : IRegistryEntry
}
