package me.jfenn.bingo.client.common.state

import me.jfenn.bingo.client.common.hud.BingoCardColors
import me.jfenn.bingo.client.platform.renderer.IFramebuffer
import me.jfenn.bingo.common.map.CardView
import me.jfenn.bingo.platform.text.IText
import java.io.Closeable
import java.time.Instant

internal interface ClientCard {
    val framebuffer: IFramebuffer
    var view: CardView
    var teamName: IText?
    var shuffledAt: Instant
    var shufflePositions: List<Int>
    var colors: BingoCardColors.TeamColors
    val isGui: Boolean
    var isDirty: Boolean

    val teamKey get() = view.teamKey
    val tiles get() = view.tiles
    val display get() = view.display
}

internal class ClientCardBase(
    override val framebuffer: IFramebuffer,
    override var view: CardView,
    override var teamName: IText? = null,
    override var shuffledAt: Instant = Instant.MIN,
    override var shufflePositions: List<Int> = (0 until 25).toList(),
    override var colors: BingoCardColors.TeamColors = BingoCardColors.TeamColors(),
    override val isGui: Boolean = false,
    override var isDirty: Boolean = true,
    guiFramebuffer: IFramebuffer,
) : ClientCard, Closeable {

    val guiCard: ClientCard = ClientCardProxy(this, guiFramebuffer)

    override fun close() {
        framebuffer.close()
        guiCard.framebuffer.close()
    }
}

internal class ClientCardProxy(
    base: ClientCard,
    override val framebuffer: IFramebuffer,
    override val isGui: Boolean = true,
    override var isDirty: Boolean = true,
) : ClientCard by base
