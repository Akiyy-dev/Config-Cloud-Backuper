package com.configcloudbackuper.config.model;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public class BackupFileInfo {
    private final Path path;
    private final String fileName;
    private final long size;
    private final FileTime lastModifiedTime;

    public BackupFileInfo(Path path, String fileName, long size, FileTime lastModifiedTime) {
        this.path = path;
        this.fileName = fileName;
        this.size = size;
        this.lastModifiedTime = lastModifiedTime;
    }

    public Path getPath() { return path; }
    public String getFileName() { return fileName; }
    public long getSize() { return size; }
    public FileTime getLastModifiedTime() { return lastModifiedTime; }

    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
}
