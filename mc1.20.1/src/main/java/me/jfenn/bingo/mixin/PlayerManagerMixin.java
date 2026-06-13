package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.spawn.SpawnData;
import me.jfenn.bingo.mixinhandler.PlayerManagerMixinHelper;
import me.jfenn.bingo.platform.block.BlockPosition;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Shadow @Final private MinecraftServer server;

    @Unique
    private final Logger log = LoggerFactory.getLogger(PlayerManagerMixin.class);

    @Inject(at = @At(value = "HEAD"), method = "sendToAround", cancellable = true)
    private void sendToAround(@Nullable PlayerEntity player, double x, double y, double z, double distance, RegistryKey<World> worldKey, Packet<?> packet, CallbackInfo ci) {
        // If the game is in PREGAME, prevent chaos by cancelling sound packets while in the lobby
        if (packet instanceof PlaySoundS2CPacket && PlayerManagerMixinHelper.Companion.shouldPreventLobbyChaos()) {
            ci.cancel();
        }
    }

    @Inject(at = @At(value = "RETURN"), method = "loadPlayerData", cancellable = true)
    private void loadPlayerData(ServerPlayerEntity player, CallbackInfoReturnable<NbtCompound> ci) {
        // Provide default player data (for the lobby dimension / spawnpoint) if null
        if (ci.getReturnValue() == null && PlayerManagerMixinHelper.Companion.shouldSpawnInLobby()) {
            SpawnData spawn = PlayerManagerMixinHelper.Companion.getPregameSpawnData();
            if (spawn == null) return;

            NbtCompound nbt = new NbtCompound();
            nbt.putString("Dimension", spawn.getDimension());

            NbtList position = new NbtList();
            BlockPosition blockPosition = spawn.getPosition();
            position.add(NbtDouble.of(blockPosition.getX() + 0.5));
            position.add(NbtDouble.of(blockPosition.getY()));
            position.add(NbtDouble.of(blockPosition.getZ() + 0.5));
            nbt.put("Pos", position);

            NbtList rotation = new NbtList();
            rotation.add(NbtFloat.of(spawn.getYaw())); // yaw
            rotation.add(NbtFloat.of(0f)); // pitch
            nbt.put("Rotation", rotation);

            player.readNbt(nbt);
            ci.setReturnValue(nbt);
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "respawnPlayer")
    private void respawnPlayer(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> info) {
        if (!PlayerManagerMixinHelper.Companion.exists()) return;

        if (alive) {
            // the player is exiting through the end portal...
            // if the spawnpoint is also in the end, then respawn them in the overworld
            if (PlayerManagerMixinHelper.Companion.shouldOverrideEndRespawn()) {
                var overworld = player.server.getOverworld();
                player.setSpawnPoint(overworld.getRegistryKey(), overworld.getSpawnPos(), overworld.getSpawnAngle(), false, false);
            }
            return;
        }

        // If the spawnpoint is a respawn anchor, get the current state
        // (this will be affected by getRespawnTarget)
        ServerWorld respawnAnchorWorld = server.getWorld(player.getSpawnPointDimension());
        BlockPos respawnAnchorBlockPos = player.getSpawnPointPosition();
        BlockState respawnAnchorBlockState = respawnAnchorWorld != null && respawnAnchorBlockPos != null
                ? respawnAnchorWorld.getBlockState(respawnAnchorBlockPos)
                : null;
        if (respawnAnchorBlockState == null || !(respawnAnchorBlockState.getBlock() instanceof RespawnAnchorBlock)) {
            // Indicates that this is not a respawn anchor block
            respawnAnchorWorld = null;
        }

        // target can be null if the player's spawnpoint failed... so it should default to the team spawn
        Optional<Vec3d> targetRespawn = Optional.empty();
        try {
            var spawnWorld = player.server.getWorld(player.getSpawnPointDimension());
            var spawnPosition = player.getSpawnPointPosition();
            if (spawnWorld != null && spawnPosition != null) {
                targetRespawn = PlayerEntity.findRespawnPosition(
                        spawnWorld,
                        spawnPosition,
                        player.getSpawnAngle(),
                        player.isSpawnForced(),
                        alive
                );
            }
        } catch (Throwable e) {
            log.error("Exception thrown during PlayerEntity.findRespawnPosition", e);
        }

        // Reset the respawn anchor to the state captured before getRespawnTarget was called
        if (respawnAnchorWorld != null) {
            respawnAnchorWorld.setBlockState(respawnAnchorBlockPos, respawnAnchorBlockState);
        }

        // if the targeted spawnpoint is invalid (i.e. a broken bed, or otherwise somewhere that would reset to the world spawn) ...
        if (targetRespawn.isEmpty()) {
            // change the player's spawnpoint back to the team spawn location
            PlayerManagerMixinHelper.Companion.setPlayerSpawnpoint(player);
        }
    }
}
