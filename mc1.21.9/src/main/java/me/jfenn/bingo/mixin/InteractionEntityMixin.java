package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.event.InteractionEntityEvents;
import me.jfenn.bingo.impl.InteractionEntityImpl;
import me.jfenn.bingo.impl.PlayerHandle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InteractionEntity.class)
abstract class InteractionEntityMixin extends Entity {

    public InteractionEntityMixin(EntityType<?> entityType, World world) {
        super(entityType, world);
    }

    @Inject(at = @At(value = "HEAD"), method = "handleAttack")
    public void handleAttack(Entity attacker, CallbackInfoReturnable<Boolean> ci) {
        if (attacker instanceof ServerPlayerEntity serverPlayer) {
            MinecraftServer server = serverPlayer.getEntityWorld().getServer();
            if (server != null) {
                InteractionEntityEvents.triggerInteract(
                        new InteractionEntityImpl((InteractionEntity) (Object) this),
                        new PlayerHandle(serverPlayer, null),
                        server
                );
            }
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "interact", cancellable = true)
    public void interact(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            MinecraftServer server = serverPlayer.getEntityWorld().getServer();
            if (server != null) {
                boolean success = InteractionEntityEvents.triggerInteract(
                        new InteractionEntityImpl((InteractionEntity) (Object) this),
                        new PlayerHandle(serverPlayer, null),
                        server
                );

                if (success) {
                    // For some reason the hand swing is broken in 1.21.2
                    serverPlayer.swingHand(Hand.MAIN_HAND, true);
                    ci.setReturnValue(ActionResult.SUCCESS);
                    ci.cancel();
                }
            }
        }
    }
}
