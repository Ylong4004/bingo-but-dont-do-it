package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhelper.ServerRecipeBookMixinHelper;
import net.minecraft.server.network.ServerRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerRecipeBook.class)
public class ServerRecipeBookMixin {
    @ModifyArg(
            method = "method_64591(Ljava/util/List;Lnet/minecraft/recipe/RecipeEntry;Lnet/minecraft/recipe/RecipeDisplayEntry;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/RecipeBookAddS2CPacket$Entry;<init>(Lnet/minecraft/recipe/RecipeDisplayEntry;ZZ)V"),
            index = 1
    )
    private static boolean showNotification(boolean showNotification) {
        if (ServerRecipeBookMixinHelper.getHideRecipeNotifications()) {
            return false;
        } else {
            return showNotification;
        }
    }
}
