package com.configcloudbackuper.config;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.config.model.BackupFileInfo;
import com.configcloudbackuper.config.widget.BackupFileListEntry;
import com.configcloudbackuper.config.widget.BackupNowButtonEntry;
import com.configcloudbackuper.config.widget.DownloadFromWebDavButtonEntry;
import com.configcloudbackuper.config.widget.ServerRemoteActionsEntry;
import com.configcloudbackuper.core.ModConfig;
import com.configcloudbackuper.util.BackupPaths;
import com.configcloudbackuper.webdav.WebDavConfig;
import com.configcloudbackuper.webdav.WebDavDownloader;
import com.configcloudbackuper.webdav.WebDavUploader;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * 备份管理分类 - 组装第 4 个分类的所有控件
 */
public class BackupManagementCategory {

    private static final int MAX_DISPLAY_FILES = 20;

    /**
     * 构建"备份管理"分类
     *
     * @param builder       ConfigBuilder 实例
     * @param entryBuilder  ConfigEntryBuilder 实例
     * @param isChinese     是否使用中文
     * @param config        ModConfig 实例（用于获取备份目录、前缀、后缀）
     * @param webDavConfig  WebDAV 配置
     * @return 构建好的 ConfigCategory
     */
    public static ConfigCategory build(
            ConfigBuilder builder,
            ConfigEntryBuilder entryBuilder,
            boolean isChinese,
            ModConfig config,
            WebDavConfig webDavConfig
    ) {
        // 创建分类
        ConfigCategory category = builder.getOrCreateCategory(
                Text.literal(isChinese ? "备份管理" : "Backup Management"));

        // 刷新回调 - 重新构建整个界面
        Runnable onRefresh = () -> {
            // 由于 Cloth Config 不支持动态刷新单个分类，
            // 这里通过重新设置父屏幕来刷新
            if (builder.getParentScreen() != null) {
                // 实际刷新由外部调用者处理
            }
        };

        // ===== 一键备份按钮 =====
        // 传入 parentScreen 以便备份完成后重建配置 Screen 刷新文件列表
        category.addEntry(new BackupNowButtonEntry(builder.getParentScreen()));

        // ===== 从 WebDAV 下载按钮 =====
        WebDavDownloader webDavDownloader = new WebDavDownloader();
        category.addEntry(new DownloadFromWebDavButtonEntry(
                webDavDownloader,
                webDavConfig,
                config,
                builder.getParentScreen()
        ));

        // ===== 服务端联动操作入口 =====
        category.addEntry(new ServerRemoteActionsEntry(isChinese));

        // ===== 备份文件列表 =====
        try {
            // 获取备份目录、前缀和后缀
            Path backupFolder = BackupPaths.resolveBackupDirectory(config);
            String prefix = config.getBackupFilePrefix() != null ? config.getBackupFilePrefix() : "backup";
            String suffix = config.getBackupFileSuffix() != null ? config.getBackupFileSuffix() : ".zip";

            // 扫描备份文件
            BackupFileManager fileManager = new BackupFileManager();
            List<BackupFileInfo> backupFiles = fileManager.listBackupFiles(backupFolder, prefix, suffix);

            // 限制显示数量
            if (backupFiles.size() > MAX_DISPLAY_FILES) {
                backupFiles = backupFiles.subList(0, MAX_DISPLAY_FILES);
            }

            // 添加文件列表控件
            WebDavUploader webDavUploader = new WebDavUploader();
            category.addEntry(new BackupFileListEntry(
                    backupFiles,
                    fileManager,
                    onRefresh,
                    webDavUploader,
                    webDavConfig,
                    builder.getParentScreen()
            ));
        } catch (InvalidPathException e) {
            FabricModInitializer.getLogger().error("Invalid backup directory path in config", e);
        } catch (Exception e) {
            FabricModInitializer.getLogger().error("Failed to load backup file list", e);
        }

        return category;
    }
}
