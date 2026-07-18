package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.mixinhandler.PlayerListNameDecorators
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.text.IText
import net.minecraft.util.Formatting
import org.junit.jupiter.api.Test
import java.util.UUID

class DDITabLivesServiceTest {

    @Test
    fun `生命通过玩家名后缀显示且停止后立即移除`() {
        val playerId = UUID.randomUUID()
        val outsiderId = UUID.randomUUID()
        val playerManager = mockk<IPlayerManager>()
        val text = mockk<TextProvider>()
        val baseName = mockk<IText>()
        val decoratedName = mockk<IText>()
        val heartText = mockk<IText>()
        every { playerManager.getPlayers() } returns emptyList()
        every { text.empty() } returns decoratedName
        every { decoratedName.append(baseName) } returns decoratedName
        every { decoratedName.append(" ") } returns decoratedName
        every { text.literal("3♥") } returns heartText
        every { heartText.formatted(Formatting.RED) } returns heartText
        every { decoratedName.append(heartText) } returns decoratedName

        val service = DDITabLivesService(playerManager, text)
        try {
            service.start { uuid -> if (uuid == playerId) 3 else null }

            assertThat(PlayerListNameDecorators.apply(playerId, baseName)).isEqualTo(decoratedName)
            assertThat(PlayerListNameDecorators.apply(outsiderId, baseName)).isNull()
            verify(exactly = 1) { text.literal("3♥") }
        } finally {
            service.stop()
        }

        assertThat(PlayerListNameDecorators.apply(playerId, baseName)).isNull()
    }
}
