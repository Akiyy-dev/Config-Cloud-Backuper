package com.naocraftlab.configbackuper.config;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.core.BackupLimiter;
import com.naocraftlab.configbackuper.core.ConfigBackuper;
import com.naocraftlab.configbackuper.core.ModConfig;
import com.naocraftlab.configbackuper.core.ModConfigurationManager;
import com.naocraftlab.configbackuper.webdav.WebDavConfig;
import com.naocraftlab.configbackuper.webdav.WebDavUploader;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Path;

public class ModConfigScreen extends Screen {

    private final Screen parent;

    public ModConfigScreen(Screen parent) {
        super(Text.literal("Config Backuper"));
        this.parent = parent;
    }

    public Screen build() {
        FabricModInitializer mod = FabricModInitializer.getInstance();
        ModConfigurationManager configManager = mod.getModConfigurationManager();
        ModConfig config = configManager.read();
        WebDavConfig webDavConfig = mod.loadWebDavConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("Config Backuper Settings"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ===== General Settings =====
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General Settings"));

        // includeGameConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        Text.literal("Include Game Configs"),
                        config.isIncludeGameConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeGameConfigs)
                .setTooltip(Text.literal("Include game configuration files in the backup"))
                .build());

        // includeModConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        Text.literal("Include Mod Configs"),
                        config.isIncludeModConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeModConfigs)
                .setTooltip(Text.literal("Include mod configuration files in the backup"))
                .build());

        // includeShaderPackConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        Text.literal("Include Shader Configs"),
                        config.isIncludeShaderPackConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeShaderPackConfigs)
                .setTooltip(Text.literal("Include shader pack configuration files in the backup"))
                .build());

        // compressionEnabled
        general.addEntry(entryBuilder.startBooleanToggle(
                        Text.literal("Compression Enabled"),
                        config.isCompressionEnabled())
                .setDefaultValue(true)
                .setSaveConsumer(config::setCompressionEnabled)
                .setTooltip(Text.literal("Enable compression for backups (reduces size but slows creation)"))
                .build());

        // ===== Backup Storage Settings =====
        ConfigCategory storage = builder.getOrCreateCategory(Text.literal("Backup Storage"));

        // backupFolder
        final Path backupFolder = config.getBackupFolder();
        storage.addEntry(entryBuilder.startStrField(
                        Text.literal("Backup Folder"),
                        backupFolder != null ? backupFolder.toString() : "")
                .setDefaultValue("./config-backuper-backups")
                .setSaveConsumer(value -> config.setBackupFolder(Path.of(value)))
                .setTooltip(Text.literal("Directory for storing backups"))
                .build());

        // backupFilePrefix
        storage.addEntry(entryBuilder.startStrField(
                        Text.literal("Backup File Prefix"),
                        config.getBackupFilePrefix() != null ? config.getBackupFilePrefix() : "")
                .setDefaultValue("backup")
                .setSaveConsumer(config::setBackupFilePrefix)
                .setTooltip(Text.literal("Prefix for backup file names"))
                .build());

        // backupFileSuffix
        storage.addEntry(entryBuilder.startStrField(
                        Text.literal("Backup File Suffix"),
                        config.getBackupFileSuffix() != null ? config.getBackupFileSuffix() : "")
                .setDefaultValue(".zip")
                .setSaveConsumer(config::setBackupFileSuffix)
                .setTooltip(Text.literal("Suffix for backup file names"))
                .build());

        // maxBackups
        storage.addEntry(entryBuilder.startIntField(
                        Text.literal("Max Backups"),
                        config.getMaxBackups())
                .setDefaultValue(10)
                .setMin(-1)
                .setSaveConsumer(config::setMaxBackups)
                .setTooltip(Text.literal("Maximum number of backups to keep (-1 = unlimited)"))
                .build());

        // ===== WebDAV Cloud Backup Settings =====
        ConfigCategory webdavCategory = builder.getOrCreateCategory(Text.literal("WebDAV Cloud Backup"));

        // webdavEnabled
        webdavCategory.addEntry(entryBuilder.startBooleanToggle(
                        Text.literal("Enable WebDAV Upload"),
                        webDavConfig.isEnabled())
                .setDefaultValue(false)
                .setSaveConsumer(webDavConfig::setEnabled)
                .setTooltip(Text.literal("Upload backups to WebDAV server after creation"))
                .build());

        // serverUrl
        webdavCategory.addEntry(entryBuilder.startStrField(
                        Text.literal("Server URL"),
                        webDavConfig.getServerUrl())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setServerUrl)
                .setTooltip(Text.literal("WebDAV server URL (e.g., https://example.com/remote.php/dav/files/user/)"))
                .build());

        // username
        webdavCategory.addEntry(entryBuilder.startStrField(
                        Text.literal("Username"),
                        webDavConfig.getUsername())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setUsername)
                .setTooltip(Text.literal("WebDAV account username"))
                .build());

        // password
        webdavCategory.addEntry(entryBuilder.startStrField(
                        Text.literal("Password"),
                        webDavConfig.getPassword())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setPassword)
                .setTooltip(Text.literal("WebDAV account password"))
                .build());

        // remotePath
        webdavCategory.addEntry(entryBuilder.startStrField(
                        Text.literal("Remote Path"),
                        webDavConfig.getRemotePath())
                .setDefaultValue("/ConfigBackuper/")
                .setSaveConsumer(webDavConfig::setRemotePath)
                .setTooltip(Text.literal("Remote directory path on WebDAV server"))
                .build());

        // ===== Save Handler =====
        builder.setSavingRunnable(() -> {
            // Save main configuration
            configManager.save(config);

            // Save WebDAV configuration
            mod.saveWebDavConfig(webDavConfig);

            // Reload backuper and limiter with new config, then trigger backup
            try {
                mod.reloadConfig();

                ConfigBackuper backuper = mod.getConfigBackuper();
                BackupLimiter limiter = mod.getBackupLimiter();

                backuper.performBackup();
                limiter.removeOldBackups();

                FabricModInitializer.getLogger().info("Backup completed after config save");

                // WebDAV upload
                if (webDavConfig.isEnabled()) {
                    uploadLatestBackup(config.getBackupFolder(), config, webDavConfig);
                }
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("Failed to perform backup after config save", e);
            }
        });

        return builder.build();
    }

    /**
     * 上传最新的备份文件到 WebDAV
     */
    private void uploadLatestBackup(Path backupFolder, ModConfig config, WebDavConfig webDavConfig) {
        try {
            File folder = backupFolder.toFile();
            if (!folder.exists() || !folder.isDirectory()) return;

            String prefix = config.getBackupFilePrefix() != null ? config.getBackupFilePrefix() : "";
            String suffix = config.getBackupFileSuffix() != null ? config.getBackupFileSuffix() : ".zip";

            File[] files = folder.listFiles((dir, name) ->
                    name.startsWith(prefix) && name.endsWith(suffix));

            if (files == null || files.length == 0) return;

            // 找到最新的文件
            File latestFile = files[0];
            for (File f : files) {
                if (f.lastModified() > latestFile.lastModified()) {
                    latestFile = f;
                }
            }

            WebDavUploader uploader = new WebDavUploader();
            String error = uploader.uploadBackup(latestFile.toPath(), webDavConfig);

            if (error != null) {
                FabricModInitializer.getLogger().error("WebDAV upload failed: " + error);
            } else {
                FabricModInitializer.getLogger().info("WebDAV upload completed successfully");
            }
        } catch (Exception e) {
            FabricModInitializer.getLogger().error("Failed to upload backup to WebDAV", e);
        }
    }

    @Override
    public void init() {
        super.init();
        if (this.client != null) {
            this.client.setScreen(build());
        }
    }
}
