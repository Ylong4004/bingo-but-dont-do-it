package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.PlayerManagerMixinHelper;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalBlock.class)
abstract class EndPortalBlockMixin {
    @Inject(
            method = "createTeleportTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;getRespawnTarget(ZLnet/minecraft/world/TeleportTarget$PostDimensionTransition;)Lnet/minecraft/world/TeleportTarget;")
    )
    public void createTeleportTargetForEndSpawn(
            ServerWorld world,
            Entity entity,
            BlockPos pos,
            CallbackInfoReturnable<TeleportTarget> cir
    ) {
        if (entity instanceof ServerPlayerEntity player) {
            // the player is exiting through the end portal...
            // if the spawnpoint is also in the end, then respawn them in the overworld
            // NOTE: duplicated inside PlayerManagerMixin
            if (PlayerManagerMixinHelper.Companion.shouldOverrideEndRespawn()) {
                var overworld = world.getServer().getOverworld();
                player.setSpawnPoint(
                        new ServerPlayerEntity.Respawn(overworld.getSpawnPoint(), false),
                        false
                );
            }
        }
    }
}
