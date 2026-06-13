package me.jfenn.bingo.mixin;

import me.jfenn.bingo.mixinhandler.GameRuleOverrideHelper;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRules.class)
public class GameRuleOverrideMixin {
    @Inject(at = @At("HEAD"), method = "getBoolean(Lnet/minecraft/world/GameRules$Key;)Z", cancellable = true)
    void getBoolean(GameRules.Key<?> key, CallbackInfoReturnable<Boolean> ci) {
        Object override = GameRuleOverrideHelper.INSTANCE.getGameRuleOverrides().get(key.getName());
        if (override instanceof Boolean b) {
            ci.setReturnValue(b);
            ci.cancel();
        }
    }
}
