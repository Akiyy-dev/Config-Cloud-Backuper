package com.naocraftlab.configbackuper.core;

import java.nio.file.Path;

/**
 * 模组配置数据类
 * <p>
 * 注意：backupFolder 以 String 形式存储而非 Path，以避免 Gson 在 Java 21+
 * 模块系统下无法通过反射序列化 sun.nio.fs.WindowsPath 导致的崩溃问题。
 * getBackupFolder() / setBackupFolder() 负责 String ↔ Path 的转换。
 */
public class ModConfig {

    private boolean includeGameConfigs = true;
    private boolean includeModConfigs = true;
    private boolean includeShaderPackConfigs = true;
    private boolean includeSchematics = true;
    private boolean include3dSkin = true;
    private boolean includeSyncmatics = true;
    private boolean includeDefaultConfigs = true;
    private int maxBackups = 10;
    private boolean compressionEnabled = true;
    private String backupFolder = "./config-backuper-backups";
    private String backupFilePrefix = "backup";
    private String backupFileSuffix = ".zip";
    private boolean clientUploadToServerEnabled = true;
    private String clientUploadFolder = "./config-backuper-backups/client-uploads";
    private int clientUploadMaxBackupsPerPlayer = 10;

    public boolean isIncludeGameConfigs() {
        return includeGameConfigs;
    }

    public void setIncludeGameConfigs(boolean includeGameConfigs) {
        this.includeGameConfigs = includeGameConfigs;
    }

    public boolean isIncludeModConfigs() {
        return includeModConfigs;
    }

    public void setIncludeModConfigs(boolean includeModConfigs) {
        this.includeModConfigs = includeModConfigs;
    }

    public boolean isIncludeShaderPackConfigs() {
        return includeShaderPackConfigs;
    }

    public void setIncludeShaderPackConfigs(boolean includeShaderPackConfigs) {
        this.includeShaderPackConfigs = includeShaderPackConfigs;
    }

    public boolean isIncludeSchematics() {
        return includeSchematics;
    }

    public void setIncludeSchematics(boolean includeSchematics) {
        this.includeSchematics = includeSchematics;
    }

    public boolean isInclude3dSkin() {
        return include3dSkin;
    }

    public void setInclude3dSkin(boolean include3dSkin) {
        this.include3dSkin = include3dSkin;
    }

    public boolean isIncludeSyncmatics() {
        return includeSyncmatics;
    }

    public void setIncludeSyncmatics(boolean includeSyncmatics) {
        this.includeSyncmatics = includeSyncmatics;
    }

    public boolean isIncludeDefaultConfigs() {
        return includeDefaultConfigs;
    }

    public void setIncludeDefaultConfigs(boolean includeDefaultConfigs) {
        this.includeDefaultConfigs = includeDefaultConfigs;
    }

    public int getMaxBackups() {
        return maxBackups;
    }

    public void setMaxBackups(int maxBackups) {
        this.maxBackups = maxBackups;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    /**
     * 获取备份文件夹路径（String → Path 转换）
     */
    public Path getBackupFolder() {
        return backupFolder != null ? Path.of(backupFolder) : Path.of("./config-backuper-backups");
    }

    /**
     * 设置备份文件夹路径（Path → String 转换）
     */
    public void setBackupFolder(Path backupFolder) {
        this.backupFolder = backupFolder != null ? backupFolder.toString() : "./config-backuper-backups";
    }

    public String getBackupFilePrefix() {
        return backupFilePrefix;
    }

    public void setBackupFilePrefix(String backupFilePrefix) {
        this.backupFilePrefix = backupFilePrefix;
    }

    public String getBackupFileSuffix() {
        return backupFileSuffix;
    }

    public void setBackupFileSuffix(String backupFileSuffix) {
        this.backupFileSuffix = backupFileSuffix;
    }

    public boolean isClientUploadToServerEnabled() {
        return clientUploadToServerEnabled;
    }

    public void setClientUploadToServerEnabled(boolean clientUploadToServerEnabled) {
        this.clientUploadToServerEnabled = clientUploadToServerEnabled;
    }

    public Path getClientUploadFolder() {
        return clientUploadFolder != null ? Path.of(clientUploadFolder) : Path.of("./config-backuper-backups/client-uploads");
    }

    public void setClientUploadFolder(Path clientUploadFolder) {
        this.clientUploadFolder = clientUploadFolder != null ? clientUploadFolder.toString() : "./config-backuper-backups/client-uploads";
    }

    public int getClientUploadMaxBackupsPerPlayer() {
        return clientUploadMaxBackupsPerPlayer;
    }

    public void setClientUploadMaxBackupsPerPlayer(int clientUploadMaxBackupsPerPlayer) {
        this.clientUploadMaxBackupsPerPlayer = clientUploadMaxBackupsPerPlayer;
    }
}
