package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhelper.ServerChunkManagerMixinHelper;
import net.minecraft.server.world.ServerEntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntityManager.class)
public class ServerEntityManagerMixin {
    @Unique
    private final Logger logger = LoggerFactory.getLogger(ServerEntityManagerMixin.class);

    @Inject(at = @At(value = "HEAD"), method = "flush()V", cancellable = true)
    public void flush(CallbackInfo ci) {
        if (ServerChunkManagerMixinHelper.INSTANCE.getShouldCancelSaving()) {
            logger.debug("Skipping entity manager flush() call, because Yet Another Bingo will discard the save data.");
            ci.cancel();
        }
    }
}
