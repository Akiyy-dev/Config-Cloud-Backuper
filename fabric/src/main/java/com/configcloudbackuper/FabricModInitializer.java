package com.configcloudbackuper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.configcloudbackuper.core.BackupLimiter;
import com.configcloudbackuper.core.ConfigBackuper;
import com.configcloudbackuper.core.CriticalConfigBackuperException;
import com.configcloudbackuper.core.ModConfig;
import com.configcloudbackuper.core.ModConfigPaths;
import com.configcloudbackuper.core.ModConfigurationManager;
import com.configcloudbackuper.server.ConfigBackuperServerCommands;
import com.configcloudbackuper.server.ServerSyncNetworking;
import com.configcloudbackuper.util.LoggerWrapper;
import com.configcloudbackuper.util.LoggerWrapperSlf4j;
import com.configcloudbackuper.webdav.WebDavConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FabricModInitializer implements ModInitializer {

    private static final ModMetadata MOD_METADATA = FabricLoader.getInstance()
            .getModContainer("config-cloud-backuper")
            .map(ModContainer::getMetadata)
            .orElseThrow(() -> new CriticalConfigBackuperException("Failed to get mod metadata"));
    private static final LoggerWrapper LOGGER = new LoggerWrapperSlf4j(LoggerFactory.getLogger(MOD_METADATA.getName()));
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FabricModInitializer instance;

    private ModConfigurationManager clientModConfigurationManager;
    private ModConfigurationManager serverModConfigurationManager;
    private ConfigBackuper clientConfigBackuper;
    private BackupLimiter clientBackupLimiter;
    private ConfigBackuper serverConfigBackuper;
    private BackupLimiter serverBackupLimiter;

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
        String modId = MOD_METADATA.getId();
        Path clientFile = ModConfigPaths.clientConfigFile(modId);
        Path serverFile = ModConfigPaths.serverConfigFile(modId);
        Path legacyFile = ModConfigPaths.legacyConfigFile(modId);

        migrateLegacyConfigIfNeeded(legacyFile, clientFile, serverFile);

        clientModConfigurationManager = new ModConfigurationManager(LOGGER, clientFile);
        serverModConfigurationManager = new ModConfigurationManager(LOGGER, serverFile);

        ensureDefaultConfigFile(clientModConfigurationManager, clientFile);
        ensureDefaultConfigFile(serverModConfigurationManager, serverFile);

        ModConfig clientCfg = clientModConfigurationManager.read();
        ModConfig serverCfg = serverModConfigurationManager.read();
        clientConfigBackuper = new ConfigBackuper(LOGGER, clientCfg);
        clientBackupLimiter = new BackupLimiter(LOGGER, clientCfg);
        serverConfigBackuper = new ConfigBackuper(LOGGER, serverCfg);
        serverBackupLimiter = new BackupLimiter(LOGGER, serverCfg);

        CommandRegistrationCallback.EVENT.register(ConfigBackuperServerCommands::register);
        ServerSyncNetworking.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ModConfig cfg = getInstance().getServerModConfigurationManager().read();
            ServerSyncNetworking.sendCapability(handler.getPlayer(), cfg.isClientUploadToServerEnabled());
        });
    }

    private static void migrateLegacyConfigIfNeeded(Path legacyFile, Path clientFile, Path serverFile) {
        try {
            if (!Files.isRegularFile(legacyFile)) {
                return;
            }
            if (!Files.isRegularFile(clientFile)) {
                Files.copy(legacyFile, clientFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("已将旧版主配置迁移为客户端配置: " + clientFile.getFileName());
            }
            if (!Files.isRegularFile(serverFile)) {
                Files.copy(legacyFile, serverFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("已将旧版主配置迁移为服务端配置: " + serverFile.getFileName());
            }
        } catch (Exception e) {
            LOGGER.error("迁移旧版 config-cloud-backuper.json 失败", e);
        }
    }

    private static void ensureDefaultConfigFile(ModConfigurationManager mgr, Path file) {
        if (!Files.isRegularFile(file)) {
            mgr.save(mgr.read());
        }
    }

    public static FabricModInitializer getInstance() {
        return instance;
    }

    public ModConfigurationManager getClientModConfigurationManager() {
        return clientModConfigurationManager;
    }

    public ModConfigurationManager getServerModConfigurationManager() {
        return serverModConfigurationManager;
    }

    public ConfigBackuper getClientConfigBackuper() {
        return clientConfigBackuper;
    }

    public BackupLimiter getClientBackupLimiter() {
        return clientBackupLimiter;
    }

    public ConfigBackuper getServerConfigBackuper() {
        return serverConfigBackuper;
    }

    public BackupLimiter getServerBackupLimiter() {
        return serverBackupLimiter;
    }

    public static ModMetadata getModMetadata() {
        return MOD_METADATA;
    }

    public static LoggerWrapper getLogger() {
        return LOGGER;
    }

    /** 重新加载客户端主配置并刷新客户端侧备份器（ModMenu 界面保存后调用）。 */
    public void reloadClientConfig() {
        ModConfig c = clientModConfigurationManager.read();
        this.clientConfigBackuper = new ConfigBackuper(LOGGER, c);
        this.clientBackupLimiter = new BackupLimiter(LOGGER, c);
    }

    /** 重新加载服务端主配置并刷新服务端侧备份器（服务端 config / remote server set 后调用）。 */
    public void reloadServerConfig() {
        ModConfig c = serverModConfigurationManager.read();
        this.serverConfigBackuper = new ConfigBackuper(LOGGER, c);
        this.serverBackupLimiter = new BackupLimiter(LOGGER, c);
    }

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
