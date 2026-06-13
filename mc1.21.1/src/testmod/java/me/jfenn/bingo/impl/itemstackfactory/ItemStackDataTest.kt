@file:Suppress("unused")

package me.jfenn.bingo.impl.itemstackfactory

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.mockk
import me.jfenn.bingo.platform.ITextSerializer
import me.jfenn.bingo.common.test.BaseGameTest
import me.jfenn.bingo.impl.ItemStackFactory
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ItemEnchantmentsComponent
import net.minecraft.enchantment.Enchantments
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKeys
import net.minecraft.test.GameTest
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.slf4j.LoggerFactory

class ItemStackDataTest : BaseGameTest() {

    private val mockTextSerializer = mockk<ITextSerializer>()

    private val itemStackFactory = ItemStackFactory(
        LoggerFactory.getLogger(this::class.java),
        null
    )

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun encodesItemComponent(context: TestContext) {
        val stack = ItemStack(Items.RED_BED, 1)
        stack[DataComponentTypes.ITEM_NAME] = Text.literal("Red Bed").formatted(Formatting.RED)

        val item = itemStackFactory.forStack(stack)
        val components = item.getComponentsString()

        assertThat(components).isEqualTo(
            mapOf(
                "minecraft:item_name" to """"{\"color\":\"red\",\"text\":\"Red Bed\"}""""
            )
        )
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun decodesItemComponent(context: TestContext) {
        val item = itemStackFactory.createStack("minecraft:red_bed", 1)
        val isSuccess = item.setComponentsString(
            mapOf(
                "minecraft:item_name" to """"{\"color\":\"red\",\"text\":\"Red Bed\"}""""
            )
        )

        assertThat(isSuccess).isTrue()
        assertThat((item.stack as ItemStack)[DataComponentTypes.ITEM_NAME]).isEqualTo(
            Text.literal("Red Bed").formatted(Formatting.RED)
        )
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun determinesComponentOverlap(context: TestContext) {
        val stack = ItemStack(Items.RED_BED, 1)
        stack[DataComponentTypes.ITEM_NAME] = Text.literal("Red Bed").formatted(Formatting.RED)

        val item = itemStackFactory.forStack(stack)
        val isOverlapping = item.isDataOverlapping(
            nbt = null,
            components = mapOf(
                "minecraft:item_name" to """"{\"color\":\"red\",\"text\":\"Red Bed\"}""""
            )
        )

        assertThat(isOverlapping).isTrue()
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    fun encodesItemComponentUsingRegistry(context: TestContext) {
        val itemStackFactory = ItemStackFactory(
            LoggerFactory.getLogger(this::class.java),
            context.world.server
        )

        val stack = ItemStack(Items.GOLDEN_SWORD, 1)
        val enchantmentRegistry = context.world.server.registryManager.get(RegistryKeys.ENCHANTMENT)
        val fireAspect = enchantmentRegistry.getEntry(Enchantments.FIRE_ASPECT).get()
        stack[DataComponentTypes.ENCHANTMENTS] = ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT)
            .also { it.set(fireAspect, 2) }
            .build()

        val item = itemStackFactory.forStack(stack)
        val data = item.getComponentsString()

        assertThat(data).isEqualTo(
            mapOf(
                "minecraft:enchantments" to "{\"levels\":{\"minecraft:fire_aspect\":2}}"
            )
        )
    }
}