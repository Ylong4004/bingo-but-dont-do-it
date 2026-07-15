package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import me.jfenn.bingo.integrations.ddi.DDITriggerType;
import net.minecraft.block.AbstractCauldronBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries the clicked block into the post-success screen-open hook. */
@Mixin(ServerPlayerInteractionManager.class)
public class DDIServerPlayerInteractionMixin {

    @Unique
    private Item bingo$ddiItemBeforeBlockInteraction;

    @Unique
    private Block bingo$ddiBlockBeforeInteraction;

    @Inject(
            method = "interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD")
    )
    private void bingo$beginDDIBlockInteraction(
            ServerPlayerEntity player,
            World world,
            ItemStack stack,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        bingo$ddiItemBeforeBlockInteraction = player.getStackInHand(hand).getItem();
        bingo$ddiBlockBeforeInteraction = world.getBlockState(hitResult.getBlockPos()).getBlock();
        DDITriggerDetector.beginBlockInteraction(
                player,
                Registries.BLOCK.getId(world.getBlockState(hitResult.getBlockPos()).getBlock()).toString()
        );
    }

    @Inject(
            method = "interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN")
    )
    private void bingo$endDDIBlockInteraction(
            ServerPlayerEntity player,
            World world,
            ItemStack stack,
            Hand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (cir.getReturnValue().isAccepted()
                && bingo$ddiBlockBeforeInteraction instanceof AbstractCauldronBlock) {
            if (bingo$ddiItemBeforeBlockInteraction == Items.WATER_BUCKET) {
                DDITriggerDetector.reportAction(player, DDITriggerType.EMPTY_BUCKET_WATER);
            } else if (bingo$ddiItemBeforeBlockInteraction == Items.LAVA_BUCKET) {
                DDITriggerDetector.reportAction(player, DDITriggerType.EMPTY_BUCKET_LAVA);
            } else if (bingo$ddiItemBeforeBlockInteraction == Items.BUCKET
                    && bingo$ddiBlockBeforeInteraction == Blocks.WATER_CAULDRON) {
                DDITriggerDetector.reportAction(player, DDITriggerType.FILL_BUCKET_WATER);
            } else if (bingo$ddiItemBeforeBlockInteraction == Items.BUCKET
                    && bingo$ddiBlockBeforeInteraction == Blocks.LAVA_CAULDRON) {
                DDITriggerDetector.reportAction(player, DDITriggerType.FILL_BUCKET_LAVA);
            }
        }
        DDITriggerDetector.endBlockInteraction(player);
        bingo$ddiItemBeforeBlockInteraction = null;
        bingo$ddiBlockBeforeInteraction = null;
    }
}
