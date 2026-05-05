package com.naocraftlab.configbackuper.core;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.config.BackupFileManager;
import com.naocraftlab.configbackuper.config.model.BackupFileInfo;
import com.naocraftlab.configbackuper.util.BackupPaths;
import com.naocraftlab.configbackuper.webdav.WebDavConfig;
import com.naocraftlab.configbackuper.webdav.WebDavUploader;

import java.nio.file.Path;
import java.util.List;

/**
 * 统一执行「本地备份 + 清理旧文件 +（若启用 WebDAV）上传最新备份」
 */
public final class BackupCoordinator {

    private BackupCoordinator() {
    }

    public static void runLocalBackupWithCleanup(FabricModInitializer mod) {
        mod.getConfigBackuper().performBackup();
        mod.getBackupLimiter().removeOldBackups();
    }

    /**
     * 在本地备份与清理完成后，若 WebDAV 已启用则上传当前目录下最新的匹配备份文件。
     */
    public static void runLocalBackupCleanupAndWebDavIfEnabled(FabricModInitializer mod) {
        runLocalBackupWithCleanup(mod);
        WebDavConfig webDav = mod.loadWebDavConfig();
        if (!webDav.isEnabled()) {
            return;
        }
        ModConfig cfg = mod.getModConfigurationManager().read();
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
