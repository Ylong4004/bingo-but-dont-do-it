package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhelper.ServerChunkManagerMixinHelper;
import net.minecraft.server.world.ServerChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkManager.class)
public class ServerChunkManagerMixin {
    @Unique
    private final Logger logger = LoggerFactory.getLogger(ServerChunkManagerMixin.class);

    @Inject(at = @At(value = "HEAD"), method = "save(Z)V", cancellable = true)
    public void save(boolean flush, CallbackInfo ci) {
        if (ServerChunkManagerMixinHelper.getShouldCancelSaving()) {
            logger.debug("Skipping chunk manager save() call, because Yet Another Bingo will discard the save data.");
            ci.cancel();
        }
    }
}
