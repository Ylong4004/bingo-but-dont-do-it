package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports a completed crafting operation after the player takes its result.
 * This covers both the 2x2 inventory grid and crafting tables.
 */
@Mixin(CraftingResultSlot.class)
public class DDICraftingResultSlotMixin {

    /**
     * quickMove empties the live result stack before onTakeItem is called, but
     * onQuickTransfer first invokes this overload with an intact copy.
     */
    @Unique
    private Item bingo$ddiQuickCraftItem;

    @Inject(
            method = "onCrafted(Lnet/minecraft/item/ItemStack;I)V",
            at = @At("HEAD")
    )
    private void bingo$captureDDIQuickCraft(
            ItemStack stack,
            int amount,
            CallbackInfo ci
    ) {
        if (amount > 0 && !stack.isEmpty()) {
            bingo$ddiQuickCraftItem = stack.getItem();
        }
    }

    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void bingo$reportDDICraft(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        Item craftedItem = bingo$ddiQuickCraftItem;
        bingo$ddiQuickCraftItem = null;
        if (craftedItem == null && !stack.isEmpty()) {
            craftedItem = stack.getItem();
        }
        if (player instanceof ServerPlayerEntity serverPlayer && craftedItem != null) {
            DDITriggerDetector.reportCrafted(
                    serverPlayer,
                    craftedItem
            );
        }
    }
}
