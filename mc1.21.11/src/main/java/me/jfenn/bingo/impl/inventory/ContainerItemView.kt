package me.jfenn.bingo.impl.inventory

import me.jfenn.bingo.platform.inventory.IContainerItemView
import me.jfenn.bingo.platform.item.IItemStack
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.BundleContentsComponent
import net.minecraft.component.type.ContainerComponent
import net.minecraft.item.ItemStack

interface ContainerItemView {
    class Bundle(
        val bundleItem: ItemStack,
        override val stack: IItemStack
    ) : IContainerItemView {
        override fun replace(newStack: IItemStack) {
            val bundleComponent = bundleItem[DataComponentTypes.BUNDLE_CONTENTS]

            val newBundleComponent = BundleContentsComponent(
                bundleComponent?.iterate()
                    ?.mapNotNull { bundleStack ->
                        if (bundleStack === stack.stack)
                            newStack.stack.takeUnless { it.isEmpty }
                        else bundleStack
                    }
                    ?.toList()
                    ?: emptyList()
            )

            bundleItem[DataComponentTypes.BUNDLE_CONTENTS] = newBundleComponent
        }
    }

    class Container(
        val containerItem: ItemStack,
        override val stack: IItemStack
    ) : IContainerItemView {
        override fun replace(newStack: IItemStack) {
            val containerComponent = containerItem[DataComponentTypes.CONTAINER]

            val newContainerComponent = ContainerComponent.fromStacks(
                containerComponent?.iterateNonEmpty()
                    ?.mapNotNull { bundleStack ->
                        if (bundleStack === stack.stack)
                            newStack.stack.takeUnless { it.isEmpty }
                        else bundleStack
                    }
                    ?.toList()
                    ?: emptyList()
            )

            containerItem[DataComponentTypes.CONTAINER] = newContainerComponent
        }
    }

    class Inventory(
        override val stack: IItemStack
    ) : IContainerItemView {
        override fun replace(newStack: IItemStack) {}
    }
}