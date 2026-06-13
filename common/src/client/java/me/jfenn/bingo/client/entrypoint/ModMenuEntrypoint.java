package me.jfenn.bingo.client.entrypoint;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuEntrypoint implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            var yacl = ModMenuEntrypointHelper.INSTANCE.getYaclIntegration();
            return yacl.buildConfigScreen(parent);
        };
    }
}
