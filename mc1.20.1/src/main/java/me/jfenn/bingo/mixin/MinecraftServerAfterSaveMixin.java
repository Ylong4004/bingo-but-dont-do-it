package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.ConstantsKt;
import me.jfenn.bingo.impl.ServerEventsImpl;
import me.jfenn.bingo.mixinhelper.ServerChunkManagerMixinHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerAfterSaveMixin {
    @Inject(method = "save", at = @At("TAIL"))
    private void endSave(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        var callback = ServerEventsImpl.Companion.getAfterSaveCallback();
        if (callback != null) callback.invoke((MinecraftServer) (Object) this);
    }
}
