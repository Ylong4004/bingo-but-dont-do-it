package me.jfenn.bingo.mixin;

import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapState.class)
public interface MapStateAccessor {
    @Invoker("markDirty")
    void invokeMarkDirty(int x, int z);
}
