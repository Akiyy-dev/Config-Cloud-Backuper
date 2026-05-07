package com.configcloudbackuper.core;

import com.configcloudbackuper.util.LoggerWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 配置备份执行器 - 负责将游戏配置、模组配置、着色器配置打包备份
 */
public class ConfigBackuper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final LoggerWrapper logger;
    private final ModConfig config;

    public ConfigBackuper(LoggerWrapper logger, ModConfig config) {
        this.logger = logger;
        this.config = config;
    }

    /**
     * 执行备份
     */
    public void performBackup() {
        try {
            final Path backupDir = resolveBackupDirectory();
            Files.createDirectories(backupDir);

            final String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            final String prefix = config.getBackupFilePrefix() != null ? config.getBackupFilePrefix() : "backup";
            final String suffix = config.getBackupFileSuffix() != null ? config.getBackupFileSuffix() : ".zip";
            final String backupFileName = prefix + "_" + timestamp + suffix;
            final Path backupFile = backupDir.resolve(backupFileName);

            logger.info("Starting backup to: " + backupFile);

            if (config.isCompressionEnabled()) {
                createCompressedBackup(backupFile);
            } else {
                createUncompressedBackup(backupFile);
            }

            logger.info("Backup completed: " + backupFile);
        } catch (Exception e) {
            logger.error("Backup failed", e);
        }
    }

    /**
     * 创建压缩备份
     */
    private void createCompressedBackup(Path backupFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(backupFile))) {
            // 备份游戏配置
            if (config.isIncludeGameConfigs()) {
                addDirectoryToZip(zos, getGameConfigDir(), "game-configs/");
            }
            // 备份模组配置
            if (config.isIncludeModConfigs()) {
                addDirectoryToZip(zos, getModConfigDir(), "mod-configs/");
            }
            // 备份着色器配置
            if (config.isIncludeShaderPackConfigs()) {
                addDirectoryToZip(zos, getShaderPackConfigDir(), "shader-configs/");
            }
            // 备份 schematics 配置
            if (config.isIncludeSchematics()) {
                addDirectoryToZip(zos, getSchematicsDir(), "schematics/");
            }
            // 备份 3d-skin 配置
            if (config.isInclude3dSkin()) {
                addDirectoryToZip(zos, get3dSkinDir(), "3d-skin/");
            }
            // 备份 syncmatics 配置
            if (config.isIncludeSyncmatics()) {
                addDirectoryToZip(zos, getSyncmaticsDir(), "syncmatics/");
            }
            // 备份 defaultconfigs 配置
            if (config.isIncludeDefaultConfigs()) {
                addDirectoryToZip(zos, getDefaultConfigsDir(), "defaultconfigs/");
            }
        }
    }

    /**
     * 创建未压缩备份（直接复制目录）
     */
    private void createUncompressedBackup(Path backupFile) throws IOException {
        Files.createDirectories(backupFile);

        if (config.isIncludeGameConfigs()) {
            copyDirectory(getGameConfigDir(), backupFile.resolve("game-configs"));
        }
        if (config.isIncludeModConfigs()) {
            copyDirectory(getModConfigDir(), backupFile.resolve("mod-configs"));
        }
        if (config.isIncludeShaderPackConfigs()) {
            copyDirectory(getShaderPackConfigDir(), backupFile.resolve("shader-configs"));
        }
        if (config.isIncludeSchematics()) {
            copyDirectory(getSchematicsDir(), backupFile.resolve("schematics"));
        }
        if (config.isInclude3dSkin()) {
            copyDirectory(get3dSkinDir(), backupFile.resolve("3d-skin"));
        }
        if (config.isIncludeSyncmatics()) {
            copyDirectory(getSyncmaticsDir(), backupFile.resolve("syncmatics"));
        }
        if (config.isIncludeDefaultConfigs()) {
            copyDirectory(getDefaultConfigsDir(), backupFile.resolve("defaultconfigs"));
        }
    }

    /**
     * 将目录添加到 ZIP 输出流
     */
    private void addDirectoryToZip(ZipOutputStream zos, Path sourceDir, String entryPrefix) throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            logger.warn("Directory does not exist, skipping: " + sourceDir);
            return;
        }

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final String entryName = entryPrefix + sourceDir.relativize(file).toString().replace("\\", "/");
                final ZipEntry zipEntry = new ZipEntry(entryName);
                zos.putNextEntry(zipEntry);
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                final String entryName = entryPrefix + sourceDir.relativize(dir).toString().replace("\\", "/");
                if (!entryName.endsWith("/")) {
                    final ZipEntry zipEntry = new ZipEntry(entryName + "/");
                    zos.putNextEntry(zipEntry);
                    zos.closeEntry();
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 复制目录
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            logger.warn("Directory does not exist, skipping: " + source);
            return;
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 解析备份目录路径
     */
    private Path resolveBackupDirectory() {
        Path folder = config.getBackupFolder();
        if (folder == null) {
            folder = Path.of("./configcloudbackuper-backups");
        }
        if (!folder.isAbsolute()) {
            folder = Path.of(System.getProperty("user.dir")).resolve(folder).normalize();
        }
        return folder;
    }

    /**
     * 获取游戏配置目录
     */
    private Path getGameConfigDir() {
        return Path.of(System.getProperty("user.dir"), ".minecraft").normalize();
    }

    /**
     * 获取模组配置目录
     */
    private Path getModConfigDir() {
        return Path.of(System.getProperty("user.dir"), "config").normalize();
    }

    /**
     * 获取着色器配置目录
     */
    private Path getShaderPackConfigDir() {
        return Path.of(System.getProperty("user.dir"), "shaderpacks").normalize();
    }

    /**
     * 获取 schematics 目录
     */
    private Path getSchematicsDir() {
        return Path.of(System.getProperty("user.dir"), "schematics").normalize();
    }

    /**
     * 获取 3d-skin 目录
     */
    private Path get3dSkinDir() {
        return Path.of(System.getProperty("user.dir"), "3d-skin").normalize();
    }

    /**
     * 获取 syncmatics 目录
     */
    private Path getSyncmaticsDir() {
        return Path.of(System.getProperty("user.dir"), "syncmatics").normalize();
    }

    /**
     * 获取 defaultconfigs 目录
     */
    private Path getDefaultConfigsDir() {
        return Path.of(System.getProperty("user.dir"), "defaultconfigs").normalize();
    }
}
