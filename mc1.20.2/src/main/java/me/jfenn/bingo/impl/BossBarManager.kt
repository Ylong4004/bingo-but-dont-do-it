package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.IBossBar
import me.jfenn.bingo.platform.IBossBarManager
import me.jfenn.bingo.platform.IPlayerHandle
import net.minecraft.entity.boss.BossBar
import net.minecraft.entity.boss.CommandBossBar
import net.minecraft.entity.boss.ServerBossBar
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.lang.ref.WeakReference
import java.util.*

internal class BossBarManager(
    private val server: MinecraftServer,
) : IBossBarManager {
    companion object {
        internal val serverBossBars = Collections.synchronizedList(
            mutableListOf<WeakReference<ServerBossBar>>()
        )
    }

    override fun get(id: String): IBossBar? {
        return server.bossBarManager.get(Identifier(id))
            ?.let { BossBarImpl(it) }
    }

    override fun remove(bossBar: IBossBar) {
        require(bossBar is BossBarImpl)
        bossBar.clearPlayers()
        (bossBar.bossBar as? CommandBossBar)
            ?.let { server.bossBarManager.remove(it) }
    }

    override fun add(id: String, title: Text): IBossBar {
        val bossBar = server.bossBarManager.add(Identifier(id), title)
        return BossBarImpl(bossBar)
    }

    override fun list(): List<IBossBar> {
        return server.bossBarManager.all
            .map { BossBarImpl(it) }
            .plus(
                serverBossBars
                    .mapNotNull { it.get() }
                    .map { BossBarImpl(it) }
            )
    }
}

internal class BossBarImpl(
    internal val bossBar: BossBar,
): IBossBar {
    private val commandBossBar: CommandBossBar? = bossBar as? CommandBossBar
    private val serverBossBar: ServerBossBar? = bossBar as? ServerBossBar

    override val id: String?
        get() = commandBossBar?.id?.toString()

    override var name: IText
        get() = TextImpl(bossBar.name.copy())
        set(value) { bossBar.name = value.value }

    override var color: IBossBar.Color
        get() = IBossBar.Color.WHITE
        set(value) {
            bossBar.color = when (value) {
                IBossBar.Color.WHITE -> BossBar.Color.WHITE
            }
        }

    override var style: IBossBar.Style
        get() = IBossBar.Style.PROGRESS
        set(value) {
            bossBar.style = when (value) {
                IBossBar.Style.PROGRESS -> BossBar.Style.PROGRESS
            }
        }

    override var value: Int
        get() = commandBossBar?.value ?: (bossBar.percent * 100).toInt()
        set(value) {
            commandBossBar?.setValue(value)
                ?: bossBar.setPercent(value / 100f)
        }
    override var maxValue: Int
        get() = commandBossBar?.maxValue ?: 100
        set(value) {
            commandBossBar?.setMaxValue(value)
                ?: throw UnsupportedOperationException("Cannot set max value on a non-command bossbar")
        }

    override fun addPlayer(player: IPlayerHandle) {
        require(player is PlayerHandle)
        commandBossBar?.addPlayer(player.player)
        serverBossBar?.addPlayer(player.player)
    }

    override fun removePlayer(player: IPlayerHandle) {
        require(player is PlayerHandle)
        commandBossBar?.removePlayer(player.player)
        serverBossBar?.removePlayer(player.player)
    }

    override fun clearPlayers() {
        commandBossBar?.clearPlayers()
        serverBossBar?.clearPlayers()
    }
}
