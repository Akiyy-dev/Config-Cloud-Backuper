package com.naocraftlab.configbackuper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.naocraftlab.configbackuper.core.BackupLimiter;
import com.naocraftlab.configbackuper.core.ConfigBackuper;
import com.naocraftlab.configbackuper.core.CriticalConfigBackuperException;
import com.naocraftlab.configbackuper.core.ModConfig;
import com.naocraftlab.configbackuper.core.ModConfigurationManager;
import com.naocraftlab.configbackuper.server.ConfigBackuperServerCommands;
import com.naocraftlab.configbackuper.server.ServerSyncNetworking;
import com.naocraftlab.configbackuper.util.LoggerWrapper;
import com.naocraftlab.configbackuper.util.LoggerWrapperSlf4j;
import com.naocraftlab.configbackuper.webdav.WebDavConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FabricModInitializer implements ModInitializer {

    private static final ModMetadata MOD_METADATA = FabricLoader.getInstance()
            .getModContainer("config-backuper")
            .map(ModContainer::getMetadata)
            .orElseThrow(() -> new CriticalConfigBackuperException("Failed to get mod metadata"));
    private static final LoggerWrapper LOGGER = new LoggerWrapperSlf4j(LoggerFactory.getLogger(MOD_METADATA.getName()));
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FabricModInitializer instance;

    private ModConfigurationManager modConfigurationManager;
    private ConfigBackuper configBackuper;
    private BackupLimiter backupLimiter;

    @Override
    public void onInitialize() {
        try {
            initScript();
        } catch (Exception e) {
            throw new CriticalConfigBackuperException(MOD_METADATA.getName() + " error", e);
        }
    }

    private void initScript() {
        instance = this;
        final Path configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_METADATA.getId() + ".json");
        modConfigurationManager = new ModConfigurationManager(LOGGER, configFile);

        final ModConfig modConfig = modConfigurationManager.read();
        configBackuper = new ConfigBackuper(LOGGER, modConfig);
        backupLimiter = new BackupLimiter(LOGGER, modConfig);

        // 不再自动执行备份
        // configBackuper.performBackup();
        // backupLimiter.removeOldBackups();

        // 服务端命令注册（客户端命令由 ConfigBackuperClient 注册）
        CommandRegistrationCallback.EVENT.register(ConfigBackuperServerCommands::register);
        ServerSyncNetworking.register();
    }

    // 公共方法
    public static FabricModInitializer getInstance() { return instance; }
    public ConfigBackuper getConfigBackuper() { return configBackuper; }
    public BackupLimiter getBackupLimiter() { return backupLimiter; }
    public ModConfigurationManager getModConfigurationManager() { return modConfigurationManager; }
    public static ModMetadata getModMetadata() { return MOD_METADATA; }
    public static LoggerWrapper getLogger() { return LOGGER; }

    /**
     * 重新加载配置并重新创建 ConfigBackuper 和 BackupLimiter 实例。
     * 在配置界面保存配置后调用，以确保备份器使用最新的配置。
     */
    public void reloadConfig() {
        final ModConfig modConfig = modConfigurationManager.read();
        this.configBackuper = new ConfigBackuper(LOGGER, modConfig);
        this.backupLimiter = new BackupLimiter(LOGGER, modConfig);
    }

    // ===== WebDAV 配置管理 =====

    /**
     * 加载 WebDAV 配置
     */
    public WebDavConfig loadWebDavConfig() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_METADATA.getId() + "_webdav.json");
        try {
            if (Files.exists(configFile)) {
                String json = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
                return GSON.fromJson(json, WebDavConfig.class);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load WebDAV config", e);
        }
        return new WebDavConfig();
    }

    /**
     * 保存 WebDAV 配置
     */
    public void saveWebDavConfig(WebDavConfig webDavConfig) {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_METADATA.getId() + "_webdav.json");
        try {
            Files.createDirectories(configFile.getParent());
            String json = GSON.toJson(webDavConfig);
            Files.writeString(configFile, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to save WebDAV config", e);
        }
    }
}
