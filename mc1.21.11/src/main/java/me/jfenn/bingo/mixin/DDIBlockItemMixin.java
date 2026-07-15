package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports only block placements accepted by vanilla on the server. */
@Mixin(BlockItem.class)
public class DDIBlockItemMixin {

    @Inject(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN")
    )
    private void bingo$reportDDIPlacedBlock(
            ItemPlacementContext context,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (cir.getReturnValue().isAccepted()
                && context.getPlayer() instanceof ServerPlayerEntity player) {
            DDITriggerDetector.reportPlaced(
                    player,
                    Registries.ITEM.getId((BlockItem) (Object) this).toString()
            );
        }
    }
}
