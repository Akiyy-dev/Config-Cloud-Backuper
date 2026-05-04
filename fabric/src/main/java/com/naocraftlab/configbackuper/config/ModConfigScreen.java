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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Path;

public class ModConfigScreen extends Screen {

    private final Screen parent;
    private final boolean isChinese;

    public ModConfigScreen(Screen parent) {
        super(Text.literal("Config Backuper"));
        this.parent = parent;
        // 检测游戏语言：如果当前语言代码以 zh 开头，则使用中文
        this.isChinese = MinecraftClient.getInstance().getLanguageManager()
                .getLanguage().startsWith("zh");
    }

    /**
     * 根据当前语言返回中文或英文文本
     */
    private Text t(String zh, String en) {
        return Text.literal(isChinese ? zh : en);
    }

    public Screen build() {
        FabricModInitializer mod = FabricModInitializer.getInstance();
        ModConfigurationManager configManager = mod.getModConfigurationManager();
        ModConfig config = configManager.read();
        WebDavConfig webDavConfig = mod.loadWebDavConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(t("Config Backuper 设置", "Config Backuper Settings"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ===== General Settings =====
        ConfigCategory general = builder.getOrCreateCategory(
                t("通用设置", "General Settings"));

        // includeGameConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        t("包含游戏配置", "Include Game Configs"),
                        config.isIncludeGameConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeGameConfigs)
                .setTooltip(t("将游戏配置文件加入备份", "Include game configuration files in the backup"))
                .build());

        // includeModConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        t("包含模组配置", "Include Mod Configs"),
                        config.isIncludeModConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeModConfigs)
                .setTooltip(t("将模组配置文件加入备份", "Include mod configuration files in the backup"))
                .build());

        // includeShaderPackConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        t("包含着色器配置", "Include Shader Configs"),
                        config.isIncludeShaderPackConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeShaderPackConfigs)
                .setTooltip(t("将着色器配置文件加入备份", "Include shader pack configuration files in the backup"))
                .build());

        // compressionEnabled
        general.addEntry(entryBuilder.startBooleanToggle(
                        t("启用压缩", "Compression Enabled"),
                        config.isCompressionEnabled())
                .setDefaultValue(true)
                .setSaveConsumer(config::setCompressionEnabled)
                .setTooltip(t("启用备份压缩（减小体积，但会略微降低创建速度）",
                        "Enable compression for backups (reduces size but slows creation)"))
                .build());

        // ===== Backup Storage Settings =====
        ConfigCategory storage = builder.getOrCreateCategory(
                t("备份存储", "Backup Storage"));

        // backupFolder
        final Path backupFolder = config.getBackupFolder();
        storage.addEntry(entryBuilder.startStrField(
                        t("备份文件夹", "Backup Folder"),
                        backupFolder != null ? backupFolder.toString() : "")
                .setDefaultValue("./config-backuper-backups")
                .setSaveConsumer(value -> config.setBackupFolder(Path.of(value)))
                .setTooltip(t("备份文件存储目录", "Directory for storing backups"))
                .build());

        // backupFilePrefix
        storage.addEntry(entryBuilder.startStrField(
                        t("备份文件前缀", "Backup File Prefix"),
                        config.getBackupFilePrefix() != null ? config.getBackupFilePrefix() : "")
                .setDefaultValue("backup")
                .setSaveConsumer(config::setBackupFilePrefix)
                .setTooltip(t("备份文件名称前缀", "Prefix for backup file names"))
                .build());

        // backupFileSuffix
        storage.addEntry(entryBuilder.startStrField(
                        t("备份文件后缀", "Backup File Suffix"),
                        config.getBackupFileSuffix() != null ? config.getBackupFileSuffix() : "")
                .setDefaultValue(".zip")
                .setSaveConsumer(config::setBackupFileSuffix)
                .setTooltip(t("备份文件名称后缀", "Suffix for backup file names"))
                .build());

        // maxBackups
        storage.addEntry(entryBuilder.startIntField(
                        t("最大备份数", "Max Backups"),
                        config.getMaxBackups())
                .setDefaultValue(10)
                .setMin(-1)
                .setSaveConsumer(config::setMaxBackups)
                .setTooltip(t("最大保留备份数量（-1 表示不限制）",
                        "Maximum number of backups to keep (-1 = unlimited)"))
                .build());

        // ===== WebDAV Cloud Backup Settings =====
        ConfigCategory webdavCategory = builder.getOrCreateCategory(
                t("WebDAV 云备份", "WebDAV Cloud Backup"));

        // webdavEnabled
        webdavCategory.addEntry(entryBuilder.startBooleanToggle(
                        t("启用 WebDAV 上传", "Enable WebDAV Upload"),
                        webDavConfig.isEnabled())
                .setDefaultValue(false)
                .setSaveConsumer(webDavConfig::setEnabled)
                .setTooltip(t("备份完成后自动上传到 WebDAV 服务器",
                        "Upload backups to WebDAV server after creation"))
                .build());

        // serverUrl
        webdavCategory.addEntry(entryBuilder.startStrField(
                        t("服务器地址", "Server URL"),
                        webDavConfig.getServerUrl())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setServerUrl)
                .setTooltip(t("WebDAV 服务器地址（例如：https://example.com/remote.php/dav/files/user/）",
                        "WebDAV server URL (e.g., https://example.com/remote.php/dav/files/user/)"))
                .build());

        // username
        webdavCategory.addEntry(entryBuilder.startStrField(
                        t("用户名", "Username"),
                        webDavConfig.getUsername())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setUsername)
                .setTooltip(t("WebDAV 账户用户名", "WebDAV account username"))
                .build());

        // password
        webdavCategory.addEntry(entryBuilder.startStrField(
                        t("密码", "Password"),
                        webDavConfig.getPassword())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setPassword)
                .setTooltip(t("WebDAV 账户密码", "WebDAV account password"))
                .build());

        // remotePath
        webdavCategory.addEntry(entryBuilder.startStrField(
                        t("远程路径", "Remote Path"),
                        webDavConfig.getRemotePath())
                .setDefaultValue("/ConfigBackuper/")
                .setSaveConsumer(webDavConfig::setRemotePath)
                .setTooltip(t("WebDAV 服务器上的远程目录路径",
                        "Remote directory path on WebDAV server"))
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
