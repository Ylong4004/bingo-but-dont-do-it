package me.jfenn.bingo.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Mutable @Accessor("saving")
    void setSaving(boolean saving);

    @Accessor("session")
    LevelStorage.Session getSession();

    @Invoker("loadWorld")
    void invokeLoadWorld();
}
