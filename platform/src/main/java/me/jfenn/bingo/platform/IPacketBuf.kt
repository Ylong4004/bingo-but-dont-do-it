package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText
import java.time.Duration
import java.time.Instant
import java.util.*

interface IPacketBuf {
    fun writeString(str: String)
    fun readString(): String

    fun writeInt(int: Int)
    fun readInt(): Int

    fun writeLong(long: Long)
    fun readLong(): Long

    fun writeFloat(float: Float)
    fun readFloat(): Float

    fun writeBoolean(bool: Boolean)
    fun readBoolean(): Boolean

    fun writeItemStack(stack: IItemStack)
    fun readItemStack(): IItemStack

    fun writeText(text: IText?)
    fun readText(): IText

    fun writeByteArray(array: ByteArray)
    fun readByteArray(): ByteArray

    fun <T> writeNullable(value: T?, callback: (T) -> Unit) {
        writeBoolean(value != null)
        value?.let(callback)
    }

    fun <T> readNullable(callback: () -> T): T? {
        return if (readBoolean()) callback() else null
    }

    fun <T> writeList(value: Collection<T>, callback: (T) -> Unit) {
        val list = value.toList()
        writeInt(list.size)
        list.forEach(callback)
    }

    fun <T> readList(callback: () -> T): List<T> {
        return List(readInt()) { callback() }
    }

    fun writeDuration(value: Duration) {
        writeLong(
            try {
                value.toMillis()
            } catch (_: ArithmeticException) {
                0L
            }
        )
    }

    fun readDuration(): Duration {
        return Duration.ofMillis(readLong())
    }

    fun writeInstant(value: Instant) {
        writeLong(value.toEpochMilli())
    }

    fun readInstant(): Instant {
        return Instant.ofEpochMilli(readLong())
    }

    fun writeUUID(value: UUID) {
        writeLong(value.mostSignificantBits)
        writeLong(value.leastSignificantBits)
    }

    fun readUUID(): UUID {
        return UUID(readLong(), readLong())
    }

    fun <T: Enum<T>> writeEnum(value: T) {
        writeInt(value.ordinal)
    }
}

inline fun <reified T: Enum<T>> IPacketBuf.readEnum(): T {
    val ordinal = readInt()
    val values = enumValues<T>()
    return values.getOrElse(ordinal) { values.first() }
}
