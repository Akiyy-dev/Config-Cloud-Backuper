package com.naocraftlab.configbackuper.webdav;

import com.naocraftlab.configbackuper.FabricModInitializer;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WebDAV 下载器 - 负责从 WebDAV 服务器列出并下载最新的备份文件
 */
public class WebDavDownloader {

    private final WebDavClient client;

    public WebDavDownloader() {
        this.client = new WebDavClient();
    }

    /**
     * 从 WebDAV 下载最新的备份文件到本地备份目录
     *
     * @param localBackupDir 本地备份目录
     * @param config         WebDAV 配置
     * @return 下载结果信息（成功返回 null，失败返回错误消息）
     */
    public String downloadLatestBackup(Path localBackupDir, WebDavConfig config) {
        if (!config.isEnabled()) {
            return "WebDAV is not enabled";
        }

        if (config.getServerUrl() == null || config.getServerUrl().isEmpty()) {
            return "WebDAV server URL is not configured";
        }

        if (config.getUsername() == null || config.getUsername().isEmpty() ||
                config.getPassword() == null || config.getPassword().isEmpty()) {
            return "WebDAV credentials are not configured";
        }

        String auth = WebDavClient.buildAuth(config.getUsername(), config.getPassword());
        String dirUrl = WebDavClient.buildRemoteDirUrl(config.getServerUrl(), config.getRemotePath());

        FabricModInitializer.getLogger().info("Listing WebDAV remote directory: " + dirUrl);

        // 列出远程文件
        List<String> remoteFiles = client.listFiles(dirUrl, auth);

        if (remoteFiles.isEmpty()) {
            return "No backup files found on WebDAV server";
        }

        // 按文件名排序（备份文件名包含时间戳，降序取最新的）
        // 备份文件名格式如: backup_2026-05-05_12-00-00.zip
        List<String> sortedFiles = remoteFiles.stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        String latestFileName = sortedFiles.get(0);
        FabricModInitializer.getLogger().info("Latest remote backup file: " + latestFileName);

        // 构建远程文件 URL 和本地目标路径
        String fileUrl = WebDavClient.buildRemoteUrl(config.getServerUrl(), config.getRemotePath(), latestFileName);
        Path targetPath = localBackupDir.resolve(latestFileName);

        // 下载文件
        FabricModInitializer.getLogger().info("Downloading from WebDAV: " + fileUrl);
        boolean success = client.downloadFile(fileUrl, auth, targetPath);

        if (success) {
            FabricModInitializer.getLogger().info("WebDAV download completed: " + targetPath);
            return null; // 成功
        } else {
            String errorMsg = "Failed to download backup from WebDAV: " + latestFileName;
            FabricModInitializer.getLogger().error(errorMsg);
            return errorMsg;
        }
    }
}
