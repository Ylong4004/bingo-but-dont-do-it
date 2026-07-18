package me.jfenn.bingo.mixin;

import me.jfenn.bingo.integrations.ddi.DDITriggerDetector;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 只上报服务端原版逻辑已经接受的方块放置操作。 */
@Mixin(BlockItem.class)
public class DDIBlockItemMixin {

    @Redirect(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;emitGameEvent(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/event/GameEvent$Emitter;)V"
            )
    )
    private void bingo$reportDDIPlacedBlock(
            World world,
            RegistryEntry<GameEvent> event,
            BlockPos pos,
            GameEvent.Emitter emitter
    ) {
        world.emitGameEvent(event, pos, emitter);
        if (emitter.sourceEntity() instanceof ServerPlayerEntity player
                && emitter.affectedState() != null) {
            DDITriggerDetector.reportPlaced(
                    player,
                    emitter.affectedState().getBlock()
            );
        }
    }
}
