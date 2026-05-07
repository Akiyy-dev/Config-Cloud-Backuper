package com.configcloudbackuper.webdav;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.util.HashUtils;

import java.io.File;
import java.nio.file.Path;

public class WebDavUploader {

    private final WebDavClient client;

    public WebDavUploader() {
        this.client = new WebDavClient();
    }

    /**
     * 上传备份文件到 WebDAV
     *
     * @param backupFile 本地备份文件路径
     * @param config     WebDAV 配置
     * @return 上传结果信息（成功返回 null，失败返回错误消息；未启用且非强制时返回 null 表示跳过）
     */
    public String uploadBackup(Path backupFile, WebDavConfig config) {
        return uploadBackup(backupFile, config, false);
    }

    /**
     * @param force 为 true 时忽略 {@link WebDavConfig#isEnabled()}，仍要求已填写 URL 与凭据
     */
    public String uploadBackup(Path backupFile, WebDavConfig config, boolean force) {
        if (!config.isEnabled() && !force) {
            return null;
        }

        if (config.getServerUrl() == null || config.getServerUrl().isEmpty()) {
            return "WebDAV server URL is not configured";
        }

        if (config.getUsername() == null || config.getUsername().isEmpty() ||
            config.getPassword() == null || config.getPassword().isEmpty()) {
            return "WebDAV credentials are not configured";
        }

        File file = backupFile.toFile();
        if (!file.exists() || !file.isFile()) {
            return "Backup file not found: " + backupFile;
        }

        String auth = WebDavClient.buildAuth(config.getUsername(), config.getPassword());
        String remoteUrl = WebDavClient.buildRemoteUrl(config.getServerUrl(), config.getRemotePath(), file.getName());

        FabricModInitializer.getLogger().info("Starting WebDAV upload to: " + remoteUrl);

        // 先创建远程目录
        String dirUrl = remoteUrl.substring(0, remoteUrl.lastIndexOf('/'));
        client.createDirectory(dirUrl, auth);

        // 上传文件
        boolean success = client.uploadFile(remoteUrl, file, auth);

        if (success) {
            try {
                String sha256 = HashUtils.sha256Hex(backupFile);
                String shaUrl = WebDavClient.buildRemoteUrl(config.getServerUrl(), config.getRemotePath(), file.getName() + ".sha256");
                boolean shaOk = client.uploadText(shaUrl, sha256, auth);
                if (!shaOk) {
                    String errorMsg = "WebDAV upload succeeded but sha256 upload failed: " + file.getName();
                    FabricModInitializer.getLogger().error(errorMsg);
                    return errorMsg;
                }
                FabricModInitializer.getLogger().info("WebDAV upload successful (with sha256): " + file.getName());
                return null;
            } catch (Exception e) {
                String errorMsg = "WebDAV upload succeeded but local sha256 generation failed: " + file.getName();
                FabricModInitializer.getLogger().error(errorMsg, e);
                return errorMsg;
            }
        } else {
            String errorMsg = "WebDAV upload failed: " + file.getName();
            FabricModInitializer.getLogger().error(errorMsg);
            return errorMsg;
        }
    }
}
