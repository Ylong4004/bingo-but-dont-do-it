package me.jfenn.bingo.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.village.raid.Raid;
import net.minecraft.village.raid.RaidManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RaidManager.class)
public interface RaidManagerAccessor {
    @Accessor("raids")
    Int2ObjectMap<Raid> getRaids();
}
