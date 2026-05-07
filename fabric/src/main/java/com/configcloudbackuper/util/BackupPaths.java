package com.configcloudbackuper.util;

import com.configcloudbackuper.core.ModConfig;

import java.nio.file.Path;

/**
 * 解析备份目录路径（与 {@link com.configcloudbackuper.core.ConfigBackuper} 内逻辑一致）
 */
public final class BackupPaths {

    private BackupPaths() {
    }

    public static Path resolveBackupDirectory(ModConfig config) {
        Path folder = config.getBackupFolder();
        if (folder == null) {
            folder = Path.of("./configcloudbackuper-backups");
        }
        if (!folder.isAbsolute()) {
            folder = Path.of(System.getProperty("user.dir")).resolve(folder).normalize();
        }
        return folder;
    }
}
