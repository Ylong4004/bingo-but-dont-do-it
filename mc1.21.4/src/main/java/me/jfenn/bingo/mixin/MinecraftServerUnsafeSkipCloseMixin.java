package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.WorldDeleter;
import me.jfenn.bingo.mixinhandler.MinecraftServerMixinHandler;
import me.jfenn.bingo.platform.scope.BingoKoin;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerNetworkIo;
import net.minecraft.util.profiler.Recorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerUnsafeSkipCloseMixin {

    @Unique
    private final Logger log = LoggerFactory.getLogger(MinecraftServerUnsafeSkipCloseMixin.class);

    @Shadow
    private boolean saving;

    @Shadow @Final
    private ServerNetworkIo networkIo;

    @Shadow
    private PlayerManager playerManager;

    @Shadow private Recorder recorder;

    @Shadow public abstract void forceStopRecorder();

    @Unique
    private boolean shouldKeepWorldData() {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (BingoKoin.INSTANCE.getScope(server) == null) return true;

        // if this is running client-side, just shut down normally
        if (!server.isDedicated()) return true;

        // if isLobbyMode=false, never delete any world data
        if (!MinecraftServerMixinHandler.INSTANCE.shouldDeleteWorld(server)) {
            return true;
        }

        // if a game is running, the server should restart normally to save all world data
        return MinecraftServerMixinHandler.INSTANCE.isGamePlaying(server);
    }

    @Inject(at = @At(value = "HEAD"), method = "shutdown", cancellable = true)
    public void shutdownUnsafe(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (shouldKeepWorldData()) return;

        // Only proceed if unsafeSkipWorldClose=true
        if (!MinecraftServerMixinHandler.INSTANCE.isUnsafeSkipWorldClose(server)) return;

        try {
            // tell Fabric to invoke SERVER_STOPPING, which *might* not be the first mixin
            ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(server);
        } catch (Throwable e) {
            log.error("Error on SERVER_STOPPING", e);
        }

        // many of the shutdown tasks can be skipped, since we don't care if all the world data is saved
        log.info("Stopping server");

        if (this.recorder != null && this.recorder.isActive()) {
            this.forceStopRecorder();
        }

        if (this.networkIo != null) {
            this.networkIo.stop();
        }

        this.saving = true;

        if (this.playerManager != null) {
            log.info("Disconnecting players");
            try {
                this.playerManager.disconnectAllPlayers();
            } catch (Throwable e) {
                log.error("Error on disconnectAllPlayers", e);
            }
        }

        log.info("unsafeSkipWorldClose is true; skipping file closing");
        log.info("This will likely cause a crash...");

        this.saving = false;

        try {
            // tell Fabric to invoke SERVER_STOPPED (otherwise this would not reach its mixin)
            ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(server);
        } catch (Throwable e) {
            log.error("Error on SERVER_STOPPED", e);
        }

        // finally, delete the world files before restarting
        WorldDeleter.INSTANCE.invoke(server);

        // immediately halt the JVM (like System.exit(0) but *worse!*)
        Runtime.getRuntime().halt(0);
        ci.cancel();
    }

    @Inject(at = @At(value = "TAIL"), method = "shutdown", cancellable = true)
    public void shutdownDeleteWorld(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (shouldKeepWorldData()) return;

        try {
            // tell Fabric to invoke SERVER_STOPPED (otherwise this would not reach its mixin)
            ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(server);
        } catch (Throwable e) {
            log.error("Error on SERVER_STOPPED", e);
        }

        // finally, delete the world files before restarting
        WorldDeleter.INSTANCE.invoke(server);
        ci.cancel();
    }

}
