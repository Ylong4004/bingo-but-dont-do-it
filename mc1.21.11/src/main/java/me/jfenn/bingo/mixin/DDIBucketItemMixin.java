package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import me.jfenn.bingo.integrations.ddi.DDITriggerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidDrainable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reports bucket actions only after vanilla confirms the fluid operation. */
@Mixin(BucketItem.class)
public class DDIBucketItemMixin {

    @Redirect(
            method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/FluidDrainable;tryDrainFluid(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack bingo$reportDDIFilledBucket(
            FluidDrainable drainable,
            LivingEntity user,
            WorldAccess world,
            BlockPos pos,
            BlockState state
    ) {
        ItemStack filledStack = drainable.tryDrainFluid(user, world, pos, state);
        if (!filledStack.isEmpty() && user instanceof ServerPlayerEntity player) {
            if (filledStack.getItem() == Items.WATER_BUCKET) {
                DDITriggerDetector.reportAction(player, DDITriggerType.FILL_BUCKET_WATER);
            } else if (filledStack.getItem() == Items.LAVA_BUCKET) {
                DDITriggerDetector.reportAction(player, DDITriggerType.FILL_BUCKET_LAVA);
            }
        }
        return filledStack;
    }

    @Redirect(
            method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/BucketItem;placeFluid(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/hit/BlockHitResult;)Z"
            )
    )
    private boolean bingo$reportDDIEmptiedBucket(
            BucketItem bucket,
            LivingEntity user,
            World world,
            BlockPos pos,
            BlockHitResult hitResult
    ) {
        boolean placed = bucket.placeFluid(user, world, pos, hitResult);
        if (placed && user instanceof ServerPlayerEntity player) {
            if (bucket == Items.WATER_BUCKET) {
                DDITriggerDetector.reportAction(player, DDITriggerType.EMPTY_BUCKET_WATER);
            } else if (bucket == Items.LAVA_BUCKET) {
                DDITriggerDetector.reportAction(player, DDITriggerType.EMPTY_BUCKET_LAVA);
            }
        }
        return placed;
    }
}
