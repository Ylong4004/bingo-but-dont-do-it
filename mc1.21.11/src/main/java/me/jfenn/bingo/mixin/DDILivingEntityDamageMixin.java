package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Complements Fabric AFTER_DAMAGE, which deliberately omits fatal damage. */
@Mixin(LivingEntity.class)
public class DDILivingEntityDamageMixin {

    @Unique
    private float bingo$ddiHealthBeforeDamage;

    @Inject(
            method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("HEAD")
    )
    private void bingo$captureDDIHealthBeforeDamage(
            ServerWorld world,
            DamageSource source,
            float damageTaken,
            CallbackInfoReturnable<Boolean> cir
    ) {
        bingo$ddiHealthBeforeDamage = ((LivingEntity) (Object) this).getHealth();
    }

    @Inject(
            method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;onDeath(Lnet/minecraft/entity/damage/DamageSource;)V"
            )
    )
    private void bingo$reportDDIFatalDamage(
            ServerWorld world,
            DamageSource source,
            float damageTaken,
            CallbackInfoReturnable<Boolean> cir
    ) {
        LivingEntity entity = (LivingEntity) (Object) this;
        DDITriggerDetector.reportDamage(entity, source, damageTaken);
        DDITriggerDetector.reportHealthLoss(
                entity,
                Math.max(0.0F, bingo$ddiHealthBeforeDamage - entity.getHealth())
        );
    }

    @Inject(
            method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("RETURN")
    )
    private void bingo$reportDDIActualHealthLoss(
            ServerWorld world,
            DamageSource source,
            float damageTaken,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue()) return;
        LivingEntity entity = (LivingEntity) (Object) this;
        DDITriggerDetector.reportHealthLoss(
                entity,
                Math.max(0.0F, bingo$ddiHealthBeforeDamage - entity.getHealth())
        );
    }
}
