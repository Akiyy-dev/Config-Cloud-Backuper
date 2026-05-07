package com.configcloudbackuper.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> {
                MinecraftClient c = MinecraftClient.getInstance();
                if (c.player != null) {
                    c.player.sendMessage(
                            Text.literal("[Config Backuper] 请安装 Cloth Config API 以使用图形界面，或使用 /config_backuper 命令。"),
                            false);
                }
                return parent;
            };
        }
        return ModConfigScreen::create;
    }
}
