@file:Suppress("unused")

package me.jfenn.bingo.impl.itemstackfactory

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.mockk
import me.jfenn.bingo.platform.ITextSerializer
import me.jfenn.bingo.common.test.BaseGameTest
import me.jfenn.bingo.impl.ItemStackFactory
import me.jfenn.bingo.impl.TextSerializer
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.slf4j.LoggerFactory

class ItemStackDataTest : BaseGameTest() {

    private val mockTextSerializer = mockk<ITextSerializer>()

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun encodesItemNbt(context: TestContext) {
        val stack = ItemStack(Items.RED_BED, 1)

        stack.setCustomName(Text.literal("Red Bed").formatted(Formatting.RED))

        val itemStackFactory = ItemStackFactory(
            LoggerFactory.getLogger(this::class.java),
            TextSerializer(),
        )

        val item = itemStackFactory.forStack(stack)
        val nbt = item.getNbtString()

        assertThat(nbt).isEqualTo(
            """{display:{Name:'{"text":"Red Bed","color":"red"}'}}"""
        )
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun decodesItemNbt(context: TestContext) {
        val itemStackFactory = ItemStackFactory(
            LoggerFactory.getLogger(this::class.java),
            TextSerializer(),
        )

        val item = itemStackFactory.createStack("minecraft:red_bed", 1)
        val isSuccess = item.setNbtString(
            """{display:{Name:'{"text":"Red Bed","color":"red"}'}}"""
        )

        assertThat(isSuccess).isTrue()
        assertThat((item.stack as ItemStack).name).isEqualTo(
            Text.literal("Red Bed").formatted(Formatting.RED)
        )
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun determinesNbtOverlap(context: TestContext) {
        val itemStackFactory = ItemStackFactory(
            LoggerFactory.getLogger(this::class.java),
            TextSerializer(),
        )

        val stack = ItemStack(Items.RED_BED, 1)
        stack.setCustomName(Text.literal("Red Bed").formatted(Formatting.RED))

        val item = itemStackFactory.forStack(stack)
        val isOverlapping = item.isDataOverlapping(
            nbt = """{display:{Name:'{"text":"Red Bed","color":"red"}'}}""",
            components = null,
        )

        assertThat(isOverlapping).isTrue()
    }
}