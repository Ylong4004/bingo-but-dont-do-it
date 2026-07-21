package me.jfenn.bingo.common.game

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.mockk
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class GameOverPacketV9Test {

    @Test
    fun `V9 round trip preserves DDI damage history`() {
        val title = mockk<IText>()
        val subtitle = mockk<IText>()
        val teamName = mockk<IText>()
        val red = BingoTeamKey("bingo_red")
        val source = GameOverPacket(
            title = title,
            subtitle = subtitle,
            winner = red,
            duration = Duration.ofSeconds(123),
            isReturnToLobbyAvailable = true,
            isResumeAvailable = false,
            isWinner = true,
            isUpdate = false,
            winStreak = 2,
            bestWinStreak = 5,
            capturedItems = 7,
            endedAt = Instant.ofEpochMilli(456),
            seed = 99,
            scores = listOf(
                GameOverPacket.ScoreRanking(0, red, teamName, TeamScore(7, 1, 0), Duration.ofSeconds(123))
            ),
            defaultTab = GameOverPacket.EndScreenTab.DDI,
            ddiDamageHistory = listOf(
                DDITeamDamageHistory(
                    teamKey = red,
                    teamName = "红",
                    entries = listOf(
                        DDIDamageHistoryEntry("不要跳跃", "Alice", 2, 3),
                        DDIDamageHistoryEntry("立即扣一心", null, 1, 3),
                    ),
                )
            ),
        )
        val buffer = MemoryPacketBuf()

        GameOverPacket.V9.toPacketBuf(source, buffer)
        val decoded = GameOverPacket.V9.fromPacketBuf(buffer)

        assertThat(decoded.winner).isEqualTo(red)
        assertThat(decoded.duration).isEqualTo(source.duration)
        assertThat(decoded.defaultTab).isEqualTo(GameOverPacket.EndScreenTab.DDI)
        assertThat(decoded.ddiDamageHistory).isEqualTo(source.ddiDamageHistory)
        assertThat(decoded.scores.single().key).isEqualTo(red)
        assertThat(decoded.scores.single().score).isEqualTo(TeamScore(7, 1, 0))
    }

    @Test
    fun `V10 round trip preserves final DDI life snapshots`() {
        val title = mockk<IText>()
        val subtitle = mockk<IText>()
        val teamName = mockk<IText>()
        val blue = BingoTeamKey("bingo_blue")
        val source = GameOverPacket(
            title = title,
            subtitle = subtitle,
            winner = blue,
            duration = Duration.ofSeconds(45),
            isWinner = true,
            isUpdate = false,
            winStreak = null,
            seed = 123,
            scores = listOf(
                GameOverPacket.ScoreRanking(0, blue, teamName, TeamScore(9, 2, 1), Duration.ofSeconds(45))
            ),
            ddiDamageHistory = listOf(
                DDITeamDamageHistory(
                    teamKey = blue,
                    teamName = "蓝",
                    heartsRemaining = 3,
                    maxHearts = 4,
                )
            ),
        )
        val buffer = MemoryPacketBuf()

        GameOverPacket.V10.toPacketBuf(source, buffer)
        val decoded = GameOverPacket.V10.fromPacketBuf(buffer)

        assertThat(decoded.ddiDamageHistory.single().heartsRemaining).isEqualTo(3)
        assertThat(decoded.ddiDamageHistory.single().maxHearts).isEqualTo(4)
        assertThat(decoded.scores.single().score).isEqualTo(TeamScore(9, 2, 1))
    }

    private class MemoryPacketBuf : IPacketBuf {
        private val values = mutableListOf<Any?>()
        private var readIndex = 0

        private fun next(): Any? = values[readIndex++]

        override fun writeString(str: String) { values += str }
        override fun readString(): String = next() as String
        override fun writeInt(int: Int) { values += int }
        override fun readInt(): Int = next() as Int
        override fun writeLong(long: Long) { values += long }
        override fun readLong(): Long = next() as Long
        override fun writeFloat(float: Float) { values += float }
        override fun readFloat(): Float = next() as Float
        override fun writeBoolean(bool: Boolean) { values += bool }
        override fun readBoolean(): Boolean = next() as Boolean
        override fun writeItemStack(stack: IItemStack) { values += stack }
        override fun readItemStack(): IItemStack = next() as IItemStack
        override fun writeText(text: IText?) { values += text }
        override fun readText(): IText = next() as IText
        override fun writeByteArray(array: ByteArray) { values += array }
        override fun readByteArray(): ByteArray = next() as ByteArray
    }
}
