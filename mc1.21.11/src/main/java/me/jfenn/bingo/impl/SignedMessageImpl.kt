package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.commands.ISignedMessage
import net.minecraft.network.message.SignedMessage
import java.util.*

class SignedMessageImpl(
    val message: SignedMessage,
) : ISignedMessage {
    override val sender: UUID
        get() = message.sender
    override val text: IText
        get() = TextImpl(message.content.copy())
    override val raw: String
        get() = message.signedContent

    override fun withUnsignedContent(text: IText): ISignedMessage {
        return SignedMessageImpl(message.withUnsignedContent(text.value))
    }
}