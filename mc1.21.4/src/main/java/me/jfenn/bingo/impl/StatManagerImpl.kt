package me.jfenn.bingo.impl

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IStatHandle
import me.jfenn.bingo.platform.IStatManager
import net.minecraft.registry.Registries
import net.minecraft.stat.Stat
import net.minecraft.stat.StatHandler
import net.minecraft.stat.StatType
import net.minecraft.util.Identifier

class StatManagerImpl : IStatManager {

    override fun list(): List<IStatHandle> {
        return Registries.STAT_TYPE
            .flatten()
            .map { StatHandleImpl(it) }
    }

    override fun getById(type: String, name: String?): IStatHandle? {
        val typeId = Identifier.tryParse(type) ?: return null
        val statType = Registries.STAT_TYPE.get(typeId) ?: return null
        @Suppress("UNCHECKED_CAST") (statType as StatType<Any>)

        if (name != null) {
            val nameId = Identifier.tryParse(name) ?: return null
            val statName = statType.registry.get(nameId) ?: return null

            return StatHandleImpl(
                stat = statType.getOrCreateStat(statName),
            )
        } else {
            return SummedStatHandleImpl(statType)
        }
    }

    inner class StatHandleImpl<T>(
        private val stat: Stat<T>,
    ) : IStatHandle {
        override fun getForPlayer(player: IPlayerHandle): Int {
            require(player is PlayerHandle)
            val statHandler: StatHandler = player.player.statHandler
            return statHandler.getStat(stat)
        }

        override fun reset(player: IPlayerHandle) {
            require(player is PlayerHandle)
            player.player.resetStat(stat)
        }
    }

    inner class SummedStatHandleImpl<T>(
        private val type: StatType<T>,
    ) : IStatHandle {
        override fun getForPlayer(player: IPlayerHandle): Int {
            require(player is PlayerHandle)
            val statHandler: StatHandler = player.player.statHandler
            return type.sumOf { statHandler.getStat(it) }
        }

        override fun reset(player: IPlayerHandle) {
            require(player is PlayerHandle)
            type.forEach { player.player.resetStat(it) }
        }
    }
}