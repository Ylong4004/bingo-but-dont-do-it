package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.ConstantsKt;
import me.jfenn.bingo.mixinhelper.ServerChunkManagerMixinHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerSkipStartRegionMixin {
    @Inject(at = @At(value = "HEAD"), method = "prepareStartRegion", cancellable = true)
    private void prepareStartRegion(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (
                server.isDedicated()
                        || ServerChunkManagerMixinHelper.getShouldCancelSaving()
                        || server.getSaveProperties().getLevelName().startsWith(ConstantsKt.getBINGO_WORLD_PREFIX())
        ) {
            // The start region worldgen isn't all that useful in bingo, since the game starts at random coords anyway. So it doesn't really help.
            ci.cancel();
        }
    }
}
