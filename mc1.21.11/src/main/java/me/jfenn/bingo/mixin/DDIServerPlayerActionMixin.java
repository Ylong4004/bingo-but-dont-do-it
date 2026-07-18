package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import me.jfenn.bingo.integrations.ddi.DDITriggerType;
import me.jfenn.bingo.integrations.ddi.DDISpecialEventHooks;
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
 * 上报 Fabric 没有可靠成功后回调的操作。
 */
@Mixin(ServerPlayerEntity.class)
public class DDIServerPlayerActionMixin {

    @Unique
    private String bingo$ddiConsumedFoodId;

    @Unique
    private boolean bingo$halveDDIFoodGain;

    @Unique
    private int bingo$ddiFoodBefore;

    @Unique
    private float bingo$ddiSaturationBefore;

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
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ItemStack activeItem = player.getActiveItem();
        bingo$ddiConsumedFoodId = !activeItem.isEmpty() && activeItem.contains(DataComponentTypes.FOOD)
                ? Registries.ITEM.getId(activeItem.getItem()).toString()
                : null;
        bingo$halveDDIFoodGain = bingo$ddiConsumedFoodId != null
                && DDISpecialEventHooks.hasHungerDisease(player);
        if (bingo$halveDDIFoodGain) {
            bingo$ddiFoodBefore = player.getHungerManager().getFoodLevel();
            bingo$ddiSaturationBefore = player.getHungerManager().getSaturationLevel();
        }
    }

    @Inject(method = "consumeItem()V", at = @At("RETURN"))
    private void bingo$reportDDIConsumedFood(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (bingo$halveDDIFoodGain) {
            int foodAfter = player.getHungerManager().getFoodLevel();
            float saturationAfter = player.getHungerManager().getSaturationLevel();
            int foodGain = Math.max(0, foodAfter - bingo$ddiFoodBefore);
            float saturationGain = Math.max(0F, saturationAfter - bingo$ddiSaturationBefore);
            player.getHungerManager().setFoodLevel(bingo$ddiFoodBefore + foodGain / 2);
            player.getHungerManager().setSaturationLevel(
                    bingo$ddiSaturationBefore + saturationGain / 2F
            );
        }
        bingo$halveDDIFoodGain = false;
        if (bingo$ddiConsumedFoodId != null) {
            DDITriggerDetector.reportConsumed(
                    player,
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

        // 每次由玩家成功执行的丢弃都会产生此统计，包括 Q/Ctrl+Q 和背包界面的
        // THROW 操作。死亡掉落不会经过保留所有权的统计路径。
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
