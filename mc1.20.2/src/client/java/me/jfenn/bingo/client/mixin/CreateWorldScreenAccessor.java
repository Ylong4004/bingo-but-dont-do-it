package me.jfenn.bingo.client.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.ResourcePackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;
import java.util.function.Consumer;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Invoker("getDataPackTempDir")
    Path invokeGetDataPackTempDir();
    @Invoker("getScannedPack")
    Pair<Path, ResourcePackManager> invokeGetScannedPack(DataConfiguration dataConfiguration);
    @Invoker("applyDataPacks")
    void invokeApplyDataPacks(ResourcePackManager dataPackManager, boolean fromPackScreen, Consumer<DataConfiguration> configurationSetter);
    @Invoker("validateDataPacks")
    void invokeValidateDataPacks(ResourcePackManager dataPackManager, DataConfiguration dataConfiguration, Consumer<DataConfiguration> configurationSetter);
}
