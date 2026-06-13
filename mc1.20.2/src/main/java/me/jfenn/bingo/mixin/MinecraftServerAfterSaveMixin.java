package me.jfenn.bingo.mixin;

import me.jfenn.bingo.impl.ServerEventsImpl;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerAfterSaveMixin {
    @Inject(method = "save", at = @At("TAIL"))
    private void endSave(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        var callback = ServerEventsImpl.Companion.getAfterSaveCallback();
        if (callback != null) callback.invoke((MinecraftServer) (Object) this);
    }
}
