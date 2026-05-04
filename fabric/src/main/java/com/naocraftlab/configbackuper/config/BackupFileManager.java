package com.naocraftlab.configbackuper.config;

import com.naocraftlab.configbackuper.config.model.BackupFileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 备份文件管理器 - 负责列出、删除和重命名备份文件
 */
public class BackupFileManager {

    /**
     * 扫描备份目录，按 prefix + suffix 过滤文件，按 lastModified 降序返回
     */
    public List<BackupFileInfo> listBackupFiles(Path backupDir, String prefix, String suffix) {
        if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(backupDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(f -> {
                        String name = f.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix);
                    })
                    .map(this::toBackupFileInfo)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparingLong(
                                    (BackupFileInfo info) -> info.getLastModifiedTime().toMillis())
                            .reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * 删除备份文件
     * @return true 如果删除成功，false 如果失败
     */
    public boolean deleteBackupFile(Path filePath) {
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 重命名备份文件
     * @return true 如果重命名成功，false 如果失败
     */
    public boolean renameBackupFile(Path sourcePath, String newName) {
        try {
            Path targetPath = sourcePath.resolveSibling(newName);
            Files.move(sourcePath, targetPath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 将 Path 转换为 BackupFileInfo
     */
    private BackupFileInfo toBackupFileInfo(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            String fileName = path.getFileName().toString();
            long size = attrs.size();
            FileTime lastModifiedTime = attrs.lastModifiedTime();
            return new BackupFileInfo(path, fileName, size, lastModifiedTime);
        } catch (IOException e) {
            return null;
        }
    }
}
