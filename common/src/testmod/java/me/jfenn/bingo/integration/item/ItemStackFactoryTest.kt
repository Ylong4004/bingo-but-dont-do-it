package me.jfenn.bingo.integration.item

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.common.test.BaseGameTest
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext

class ItemStackFactoryTest : BaseGameTest() {

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun createsStackWithProperties(context: TestContext) {
        val koin = BingoKoin.getScope(context.world.server)!!
        val itemStackFactory = koin.get<IItemStackFactory>()

        val itemStack = itemStackFactory.createStack("minecraft:stick", 3)
        assertThat(itemStack).isNotNull()
        assertThat(itemStack.count).isEqualTo(3)
        assertThat(itemStack.maxCount).isEqualTo(64)
        assertThat(itemStack.identifier.toString()).isEqualTo("minecraft:stick")
        assertThat(itemStack.isEmpty).isFalse()
        assertThat(itemStack.displayName).isNotNull()
        assertThat(itemStack.lore).hasSize(0)
    }

}