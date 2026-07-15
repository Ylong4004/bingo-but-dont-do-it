package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports a completed crafting operation after the player takes its result.
 * This covers both the 2x2 inventory grid and crafting tables.
 */
@Mixin(CraftingResultSlot.class)
public class DDICraftingResultSlotMixin {

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void bingo$reportDDICraft(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer && !stack.isEmpty()) {
            DDITriggerDetector.reportCrafted(
                    serverPlayer,
                    Registries.ITEM.getId(stack.getItem()).toString()
            );
        }
    }
}
