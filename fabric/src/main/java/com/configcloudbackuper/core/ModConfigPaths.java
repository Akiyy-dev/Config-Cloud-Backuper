package com.configcloudbackuper.core;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * 主配置文件路径：客户端与服务端各一份 JSON（结构均为 {@link ModConfig}）。
 */
public final class ModConfigPaths {
    private ModConfigPaths() {
    }

    public static Path clientConfigFile(String modId) {
        return FabricLoader.getInstance().getConfigDir().resolve(modId + "-client.json");
    }

    public static Path serverConfigFile(String modId) {
        return FabricLoader.getInstance().getConfigDir().resolve(modId + "-server.json");
    }

    /** 旧版单文件，用于一次性迁移 */
    public static Path legacyConfigFile(String modId) {
        return FabricLoader.getInstance().getConfigDir().resolve(modId + ".json");
    }
}
