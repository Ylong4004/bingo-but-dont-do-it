package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import me.jfenn.bingo.integrations.ddi.DDITriggerType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * Reports actions for which Fabric has no reliable post-success callback.
 */
@Mixin(ServerPlayerEntity.class)
public class DDIServerPlayerActionMixin {

    @Unique
    private String bingo$ddiConsumedFoodId;

    @Inject(method = "openHandledScreen", at = @At("RETURN"))
    private void bingo$reportDDIContainerOpen(
            NamedScreenHandlerFactory factory,
            CallbackInfoReturnable<OptionalInt> cir
    ) {
        if (cir.getReturnValue().isPresent()) {
            DDITriggerDetector.reportContainerOpened((ServerPlayerEntity) (Object) this);
        }
    }

    @Inject(method = "consumeItem()V", at = @At("HEAD"))
    private void bingo$captureDDIConsumedFood(CallbackInfo ci) {
        ItemStack activeItem = ((ServerPlayerEntity) (Object) this).getActiveItem();
        bingo$ddiConsumedFoodId = !activeItem.isEmpty() && activeItem.contains(DataComponentTypes.FOOD)
                ? Registries.ITEM.getId(activeItem.getItem()).toString()
                : null;
    }

    @Inject(method = "consumeItem()V", at = @At("RETURN"))
    private void bingo$reportDDIConsumedFood(CallbackInfo ci) {
        if (bingo$ddiConsumedFoodId != null) {
            DDITriggerDetector.reportConsumed(
                    (ServerPlayerEntity) (Object) this,
                    bingo$ddiConsumedFoodId
            );
            bingo$ddiConsumedFoodId = null;
        }
    }

    @Inject(method = "increaseStat", at = @At("TAIL"))
    private void bingo$reportDDIStatAction(Stat<?> stat, int amount, CallbackInfo ci) {
        if (amount <= 0) return;

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Object value = stat.getValue();
        if (stat.getType() == Stats.PICKED_UP && value instanceof Item item) {
            DDITriggerDetector.reportPickedUp(
                    player,
                    item,
                    amount
            );
        }

        // This statistic is emitted for every successful player-owned drop,
        // including Q/Ctrl+Q and THROW actions from an inventory screen. Death
        // drops do not use the retained-ownership statistic path.
        if (stat.getType() == Stats.DROPPED && value instanceof Item item) {
            DDITriggerDetector.reportDropped(
                    player,
                    item,
                    amount,
                    item instanceof BlockItem
            );
        }

        if (value instanceof Identifier id) {
            if (id.equals(Stats.JUMP)) {
                DDITriggerDetector.reportJump(player);
            }
            if (id.equals(Stats.TRADED_WITH_VILLAGER)) {
                DDITriggerDetector.reportAction(
                        player,
                        DDITriggerType.VILLAGER_TRADE
                );
            }
        }
    }
}
