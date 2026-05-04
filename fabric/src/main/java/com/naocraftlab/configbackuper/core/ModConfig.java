package com.naocraftlab.configbackuper.core;

import java.nio.file.Path;

/**
 * 模组配置数据类
 */
public class ModConfig {

    private boolean includeGameConfigs = true;
    private boolean includeModConfigs = true;
    private boolean includeShaderPackConfigs = true;
    private int maxBackups = 10;
    private boolean compressionEnabled = true;
    private Path backupFolder = Path.of("./config-backuper-backups");
    private String backupFilePrefix = "backup";
    private String backupFileSuffix = ".zip";

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

    public Path getBackupFolder() {
        return backupFolder;
    }

    public void setBackupFolder(Path backupFolder) {
        this.backupFolder = backupFolder;
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
}
