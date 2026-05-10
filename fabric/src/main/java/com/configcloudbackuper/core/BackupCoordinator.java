package com.configcloudbackuper.core;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.config.BackupFileManager;
import com.configcloudbackuper.config.model.BackupFileInfo;
import com.configcloudbackuper.util.BackupPaths;
import com.configcloudbackuper.webdav.WebDavConfig;
import com.configcloudbackuper.webdav.WebDavUploader;

import java.nio.file.Path;
import java.util.List;

/**
 * 统一执行「本地备份 + 清理旧文件 +（若启用 WebDAV）上传最新备份」
 */
public final class BackupCoordinator {

    /** 使用哪一侧主配置（客户端文件 vs 服务端文件）驱动备份路径与策略 */
    public enum ConfigProfile {
        CLIENT,
        SERVER
    }

    private BackupCoordinator() {
    }

    public static void runLocalBackupWithCleanup(FabricModInitializer mod, ConfigProfile profile) {
        if (profile == ConfigProfile.SERVER) {
            mod.getServerConfigBackuper().performBackup();
            mod.getServerBackupLimiter().removeOldBackups();
        } else {
            mod.getClientConfigBackuper().performBackup();
            mod.getClientBackupLimiter().removeOldBackups();
        }
    }

    /**
     * 在本地备份与清理完成后，若 WebDAV 已启用则上传当前目录下最新的匹配备份文件。
     */
    public static void runLocalBackupCleanupAndWebDavIfEnabled(FabricModInitializer mod, ConfigProfile profile) {
        runLocalBackupWithCleanup(mod, profile);
        WebDavConfig webDav = mod.loadWebDavConfig();
        if (!webDav.isEnabled()) {
            return;
        }
        ModConfig cfg = profile == ConfigProfile.SERVER
                ? mod.getServerModConfigurationManager().read()
                : mod.getClientModConfigurationManager().read();
        Path latest = findLatestBackupPath(cfg);
        if (latest == null) {
            FabricModInitializer.getLogger().warn("WebDAV 已启用但未找到可上传的备份文件");
            return;
        }
        String err = new WebDavUploader().uploadBackup(latest, webDav);
        if (err != null) {
            FabricModInitializer.getLogger().error(err);
        }
    }

    public static Path findLatestBackupPath(ModConfig config) {
        Path dir = BackupPaths.resolveBackupDirectory(config);
        String prefix = config.getBackupFilePrefix() != null ? config.getBackupFilePrefix() : "backup";
        String suffix = config.getBackupFileSuffix() != null ? config.getBackupFileSuffix() : ".zip";
        List<BackupFileInfo> list = new BackupFileManager().listBackupFiles(dir, prefix, suffix);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0).getPath();
    }
}
