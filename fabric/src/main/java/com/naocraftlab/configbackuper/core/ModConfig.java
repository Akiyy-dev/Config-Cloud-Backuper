package com.naocraftlab.configbackuper.core;

import java.nio.file.Path;

/**
 * 模组配置数据类
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
