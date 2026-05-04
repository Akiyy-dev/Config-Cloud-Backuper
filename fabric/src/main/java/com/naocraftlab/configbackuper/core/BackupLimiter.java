package com.naocraftlab.configbackuper.core;

import com.naocraftlab.configbackuper.util.LoggerWrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 备份限制器 - 负责清理超出最大保留数量的旧备份文件
 */
public class BackupLimiter {

    private final LoggerWrapper logger;
    private final ModConfig config;

    public BackupLimiter(LoggerWrapper logger, ModConfig config) {
        this.logger = logger;
        this.config = config;
    }

    /**
     * 删除超出 maxBackups 的旧备份文件
     */
    public void removeOldBackups() {
        final int maxBackups = config.getMaxBackups();
        if (maxBackups < 0) {
            logger.info("Backup limit is disabled (maxBackups = -1), skipping cleanup");
            return;
        }

        final Path backupDir = resolveBackupDirectory();
        if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
            return;
        }

        final String prefix = config.getBackupFilePrefix() != null ? config.getBackupFilePrefix() : "backup";
        final String suffix = config.getBackupFileSuffix() != null ? config.getBackupFileSuffix() : ".zip";

        try (Stream<Path> files = Files.list(backupDir)) {
            // 筛选出备份文件并按最后修改时间排序（最旧在前）
            final var backupFiles = files
                    .filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().startsWith(prefix))
                    .filter(f -> f.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparingLong(f -> {
                        try {
                            return Files.readAttributes(f, BasicFileAttributes.class).lastModifiedTime().toMillis();
                        } catch (IOException e) {
                            return 0;
                        }
                    }))
                    .toList();

            if (backupFiles.size() <= maxBackups) {
                logger.info("Backup count (" + backupFiles.size() + ") within limit (" + maxBackups + "), no cleanup needed");
                return;
            }

            // 删除超出限制的旧文件
            final int filesToDelete = backupFiles.size() - maxBackups;
            for (int i = 0; i < filesToDelete; i++) {
                final Path oldFile = backupFiles.get(i);
                try {
                    Files.delete(oldFile);
                    logger.info("Deleted old backup: " + oldFile.getFileName());
                } catch (IOException e) {
                    logger.error("Failed to delete old backup: " + oldFile, e);
                }
            }

            logger.info("Cleanup completed: deleted " + filesToDelete + " old backup(s)");
        } catch (IOException e) {
            logger.error("Failed to list backup directory: " + backupDir, e);
        }
    }

    /**
     * 解析备份目录路径
     */
    private Path resolveBackupDirectory() {
        Path folder = config.getBackupFolder();
        if (folder == null) {
            folder = Path.of("./config-backuper-backups");
        }
        if (!folder.isAbsolute()) {
            folder = Path.of(System.getProperty("user.dir")).resolve(folder).normalize();
        }
        return folder;
    }
}
