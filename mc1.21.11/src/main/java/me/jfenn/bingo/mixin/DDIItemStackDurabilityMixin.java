package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDISpecialEventHooks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在原版耐久附魔逻辑之后应用当前作用域的耐久祝福或锈蚀效果。 */
@Mixin(ItemStack.class)
public class DDIItemStackDurabilityMixin {

    @Inject(
            method = "calculateDamage(ILnet/minecraft/server/world/ServerWorld;Lnet/minecraft/server/network/ServerPlayerEntity;)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private void bingo$modifyDDISpecialDurability(
            int amount,
            ServerWorld world,
            ServerPlayerEntity player,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (player != null && cir.getReturnValueI() > 0) {
            cir.setReturnValue(DDISpecialEventHooks.modifyDurabilityDamage(
                    player,
                    cir.getReturnValueI()
            ));
        }
    }
}
