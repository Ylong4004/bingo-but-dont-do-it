package me.jfenn.bingo.mixin;

import me.jfenn.bingo.impl.TextImpl;
import me.jfenn.bingo.mixinhandler.PlayerEntityMixinHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(at = @At(value = "HEAD"), method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", cancellable = true)
    public void dropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            if (PlayerEntityMixinHandler.Companion.shouldVanishDrop(stack)) {
                if (PlayerEntityMixinHandler.Companion.shouldKeepDrop(stack)) {
                    ItemStack cur = player.getInventory().getMainHandStack();
                    if (retainOwnership && (cur == null || cur.isEmpty())) {
                        // The complete stack was dropped
                        player.getInventory().addPickBlock(stack.copy());
                    } else {
                        // Fallback
                        player.getInventory().insertStack(stack.copy());
                    }
                }

                ci.setReturnValue(null);
                ci.cancel();
            }
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "getPlayerListName()Lnet/minecraft/text/Text;", cancellable = true)
    public void getPlayerListName(CallbackInfoReturnable<Text> ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        var originalName = player.getDisplayName();
        var name = PlayerEntityMixinHandler.Companion.getPlayerListName(
                player.getUuid(),
                new TextImpl(originalName != null ? originalName.copy() : Text.literal(player.getEntityName()))
        );
        if (name != null) {
            ci.setReturnValue(name.getValue());
            ci.cancel();
        }
    }

}
