package com.configcloudbackuper.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.config.BackupFileManager;
import com.configcloudbackuper.config.model.BackupFileInfo;
import com.configcloudbackuper.core.BackupCoordinator;
import com.configcloudbackuper.core.ModConfig;
import com.configcloudbackuper.core.ModConfigurationManager;
import com.configcloudbackuper.server.ServerSyncNetworking;
import com.configcloudbackuper.util.HashUtils;
import com.configcloudbackuper.util.BackupPaths;
import com.configcloudbackuper.webdav.WebDavConfig;
import com.configcloudbackuper.webdav.WebDavDownloader;
import com.configcloudbackuper.webdav.WebDavUploader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端命令：/config_backuper backup|list|config|cloud ...
 */
public final class ConfigBackuperCommands {

    private ConfigBackuperCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(
                ClientCommandManager.literal("config_backuper")
                        .executes(ConfigBackuperCommands::usageRoot)
                        .then(ClientCommandManager.literal("backup")
                                .executes(ConfigBackuperCommands::backup))
                        .then(ClientCommandManager.literal("list")
                                .executes(ConfigBackuperCommands::listLocal))
                        .then(ClientCommandManager.literal("config")
                                .executes(ctx -> sendLines(ctx, List.of(
                                        "用法:",
                                        "  /config_backuper config reload — 从磁盘重新加载配置",
                                        "  /config_backuper config show — 显示当前主配置",
                                        "  /config_backuper config set <键> <值> — 修改并保存（键见 show）",
                                        "  /config_backuper config help — 键名说明"
                                )))
                                .then(ClientCommandManager.literal("reload")
                                        .executes(ConfigBackuperCommands::configReload))
                                .then(ClientCommandManager.literal("show")
                                        .executes(ConfigBackuperCommands::configShow))
                                .then(ClientCommandManager.literal("help")
                                        .executes(ConfigBackuperCommands::configHelp))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("key", StringArgumentType.string())
                                                .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                        .executes(ConfigBackuperCommands::configSet)))))
                        .then(ClientCommandManager.literal("remote")
                                .executes(ctx -> sendLines(ctx, List.of(
                                        "用法:",
                                        "  /config_backuper remote cloud ... — WebDAV 远端操作",
                                        "  /config_backuper remote server ... — 客户端上传到服务端操作"
                                )))
                                .then(ClientCommandManager.literal("cloud")
                                        .executes(ctx -> sendLines(ctx, List.of(
                                                "用法:",
                                                "  /config_backuper remote cloud status — WebDAV 状态（密码不显示）",
                                                "  /config_backuper remote cloud list — 列出远程目录文件",
                                                "  /config_backuper remote cloud upload [文件名] — 上传本地备份（默认最新）",
                                                "  /config_backuper remote cloud download [文件名] — 下载到本地备份目录（默认最新）",
                                                "  /config_backuper remote cloud set <字段> <值> — 字段: enabled, serverUrl, username, password, remotePath"
                                        )))
                                        .then(ClientCommandManager.literal("status")
                                                .executes(ConfigBackuperCommands::cloudStatus))
                                        .then(ClientCommandManager.literal("list")
                                                .executes(ConfigBackuperCommands::cloudList))
                                        .then(ClientCommandManager.literal("upload")
                                                .executes(ctx -> cloudUpload(ctx, null))
                                                .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                                                        .executes(ctx -> cloudUpload(ctx, StringArgumentType.getString(ctx, "file")))))
                                        .then(ClientCommandManager.literal("download")
                                                .executes(ctx -> cloudDownload(ctx, null))
                                                .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                                                        .executes(ctx -> cloudDownload(ctx, StringArgumentType.getString(ctx, "file")))))
                                        .then(ClientCommandManager.literal("set")
                                                .then(ClientCommandManager.argument("field", StringArgumentType.string())
                                                        .then(ClientCommandManager.argument("value", StringArgumentType.greedyString())
                                                                .executes(ConfigBackuperCommands::cloudSet)))))
                                .then(ClientCommandManager.literal("server")
                                        .executes(ctx -> sendLines(ctx, List.of(
                                                "用法:",
                                                "  /config_backuper remote server status — 查询服务端客户端上传配置",
                                                "  /config_backuper remote server list — 查询当前玩家在服务端的上传备份列表",
                                                "  /config_backuper remote server upload [文件名] — 上传本地备份到服务端（默认最新）"
                                        )))
                                        .then(ClientCommandManager.literal("status")
                                                .executes(ConfigBackuperCommands::serverStatus))
                                        .then(ClientCommandManager.literal("list")
                                                .executes(ConfigBackuperCommands::serverList))
                                        .then(ClientCommandManager.literal("upload")
                                                .executes(ctx -> serverUpload(ctx, null))
                                                .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                                                        .executes(ctx -> serverUpload(ctx, StringArgumentType.getString(ctx, "file")))))))
        );
    }

    private static int usageRoot(CommandContext<FabricClientCommandSource> ctx) {
        return sendLines(ctx, List.of(
                "Config Cloud Backuper 命令:",
                "  backup — 执行本地备份、清理旧文件；若已启用 WebDAV 则上传最新备份",
                "  list — 列出本地备份目录中的备份文件",
                "  config … — 查看/修改 config-cloud-backuper.json",
                "  remote cloud … — WebDAV 操作（推荐）",
                "  remote server … — 上传本地备份到服务端（推荐）"
        ));
    }

    private static int backup(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        MinecraftClient client = source.getClient();
        source.sendFeedback(Text.literal("正在后台执行备份…"));
        CompletableFuture.runAsync(() -> {
            try {
                FabricModInitializer mod = FabricModInitializer.getInstance();
                BackupCoordinator.runLocalBackupCleanupAndWebDavIfEnabled(mod);
                client.execute(() -> source.sendFeedback(Text.literal("备份流程已执行（详见日志；若失败请查看 latest.log）。")));
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("命令备份失败", e);
                client.execute(() -> source.sendError(Text.literal("备份失败: " + e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int listLocal(CommandContext<FabricClientCommandSource> ctx) {
        FabricModInitializer mod = FabricModInitializer.getInstance();
        ModConfig cfg = mod.getModConfigurationManager().read();
        Path dir = BackupPaths.resolveBackupDirectory(cfg);
        String prefix = cfg.getBackupFilePrefix() != null ? cfg.getBackupFilePrefix() : "backup";
        String suffix = cfg.getBackupFileSuffix() != null ? cfg.getBackupFileSuffix() : ".zip";
        List<BackupFileInfo> files = new BackupFileManager().listBackupFiles(dir, prefix, suffix);
        if (files.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("未找到备份文件。目录: " + dir));
            return Command.SINGLE_SUCCESS;
        }
        ctx.getSource().sendFeedback(Text.literal("备份目录: " + dir + "（共 " + files.size() + " 个）"));
        int n = Math.min(files.size(), 30);
        for (int i = 0; i < n; i++) {
            BackupFileInfo f = files.get(i);
            ctx.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT, "  %s  (%s)", f.getFileName(), f.getFormattedSize())));
        }
        if (files.size() > n) {
            ctx.getSource().sendFeedback(Text.literal("… 其余 " + (files.size() - n) + " 个已省略"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int configReload(CommandContext<FabricClientCommandSource> ctx) {
        FabricModInitializer.getInstance().reloadConfig();
        ctx.getSource().sendFeedback(Text.literal("已从磁盘重新加载主配置并刷新备份器。"));
        return Command.SINGLE_SUCCESS;
    }

    private static int configShow(CommandContext<FabricClientCommandSource> ctx) {
        ModConfig c = FabricModInitializer.getInstance().getModConfigurationManager().read();
        return sendLines(ctx, List.of(
                "includeGameConfigs = " + c.isIncludeGameConfigs(),
                "includeModConfigs = " + c.isIncludeModConfigs(),
                "includeShaderPackConfigs = " + c.isIncludeShaderPackConfigs(),
                "includeSchematics = " + c.isIncludeSchematics(),
                "include3dSkin = " + c.isInclude3dSkin(),
                "includeSyncmatics = " + c.isIncludeSyncmatics(),
                "includeDefaultConfigs = " + c.isIncludeDefaultConfigs(),
                "compressionEnabled = " + c.isCompressionEnabled(),
                "maxBackups = " + c.getMaxBackups(),
                "backupFolder = " + c.getBackupFolder(),
                "backupFilePrefix = " + c.getBackupFilePrefix(),
                "backupFileSuffix = " + c.getBackupFileSuffix(),
                "clientUploadToServerEnabled = " + c.isClientUploadToServerEnabled(),
                "clientUploadFolder = " + c.getClientUploadFolder(),
                "clientUploadMaxBackupsPerPlayer = " + c.getClientUploadMaxBackupsPerPlayer()
        ));
    }

    private static int configHelp(CommandContext<FabricClientCommandSource> ctx) {
        return sendLines(ctx, List.of(
                "布尔键取值: true / false（或 1 / 0, on / off）",
                "maxBackups: 整数，-1 表示不限制数量",
                "backupFolder: 路径字符串（相对路径相对于游戏运行目录）",
                "backupFilePrefix / backupFileSuffix: 字符串",
                "clientUploadToServerEnabled: 是否允许客户端上传到服务端",
                "clientUploadFolder: 服务端保存客户端上传文件的根目录",
                "clientUploadMaxBackupsPerPlayer: 每位玩家最大保留数（-1 不限制）"
        ));
    }

    private static int configSet(CommandContext<FabricClientCommandSource> ctx) {
        String key = StringArgumentType.getString(ctx, "key").trim();
        String value = StringArgumentType.getString(ctx, "value");
        FabricModInitializer mod = FabricModInitializer.getInstance();
        ModConfigurationManager mgr = mod.getModConfigurationManager();
        ModConfig c = mgr.read();
        try {
            applyConfigKey(c, key, value);
        } catch (NumberFormatException e) {
            ctx.getSource().sendError(Text.literal("数字无效: " + e.getMessage()));
            return 0;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal(e.getMessage()));
            return 0;
        }
        mgr.save(c);
        mod.reloadConfig();
        ctx.getSource().sendFeedback(Text.literal("已保存并重新加载: " + key));
        return Command.SINGLE_SUCCESS;
    }

    private static void applyConfigKey(ModConfig c, String key, String value) {
        switch (key) {
            case "includeGameConfigs" -> c.setIncludeGameConfigs(parseBool(value));
            case "includeModConfigs" -> c.setIncludeModConfigs(parseBool(value));
            case "includeShaderPackConfigs" -> c.setIncludeShaderPackConfigs(parseBool(value));
            case "includeSchematics" -> c.setIncludeSchematics(parseBool(value));
            case "include3dSkin" -> c.setInclude3dSkin(parseBool(value));
            case "includeSyncmatics" -> c.setIncludeSyncmatics(parseBool(value));
            case "includeDefaultConfigs" -> c.setIncludeDefaultConfigs(parseBool(value));
            case "compressionEnabled" -> c.setCompressionEnabled(parseBool(value));
            case "maxBackups" -> c.setMaxBackups(Integer.parseInt(value.trim()));
            case "backupFolder" -> c.setBackupFolder(Path.of(value.trim()));
            case "backupFilePrefix" -> c.setBackupFilePrefix(value);
            case "backupFileSuffix" -> c.setBackupFileSuffix(value);
            case "clientUploadToServerEnabled" -> c.setClientUploadToServerEnabled(parseBool(value));
            case "clientUploadFolder" -> c.setClientUploadFolder(Path.of(value.trim()));
            case "clientUploadMaxBackupsPerPlayer" -> c.setClientUploadMaxBackupsPerPlayer(Integer.parseInt(value.trim()));
            default -> throw new IllegalArgumentException("未知配置键: " + key + "（使用 /config_backuper config show 查看键名）");
        }
    }

    private static int cloudStatus(CommandContext<FabricClientCommandSource> ctx) {
        WebDavConfig w = FabricModInitializer.getInstance().loadWebDavConfig();
        return sendLines(ctx, List.of(
                "enabled = " + w.isEnabled(),
                "serverUrl = " + nullToEmpty(w.getServerUrl()),
                "username = " + nullToEmpty(w.getUsername()),
                "password = " + (w.getPassword() != null && !w.getPassword().isEmpty() ? "********" : ""),
                "remotePath = " + nullToEmpty(w.getRemotePath())
        ));
    }

    private static int cloudList(CommandContext<FabricClientCommandSource> ctx) {
        FabricModInitializer mod = FabricModInitializer.getInstance();
        WebDavConfig w = mod.loadWebDavConfig();
        String err = WebDavDownloader.validateRemoteCredentials(w);
        if (err != null) {
            ctx.getSource().sendError(Text.literal(err));
            return 0;
        }
        WebDavDownloader dl = new WebDavDownloader();
        List<String> names = dl.listRemoteFileNames(w);
        if (names.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("远程目录无文件或 PROPFIND 失败（见日志）。"));
            return Command.SINGLE_SUCCESS;
        }
        ctx.getSource().sendFeedback(Text.literal("远程文件（最多显示 40 个）:"));
        int n = Math.min(names.size(), 40);
        for (int i = 0; i < n; i++) {
            ctx.getSource().sendFeedback(Text.literal("  " + names.get(i)));
        }
        if (names.size() > n) {
            ctx.getSource().sendFeedback(Text.literal("… 共 " + names.size() + " 个"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int cloudUpload(CommandContext<FabricClientCommandSource> ctx, String fileNameOrNull) {
        FabricClientCommandSource source = ctx.getSource();
        MinecraftClient client = source.getClient();
        source.sendFeedback(Text.literal("正在上传 WebDAV…"));
        CompletableFuture.runAsync(() -> {
            try {
                FabricModInitializer mod = FabricModInitializer.getInstance();
                WebDavConfig w = mod.loadWebDavConfig();
                ModConfig cfg = mod.getModConfigurationManager().read();
                Path file;
                if (fileNameOrNull != null && !fileNameOrNull.isBlank()) {
                    file = BackupPaths.resolveBackupDirectory(cfg).resolve(fileNameOrNull.trim());
                } else {
                    file = BackupCoordinator.findLatestBackupPath(cfg);
                }
                if (file == null || !java.nio.file.Files.isRegularFile(file)) {
                    client.execute(() -> source.sendError(Text.literal("未找到要上传的本地备份文件。")));
                    return;
                }
                String err = new WebDavUploader().uploadBackup(file, w, true);
                if (err != null) {
                    client.execute(() -> source.sendError(Text.literal(err)));
                } else {
                    client.execute(() -> source.sendFeedback(Text.literal("上传完成: " + file.getFileName())));
                }
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("WebDAV 上传失败", e);
                client.execute(() -> source.sendError(Text.literal("上传失败: " + e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int cloudDownload(CommandContext<FabricClientCommandSource> ctx, String fileNameOrNull) {
        FabricClientCommandSource source = ctx.getSource();
        MinecraftClient client = source.getClient();
        source.sendFeedback(Text.literal("正在从 WebDAV 下载…"));
        CompletableFuture.runAsync(() -> {
            try {
                FabricModInitializer mod = FabricModInitializer.getInstance();
                ModConfig cfg = mod.getModConfigurationManager().read();
                Path dir = BackupPaths.resolveBackupDirectory(cfg);
                java.nio.file.Files.createDirectories(dir);
                WebDavConfig w = mod.loadWebDavConfig();
                String remote = fileNameOrNull != null && !fileNameOrNull.isBlank() ? fileNameOrNull.trim() : null;
                String err = new WebDavDownloader().downloadBackup(dir, w, remote, true);
                if (err != null) {
                    client.execute(() -> source.sendError(Text.literal(err)));
                } else {
                    client.execute(() -> source.sendFeedback(Text.literal("下载完成，已保存到备份目录。")));
                }
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("WebDAV 下载失败", e);
                client.execute(() -> source.sendError(Text.literal("下载失败: " + e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int cloudSet(CommandContext<FabricClientCommandSource> ctx) {
        String field = StringArgumentType.getString(ctx, "field").trim().toLowerCase(Locale.ROOT);
        String value = StringArgumentType.getString(ctx, "value");
        FabricModInitializer mod = FabricModInitializer.getInstance();
        WebDavConfig w = mod.loadWebDavConfig();
        switch (field) {
            case "enabled" -> w.setEnabled(parseBool(value));
            case "serverurl" -> w.setServerUrl(value.trim());
            case "username" -> w.setUsername(value);
            case "password" -> w.setPassword(value);
            case "remotepath" -> w.setRemotePath(value.trim());
            default -> {
                ctx.getSource().sendError(Text.literal("未知字段: " + field + "（enabled / serverUrl / username / password / remotePath）"));
                return 0;
            }
        }
        mod.saveWebDavConfig(w);
        ctx.getSource().sendFeedback(Text.literal("已保存 WebDAV 配置: " + field));
        return Command.SINGLE_SUCCESS;
    }

    private static int serverStatus(CommandContext<FabricClientCommandSource> ctx) {
        if (!ClientPlayNetworking.canSend(ServerSyncNetworking.ServerActionPayload.ID)) {
            ctx.getSource().sendError(Text.literal("当前未连接支持该功能的服务端。"));
            return 0;
        }
        sendServerAction("status");
        ctx.getSource().sendFeedback(Text.literal("已请求服务端上传配置状态（结果会由服务端返回聊天消息）。"));
        return Command.SINGLE_SUCCESS;
    }

    private static int serverList(CommandContext<FabricClientCommandSource> ctx) {
        if (!ClientPlayNetworking.canSend(ServerSyncNetworking.ServerActionPayload.ID)) {
            ctx.getSource().sendError(Text.literal("当前未连接支持该功能的服务端。"));
            return 0;
        }
        sendServerAction("list");
        ctx.getSource().sendFeedback(Text.literal("已请求服务端列出你的上传备份（结果会由服务端返回聊天消息）。"));
        return Command.SINGLE_SUCCESS;
    }

    private static int serverUpload(CommandContext<FabricClientCommandSource> ctx, String fileNameOrNull) {
        FabricClientCommandSource source = ctx.getSource();
        if (!ClientPlayNetworking.canSend(ServerSyncNetworking.UploadBeginPayload.ID)) {
            source.sendError(Text.literal("当前未连接支持该功能的服务端。"));
            return 0;
        }
        ModConfig cfg = FabricModInitializer.getInstance().getModConfigurationManager().read();
        Path file;
        if (fileNameOrNull != null && !fileNameOrNull.isBlank()) {
            file = BackupPaths.resolveBackupDirectory(cfg).resolve(fileNameOrNull.trim());
        } else {
            file = BackupCoordinator.findLatestBackupPath(cfg);
        }
        if (file == null || !Files.isRegularFile(file)) {
            source.sendError(Text.literal("未找到要上传到服务端的本地备份文件。"));
            return 0;
        }
        source.sendFeedback(Text.literal("正在上传到服务端: " + file.getFileName()));
        MinecraftClient client = source.getClient();
        CompletableFuture.runAsync(() -> {
            try {
                byte[] all = Files.readAllBytes(file);
                client.execute(() -> sendServerUploadPackets(source, file.getFileName().toString(), all));
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("Upload to server failed (read file)", e);
                client.execute(() -> source.sendError(Text.literal("上传到服务端失败: " + e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Must run on the client thread: Fabric networking requires ordered sends from the main executor.
     */
    private static void sendServerUploadPackets(FabricClientCommandSource source, String fileName, byte[] all) {
        try {
            ClientPlayNetworking.send(new ServerSyncNetworking.UploadBeginPayload(
                    fileName,
                    all.length,
                    HashUtils.sha256HexBytes(all)
            ));

            final int chunkSize = 32 * 1024;
            for (int pos = 0; pos < all.length; pos += chunkSize) {
                int len = Math.min(chunkSize, all.length - pos);
                byte[] chunk = new byte[len];
                System.arraycopy(all, pos, chunk, 0, len);
                ClientPlayNetworking.send(new ServerSyncNetworking.UploadChunkPayload(chunk));
            }

            ClientPlayNetworking.send(new ServerSyncNetworking.UploadEndPayload());
            source.sendFeedback(Text.literal("上传分片已发送，等待服务端确认。"));
        } catch (Exception e) {
            FabricModInitializer.getLogger().error("Upload to server failed", e);
            source.sendError(Text.literal("上传到服务端失败: " + e.getMessage()));
        }
    }

    private static void sendServerAction(String action) {
        ClientPlayNetworking.send(new ServerSyncNetworking.ServerActionPayload(action));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean parseBool(String value) {
        String s = value.trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on")) {
            return true;
        }
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("off")) {
            return false;
        }
        throw new IllegalArgumentException("无法解析布尔值: " + value);
    }

    private static int sendLines(CommandContext<FabricClientCommandSource> ctx, List<String> lines) {
        for (String line : lines) {
            ctx.getSource().sendFeedback(Text.literal(line));
        }
        return Command.SINGLE_SUCCESS;
    }
}
