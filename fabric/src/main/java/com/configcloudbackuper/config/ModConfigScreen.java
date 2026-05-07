package com.configcloudbackuper.config;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.core.BackupLimiter;
import com.configcloudbackuper.core.ConfigBackuper;
import com.configcloudbackuper.core.ModConfig;
import com.configcloudbackuper.core.ModConfigurationManager;
import com.configcloudbackuper.webdav.WebDavConfig;
import com.configcloudbackuper.webdav.WebDavUploader;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Path;

/**
 * 配置界面工厂 - 构建 Cloth Config 配置界面。
 * <p>
 * 注意：此类不再继承 Screen，而是作为静态工厂方法使用。
 * ModMenuIntegration 通过 {@link #create(Screen)} 直接获取 Cloth Config 的 Screen 实例，
 * 避免了继承 Screen 导致的 init() → setScreen() 递归初始化问题。
 */
public class ModConfigScreen {

    private ModConfigScreen() {
        // 工具类，禁止实例化
    }

    /**
     * 根据当前语言返回中文或英文文本
     */
    private static Text t(boolean isChinese, String zh, String en) {
        return Text.literal(isChinese ? zh : en);
    }

    /**
     * 创建 Cloth Config 配置界面
     *
     * @param parent 父 Screen
     * @return Cloth Config 的配置界面 Screen
     */
    public static Screen create(Screen parent) {
        try {
            return build(parent);
        } catch (Exception e) {
            FabricModInitializer.getLogger().error("Failed to build config screen", e);
            // 返回父屏幕，避免黑屏
            return parent;
        }
    }

    private static Screen build(Screen parent) {
        // 检测游戏语言
        boolean isChinese = MinecraftClient.getInstance().getLanguageManager()
                .getLanguage().startsWith("zh");

        FabricModInitializer mod = FabricModInitializer.getInstance();
        ModConfigurationManager configManager = mod.getModConfigurationManager();
        ModConfig config = configManager.read();
        WebDavConfig webDavConfig = mod.loadWebDavConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(t(isChinese, "Config Backuper 设置", "Config Backuper Settings"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ===== General Settings =====
        ConfigCategory general = builder.getOrCreateCategory(
                t(isChinese, "通用设置", "General Settings"));

        // includeGameConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "包含游戏配置", "Include Game Configs"),
                        config.isIncludeGameConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeGameConfigs)
                .setTooltip(t(isChinese, "将游戏配置文件加入备份", "Include game configuration files in the backup"))
                .build());

        // includeModConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "包含模组配置", "Include Mod Configs"),
                        config.isIncludeModConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeModConfigs)
                .setTooltip(t(isChinese, "将模组配置文件加入备份", "Include mod configuration files in the backup"))
                .build());

        // includeShaderPackConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "包含着色器配置", "Include Shader Configs"),
                        config.isIncludeShaderPackConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeShaderPackConfigs)
                .setTooltip(t(isChinese, "将着色器配置文件加入备份", "Include shader pack configuration files in the backup"))
                .build());

        // includeSchematics
        general.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "包含 Schematics", "Include Schematics"),
                        config.isIncludeSchematics())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeSchematics)
                .setTooltip(t(isChinese, "将 schematics 文件加入备份", "Include schematics files in the backup"))
                .build());

        // include3dSkin
        general.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "包含 3D-Skin", "Include 3D-Skin"),
                        config.isInclude3dSkin())
                .setDefaultValue(true)
                .setSaveConsumer(config::setInclude3dSkin)
                .setTooltip(t(isChinese, "将 3d-skin 文件加入备份", "Include 3d-skin files in the backup"))
                .build());

        // includeSyncmatics
        general.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "包含 Syncmatics", "Include Syncmatics"),
                        config.isIncludeSyncmatics())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeSyncmatics)
                .setTooltip(t(isChinese, "将 syncmatics 文件加入备份", "Include syncmatics files in the backup"))
                .build());

        // includeDefaultConfigs
        general.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "包含 Default Configs", "Include Default Configs"),
                        config.isIncludeDefaultConfigs())
                .setDefaultValue(true)
                .setSaveConsumer(config::setIncludeDefaultConfigs)
                .setTooltip(t(isChinese, "将 defaultconfigs 文件加入备份", "Include defaultconfigs files in the backup"))
                .build());

        // compressionEnabled
        general.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "启用压缩", "Compression Enabled"),
                        config.isCompressionEnabled())
                .setDefaultValue(true)
                .setSaveConsumer(config::setCompressionEnabled)
                .setTooltip(t(isChinese, "启用备份压缩（减小体积，但会略微降低创建速度）",
                        "Enable compression for backups (reduces size but slows creation)"))
                .build());

        // ===== Backup Storage Settings =====
        ConfigCategory storage = builder.getOrCreateCategory(
                t(isChinese, "备份存储", "Backup Storage"));

        // backupFolder
        final Path backupFolder = config.getBackupFolder();
        storage.addEntry(entryBuilder.startStrField(
                        t(isChinese, "备份文件夹", "Backup Folder"),
                        backupFolder != null ? backupFolder.toString() : "")
                .setDefaultValue("./configcloudbackuper-backups")
                .setSaveConsumer(value -> config.setBackupFolder(Path.of(value)))
                .setTooltip(t(isChinese, "备份文件存储目录", "Directory for storing backups"))
                .build());

        // backupFilePrefix
        storage.addEntry(entryBuilder.startStrField(
                        t(isChinese, "备份文件前缀", "Backup File Prefix"),
                        config.getBackupFilePrefix() != null ? config.getBackupFilePrefix() : "")
                .setDefaultValue("backup")
                .setSaveConsumer(config::setBackupFilePrefix)
                .setTooltip(t(isChinese, "备份文件名称前缀", "Prefix for backup file names"))
                .build());

        // backupFileSuffix
        storage.addEntry(entryBuilder.startStrField(
                        t(isChinese, "备份文件后缀", "Backup File Suffix"),
                        config.getBackupFileSuffix() != null ? config.getBackupFileSuffix() : "")
                .setDefaultValue(".zip")
                .setSaveConsumer(config::setBackupFileSuffix)
                .setTooltip(t(isChinese, "备份文件名称后缀", "Suffix for backup file names"))
                .build());

        // maxBackups
        storage.addEntry(entryBuilder.startIntField(
                        t(isChinese, "最大备份数", "Max Backups"),
                        config.getMaxBackups())
                .setDefaultValue(10)
                .setMin(-1)
                .setSaveConsumer(config::setMaxBackups)
                .setTooltip(t(isChinese, "最大保留备份数量（-1 表示不限制）",
                        "Maximum number of backups to keep (-1 = unlimited)"))
                .build());

        // ===== WebDAV Cloud Backup Settings =====
        ConfigCategory webdavCategory = builder.getOrCreateCategory(
                t(isChinese, "WebDAV 云备份", "WebDAV Cloud Backup"));

        // webdavEnabled
        webdavCategory.addEntry(entryBuilder.startBooleanToggle(
                        t(isChinese, "启用 WebDAV 上传", "Enable WebDAV Upload"),
                        webDavConfig.isEnabled())
                .setDefaultValue(false)
                .setSaveConsumer(webDavConfig::setEnabled)
                .setTooltip(t(isChinese, "备份完成后自动上传到 WebDAV 服务器",
                        "Upload backups to WebDAV server after creation"))
                .build());

        // serverUrl
        webdavCategory.addEntry(entryBuilder.startStrField(
                        t(isChinese, "服务器地址", "Server URL"),
                        webDavConfig.getServerUrl())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setServerUrl)
                .setTooltip(t(isChinese, "WebDAV 服务器地址（例如：https://example.com/remote.php/dav/files/user/）",
                        "WebDAV server URL (e.g., https://example.com/remote.php/dav/files/user/)"))
                .build());

        // username
        webdavCategory.addEntry(entryBuilder.startStrField(
                        t(isChinese, "用户名", "Username"),
                        webDavConfig.getUsername())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setUsername)
                .setTooltip(t(isChinese, "WebDAV 账户用户名", "WebDAV account username"))
                .build());

        // password
        webdavCategory.addEntry(entryBuilder.startStrField(
                        t(isChinese, "密码", "Password"),
                        webDavConfig.getPassword())
                .setDefaultValue("")
                .setSaveConsumer(webDavConfig::setPassword)
                .setTooltip(t(isChinese, "WebDAV 账户密码", "WebDAV account password"))
                .build());

        // remotePath
        webdavCategory.addEntry(entryBuilder.startStrField(
                        t(isChinese, "远程路径", "Remote Path"),
                        webDavConfig.getRemotePath())
                .setDefaultValue("/ConfigBackuper/")
                .setSaveConsumer(webDavConfig::setRemotePath)
                .setTooltip(t(isChinese, "WebDAV 服务器上的远程目录路径",
                        "Remote directory path on WebDAV server"))
                .build());

        // ===== Backup Management =====
        try {
            BackupManagementCategory.build(
                    builder,
                    entryBuilder,
                    isChinese,
                    config,
                    webDavConfig
            );
        } catch (Exception e) {
            FabricModInitializer.getLogger().error("Failed to build Backup Management category", e);
        }

        // ===== Save Handler =====
        builder.setSavingRunnable(() -> {
            // Save main configuration
            configManager.save(config);

            // Save WebDAV configuration
            mod.saveWebDavConfig(webDavConfig);

            // Reload backuper and limiter with new config (no automatic backup)
            try {
                mod.reloadConfig();
                FabricModInitializer.getLogger().info("Configuration saved and reloaded");
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("Failed to reload config after save", e);
            }
        });

        return builder.build();
    }

    /**
     * 上传最新的备份文件到 WebDAV
     */
    private static void uploadLatestBackup(Path backupFolder, ModConfig config, WebDavConfig webDavConfig) {
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
}
