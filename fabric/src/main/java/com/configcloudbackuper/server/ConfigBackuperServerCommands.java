package com.configcloudbackuper.server;

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
import com.configcloudbackuper.util.BackupPaths;
import com.configcloudbackuper.webdav.WebDavConfig;
import com.configcloudbackuper.webdav.WebDavDownloader;
import com.configcloudbackuper.webdav.WebDavUploader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 服务端命令：/config_backuper backup|list|config|cloud ...
 */
public final class ConfigBackuperServerCommands {

    private ConfigBackuperServerCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        registerForPrefix(dispatcher, "config_backuper");
        registerForPrefix(dispatcher, "server_config_backuper");
    }

    private static void registerForPrefix(CommandDispatcher<ServerCommandSource> dispatcher, String prefix) {
        dispatcher.register(CommandManager.literal(prefix)
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ConfigBackuperServerCommands::usageRoot)
                .then(CommandManager.literal("backup")
                        .executes(ConfigBackuperServerCommands::backup))
                .then(CommandManager.literal("list")
                        .executes(ConfigBackuperServerCommands::listLocal))
                .then(CommandManager.literal("config")
                        .executes(ctx -> sendLines(ctx, List.of(
                                "用法:",
                                "  /" + prefix + " config reload — 从磁盘重新加载配置",
                                "  /" + prefix + " config show — 显示当前主配置",
                                "  /" + prefix + " config set <键> <值> — 修改并保存（键见 show）",
                                "  /" + prefix + " config help — 键名说明"
                        )))
                        .then(CommandManager.literal("reload")
                                .executes(ConfigBackuperServerCommands::configReload))
                        .then(CommandManager.literal("show")
                                .executes(ConfigBackuperServerCommands::configShow))
                        .then(CommandManager.literal("help")
                                .executes(ConfigBackuperServerCommands::configHelp))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("key", StringArgumentType.string())
                                        .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(ConfigBackuperServerCommands::configSet)))))
                .then(CommandManager.literal("remote")
                        .executes(ctx -> sendLines(ctx, List.of(
                                "用法:",
                                "  /" + prefix + " remote cloud ... — WebDAV 远端操作",
                                "  /" + prefix + " remote server ... — 客户端上传到服务端管理"
                        )))
                        .then(CommandManager.literal("cloud")
                                .executes(ctx -> sendLines(ctx, List.of(
                                        "用法:",
                                        "  /" + prefix + " remote cloud status — WebDAV 状态（密码不显示）",
                                        "  /" + prefix + " remote cloud list — 列出远程目录文件",
                                        "  /" + prefix + " remote cloud upload [文件名] — 上传本地备份（默认最新）",
                                        "  /" + prefix + " remote cloud download [文件名] — 下载到本地备份目录（默认最新）",
                                        "  /" + prefix + " remote cloud set <字段> <值> — 字段: enabled, serverUrl, username, password, remotePath"
                                )))
                                .then(CommandManager.literal("status")
                                        .executes(ConfigBackuperServerCommands::cloudStatus))
                                .then(CommandManager.literal("list")
                                        .executes(ConfigBackuperServerCommands::cloudList))
                                .then(CommandManager.literal("upload")
                                        .executes(ctx -> cloudUpload(ctx, null))
                                        .then(CommandManager.argument("file", StringArgumentType.greedyString())
                                                .executes(ctx -> cloudUpload(ctx, StringArgumentType.getString(ctx, "file")))))
                                .then(CommandManager.literal("download")
                                        .executes(ctx -> cloudDownload(ctx, null))
                                        .then(CommandManager.argument("file", StringArgumentType.greedyString())
                                                .executes(ctx -> cloudDownload(ctx, StringArgumentType.getString(ctx, "file")))))
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("field", StringArgumentType.string())
                                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                                        .executes(ConfigBackuperServerCommands::cloudSet)))))
                        .then(CommandManager.literal("server")
                                .executes(ctx -> sendLines(ctx, List.of(
                                        "用法:",
                                        "  /" + prefix + " remote server status — 查看客户端上传到服务端配置",
                                        "  /" + prefix + " remote server list — 查看你自己的上传备份（需 OP 等级 > 2）",
                                        "  /" + prefix + " remote server set <字段> <值> — 字段: enabled, folder, maxPerPlayer, maxFileSizeMb"
                                )))
                                .then(CommandManager.literal("status")
                                        .executes(ConfigBackuperServerCommands::serverStatus))
                                .then(CommandManager.literal("list")
                                        .requires(source -> source.hasPermissionLevel(3))
                                        .executes(ConfigBackuperServerCommands::serverList))
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("field", StringArgumentType.word())
                                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                                        .executes(ConfigBackuperServerCommands::serverSet))))))
                );
    }

    private static int usageRoot(CommandContext<ServerCommandSource> ctx) {
        return sendLines(ctx, List.of(
                "Config Cloud Backuper 命令:",
                "  backup — 执行本地备份、清理旧文件；若已启用 WebDAV 则上传最新备份",
                "  list — 列出本地备份目录中的备份文件",
                "  config … — 查看/修改 config-cloud-backuper-server.json（含上传大小上限 clientUploadMaxFileSizeMb）",
                "  remote cloud … — WebDAV 操作（推荐）",
                "  remote server … — 客户端上传到服务端管理（推荐）"
        ));
    }

    private static int backup(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        sendFeedback(source, () -> Text.literal("正在后台执行备份..."));
        CompletableFuture.runAsync(() -> {
            try {
                FabricModInitializer mod = FabricModInitializer.getInstance();
                BackupCoordinator.runLocalBackupCleanupAndWebDavIfEnabled(mod, BackupCoordinator.ConfigProfile.SERVER);
                source.getServer().execute(() -> sendFeedback(source, () -> Text.literal("备份流程已执行（详见服务端日志）。")));
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("服务端命令备份失败", e);
                source.getServer().execute(() -> source.sendError(Text.literal("备份失败: " + e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int listLocal(CommandContext<ServerCommandSource> ctx) {
        FabricModInitializer mod = FabricModInitializer.getInstance();
        ModConfig cfg = mod.getServerModConfigurationManager().read();
        Path dir = BackupPaths.resolveBackupDirectory(cfg);
        String prefix = cfg.getBackupFilePrefix() != null ? cfg.getBackupFilePrefix() : "backup";
        String suffix = cfg.getBackupFileSuffix() != null ? cfg.getBackupFileSuffix() : ".zip";
        List<BackupFileInfo> files = new BackupFileManager().listBackupFiles(dir, prefix, suffix);
        if (files.isEmpty()) {
            sendFeedback(ctx.getSource(), () -> Text.literal("未找到备份文件。目录: " + dir));
            return Command.SINGLE_SUCCESS;
        }
        sendFeedback(ctx.getSource(), () -> Text.literal("备份目录: " + dir + "（共 " + files.size() + " 个）"));
        int n = Math.min(files.size(), 30);
        for (int i = 0; i < n; i++) {
            BackupFileInfo f = files.get(i);
            sendFeedback(ctx.getSource(), () -> Text.literal(String.format(Locale.ROOT, "  %s  (%s)", f.getFileName(), f.getFormattedSize())));
        }
        if (files.size() > n) {
            sendFeedback(ctx.getSource(), () -> Text.literal("... 其余 " + (files.size() - n) + " 个已省略"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int configReload(CommandContext<ServerCommandSource> ctx) {
        FabricModInitializer.getInstance().reloadServerConfig();
        sendFeedback(ctx.getSource(), () -> Text.literal("已从磁盘重新加载服务端主配置（config-cloud-backuper-server.json）并刷新备份器。"));
        return Command.SINGLE_SUCCESS;
    }

    private static int configShow(CommandContext<ServerCommandSource> ctx) {
        ModConfig c = FabricModInitializer.getInstance().getServerModConfigurationManager().read();
        return sendLines(ctx, List.of(
                "（文件: config-cloud-backuper-server.json）",
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
                "clientUploadMaxBackupsPerPlayer = " + c.getClientUploadMaxBackupsPerPlayer(),
                "clientUploadMaxFileSizeMb = " + c.getClientUploadMaxFileSizeMb()
        ));
    }

    private static int configHelp(CommandContext<ServerCommandSource> ctx) {
        return sendLines(ctx, List.of(
                "布尔键取值: true / false（或 1 / 0, on / off）",
                "maxBackups: 整数，-1 表示不限制数量",
                "backupFolder: 路径字符串（相对路径相对于服务端运行目录）",
                "backupFilePrefix / backupFileSuffix: 字符串",
                "clientUploadToServerEnabled: 是否允许客户端上传到服务端",
                "clientUploadFolder: 服务端存放客户端上传文件的根目录",
                "clientUploadMaxBackupsPerPlayer: 每位玩家最大保留数（-1 不限制）",
                "clientUploadMaxFileSizeMb: 单个上传 zip 最大 MiB（有效范围 1～4096，超出会被夹紧）"
        ));
    }

    private static int configSet(CommandContext<ServerCommandSource> ctx) {
        String key = StringArgumentType.getString(ctx, "key").trim();
        String value = StringArgumentType.getString(ctx, "value");
        FabricModInitializer mod = FabricModInitializer.getInstance();
        ModConfigurationManager mgr = mod.getServerModConfigurationManager();
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
        mod.reloadServerConfig();
        sendFeedback(ctx.getSource(), () -> Text.literal("已保存并重新加载: " + key));
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
            case "clientUploadMaxFileSizeMb" -> c.setClientUploadMaxFileSizeMb(Integer.parseInt(value.trim()));
            default -> throw new IllegalArgumentException("未知配置键: " + key + "（使用 /config_backuper config show 查看键名）");
        }
    }

    private static int cloudStatus(CommandContext<ServerCommandSource> ctx) {
        WebDavConfig w = FabricModInitializer.getInstance().loadWebDavConfig();
        return sendLines(ctx, List.of(
                "enabled = " + w.isEnabled(),
                "serverUrl = " + nullToEmpty(w.getServerUrl()),
                "username = " + nullToEmpty(w.getUsername()),
                "password = " + (w.getPassword() != null && !w.getPassword().isEmpty() ? "********" : ""),
                "remotePath = " + nullToEmpty(w.getRemotePath())
        ));
    }

    private static int cloudList(CommandContext<ServerCommandSource> ctx) {
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
            sendFeedback(ctx.getSource(), () -> Text.literal("远程目录无文件或 PROPFIND 失败（见日志）。"));
            return Command.SINGLE_SUCCESS;
        }
        sendFeedback(ctx.getSource(), () -> Text.literal("远程文件（最多显示 40 个）:"));
        int n = Math.min(names.size(), 40);
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            sendFeedback(ctx.getSource(), () -> Text.literal("  " + name));
        }
        if (names.size() > n) {
            sendFeedback(ctx.getSource(), () -> Text.literal("... 共 " + names.size() + " 个"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int cloudUpload(CommandContext<ServerCommandSource> ctx, String fileNameOrNull) {
        ServerCommandSource source = ctx.getSource();
        sendFeedback(source, () -> Text.literal("正在上传 WebDAV..."));
        CompletableFuture.runAsync(() -> {
            try {
                FabricModInitializer mod = FabricModInitializer.getInstance();
                WebDavConfig w = mod.loadWebDavConfig();
                ModConfig cfg = mod.getServerModConfigurationManager().read();
                Path file;
                if (fileNameOrNull != null && !fileNameOrNull.isBlank()) {
                    file = BackupPaths.resolveBackupDirectory(cfg).resolve(fileNameOrNull.trim());
                } else {
                    file = BackupCoordinator.findLatestBackupPath(cfg);
                }
                if (file == null || !java.nio.file.Files.isRegularFile(file)) {
                    source.getServer().execute(() -> source.sendError(Text.literal("未找到要上传的本地备份文件。")));
                    return;
                }
                String err = new WebDavUploader().uploadBackup(file, w, true);
                if (err != null) {
                    source.getServer().execute(() -> source.sendError(Text.literal(err)));
                } else {
                    source.getServer().execute(() -> sendFeedback(source, () -> Text.literal("上传完成: " + file.getFileName())));
                }
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("WebDAV 上传失败", e);
                source.getServer().execute(() -> source.sendError(Text.literal("上传失败: " + e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int cloudDownload(CommandContext<ServerCommandSource> ctx, String fileNameOrNull) {
        ServerCommandSource source = ctx.getSource();
        sendFeedback(source, () -> Text.literal("正在从 WebDAV 下载..."));
        CompletableFuture.runAsync(() -> {
            try {
                FabricModInitializer mod = FabricModInitializer.getInstance();
                ModConfig cfg = mod.getServerModConfigurationManager().read();
                Path dir = BackupPaths.resolveBackupDirectory(cfg);
                java.nio.file.Files.createDirectories(dir);
                WebDavConfig w = mod.loadWebDavConfig();
                String remote = fileNameOrNull != null && !fileNameOrNull.isBlank() ? fileNameOrNull.trim() : null;
                String err = new WebDavDownloader().downloadBackup(dir, w, remote, true);
                if (err != null) {
                    source.getServer().execute(() -> source.sendError(Text.literal(err)));
                } else {
                    source.getServer().execute(() -> sendFeedback(source, () -> Text.literal("下载完成，已保存到备份目录。")));
                }
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("WebDAV 下载失败", e);
                source.getServer().execute(() -> source.sendError(Text.literal("下载失败: " + e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int cloudSet(CommandContext<ServerCommandSource> ctx) {
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
        sendFeedback(ctx.getSource(), () -> Text.literal("已保存 WebDAV 配置: " + field));
        return Command.SINGLE_SUCCESS;
    }

    private static int serverStatus(CommandContext<ServerCommandSource> ctx) {
        ModConfig c = FabricModInitializer.getInstance().getServerModConfigurationManager().read();
        return sendLines(ctx, List.of(
                "enabled = " + c.isClientUploadToServerEnabled(),
                "folder = " + c.getClientUploadFolder(),
                "maxPerPlayer = " + c.getClientUploadMaxBackupsPerPlayer(),
                "maxFileSizeMb = " + c.getClientUploadMaxFileSizeMb()
        ));
    }

    private static int serverList(CommandContext<ServerCommandSource> ctx) {
        ModConfig c = FabricModInitializer.getInstance().getServerModConfigurationManager().read();
        String playerName = ctx.getSource().getName();
        Path dir = ClientUploadStorageManager.resolvePlayerDir(c, playerName);
        if (!java.nio.file.Files.isDirectory(dir)) {
            sendFeedback(ctx.getSource(), () -> Text.literal("未找到玩家上传目录: " + playerName));
            return Command.SINGLE_SUCCESS;
        }
        try (var files = java.nio.file.Files.list(dir)) {
            var sorted = files.filter(java.nio.file.Files::isRegularFile)
                    .sorted(java.util.Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .toList();
            sendFeedback(ctx.getSource(), () -> Text.literal("玩家 " + playerName + " 上传备份: " + sorted.size()));
            int n = Math.min(sorted.size(), 40);
            for (int i = 0; i < n; i++) {
                Path p = sorted.get(i);
                sendFeedback(ctx.getSource(), () -> Text.literal("  " + p.getFileName()));
            }
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("读取上传目录失败: " + e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int serverSet(CommandContext<ServerCommandSource> ctx) {
        String field = StringArgumentType.getString(ctx, "field").trim().toLowerCase(Locale.ROOT);
        String value = StringArgumentType.getString(ctx, "value");
        FabricModInitializer mod = FabricModInitializer.getInstance();
        ModConfigurationManager mgr = mod.getServerModConfigurationManager();
        ModConfig c = mgr.read();
        try {
            switch (field) {
                case "enabled" -> c.setClientUploadToServerEnabled(parseBool(value));
                case "folder" -> c.setClientUploadFolder(Path.of(value.trim()));
                case "maxperplayer" -> c.setClientUploadMaxBackupsPerPlayer(Integer.parseInt(value.trim()));
                case "maxfilesizemb" -> c.setClientUploadMaxFileSizeMb(Integer.parseInt(value.trim()));
                default -> {
                    ctx.getSource().sendError(Text.literal("未知字段: " + field + "（enabled / folder / maxPerPlayer / maxFileSizeMb）"));
                    return 0;
                }
            }
            mgr.save(c);
            mod.reloadServerConfig();
            sendFeedback(ctx.getSource(), () -> Text.literal("已保存服务端上传设置: " + field));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("保存失败: " + e.getMessage()));
            return 0;
        }
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

    private static int sendLines(CommandContext<ServerCommandSource> ctx, List<String> lines) {
        for (String line : lines) {
            sendFeedback(ctx.getSource(), () -> Text.literal(line));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void sendFeedback(ServerCommandSource source, Supplier<Text> message) {
        source.sendFeedback(message, false);
    }
}
