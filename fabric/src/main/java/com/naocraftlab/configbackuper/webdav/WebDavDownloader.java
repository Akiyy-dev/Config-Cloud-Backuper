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
        return downloadBackup(localBackupDir, config, null, false);
    }

    /**
     * @param remoteFileName 指定远程文件名；为 null 时下载按名称降序排序后的第一个文件
     * @param force          为 true 时忽略 {@link WebDavConfig#isEnabled()}
     */
    public String downloadBackup(Path localBackupDir, WebDavConfig config, String remoteFileName, boolean force) {
        if (!config.isEnabled() && !force) {
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

        List<String> remoteFiles = client.listFiles(dirUrl, auth);

        if (remoteFiles.isEmpty()) {
            return "No backup files found on WebDAV server";
        }

        String chosen;
        if (remoteFileName != null && !remoteFileName.isBlank()) {
            if (!remoteFiles.contains(remoteFileName)) {
                return "Remote file not found: " + remoteFileName;
            }
            chosen = remoteFileName;
        } else {
            List<String> sortedFiles = remoteFiles.stream()
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            chosen = sortedFiles.get(0);
        }

        FabricModInitializer.getLogger().info("WebDAV download target file: " + chosen);

        String fileUrl = WebDavClient.buildRemoteUrl(config.getServerUrl(), config.getRemotePath(), chosen);
        Path targetPath = localBackupDir.resolve(chosen);

        FabricModInitializer.getLogger().info("Downloading from WebDAV: " + fileUrl);
        boolean success = client.downloadFile(fileUrl, auth, targetPath);

        if (success) {
            FabricModInitializer.getLogger().info("WebDAV download completed: " + targetPath);
            return null;
        } else {
            String errorMsg = "Failed to download backup from WebDAV: " + chosen;
            FabricModInitializer.getLogger().error(errorMsg);
            return errorMsg;
        }
    }

    /**
     * 校验 WebDAV 远程操作所需字段（不检查 {@link WebDavConfig#isEnabled()}，供命令行显式操作使用）
     *
     * @return 错误消息；成功返回 null
     */
    public static String validateRemoteCredentials(WebDavConfig config) {
        if (config.getServerUrl() == null || config.getServerUrl().isEmpty()) {
            return "未配置 WebDAV 服务器地址";
        }
        if (config.getUsername() == null || config.getUsername().isEmpty()
                || config.getPassword() == null || config.getPassword().isEmpty()) {
            return "未配置 WebDAV 用户名或密码";
        }
        return null;
    }

    /**
     * 列出远程目录中的文件名（字典序降序，便于查看最新备份）；凭据无效时返回空列表
     */
    public List<String> listRemoteFileNames(WebDavConfig config) {
        if (validateRemoteCredentials(config) != null) {
            return List.of();
        }
        String auth = WebDavClient.buildAuth(config.getUsername(), config.getPassword());
        String dirUrl = WebDavClient.buildRemoteDirUrl(config.getServerUrl(), config.getRemotePath());
        List<String> remoteFiles = client.listFiles(dirUrl, auth);
        return remoteFiles.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }
}
