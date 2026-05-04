package com.naocraftlab.configbackuper.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.naocraftlab.configbackuper.util.LoggerWrapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 模组配置管理器 - 负责读取和保存 JSON 格式的配置文件
 */
public class ModConfigurationManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final LoggerWrapper logger;
    private final Path configFile;

    public ModConfigurationManager(LoggerWrapper logger, Path configFile) {
        this.logger = logger;
        this.configFile = configFile;
    }

    /**
     * 读取配置文件，如果文件不存在则返回默认配置
     */
    public ModConfig read() {
        if (Files.exists(configFile)) {
            try {
                final String json = Files.readString(configFile, StandardCharsets.UTF_8);
                return GSON.fromJson(json, (Type) ModConfig.class);
            } catch (Exception e) {
                logger.error("Failed to read config file, using default config", e);
            }
        }
        return new ModConfig();
    }

    /**
     * 保存配置到文件
     */
    public void save(ModConfig config) {
        try {
            Files.createDirectories(configFile.getParent());
            final Path tempFile = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            final String json = GSON.toJson(config);
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);
            Files.move(tempFile, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Configuration saved to " + configFile);
        } catch (IOException e) {
            logger.error("Failed to save config file", e);
        }
    }
}
