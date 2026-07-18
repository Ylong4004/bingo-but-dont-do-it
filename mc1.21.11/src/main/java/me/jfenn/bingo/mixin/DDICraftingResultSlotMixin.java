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
 * 玩家取走结果后，上报一次已经完成的合成操作。
 * 同时覆盖背包中的 2×2 合成栏和工作台。
 */
@Mixin(CraftingResultSlot.class)
public class DDICraftingResultSlotMixin {

    /**
     * quickMove 会在调用 onTakeItem 前清空实时结果堆叠，但 onQuickTransfer
     * 会先用完整副本调用此重载。
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
