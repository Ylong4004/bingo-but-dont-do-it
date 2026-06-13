package me.jfenn.bingo.impl.networking

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.impl.TextImpl
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.network.PacketByteBuf
import net.minecraft.text.Text

class PacketBufImpl(
    private val buf: PacketByteBuf,
    private val itemStackFactory: IItemStackFactory,
) : IPacketBuf {
    override fun writeString(str: String) = buf.writeString(str).let {}
    override fun readString(): String = buf.readString()

    override fun writeInt(int: Int) = buf.writeInt(int).let {}
    override fun readInt(): Int = buf.readInt()

    override fun writeLong(long: Long) = buf.writeLong(long).let {}
    override fun readLong(): Long = buf.readLong()

    override fun writeFloat(float: Float) = buf.writeFloat(float).let {}
    override fun readFloat(): Float = buf.readFloat()

    override fun writeBoolean(bool: Boolean) = buf.writeBoolean(bool).let {}
    override fun readBoolean(): Boolean = buf.readBoolean()

    override fun writeItemStack(stack: IItemStack) = buf.writeItemStack(stack.stack).let {}
    override fun readItemStack(): IItemStack = itemStackFactory.forStack(buf.readItemStack())

    override fun writeText(text: IText?) = buf.writeText(text?.value ?: Text.empty()).let {}
    override fun readText(): IText = TextImpl(buf.readText().copy())

    override fun writeByteArray(array: ByteArray) = buf.writeByteArray(array).let {}
    override fun readByteArray(): ByteArray = buf.readByteArray()
}