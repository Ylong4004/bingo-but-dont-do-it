package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.ServerPlayNetworkHandlerMixinHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(at = @At("HEAD"), method = "onPlayerMove")
    private void onPlayerMove(CallbackInfo ci) {
        ServerPlayNetworkHandlerMixinHandler.INSTANCE.onPlayerMove(player);
    }
}
