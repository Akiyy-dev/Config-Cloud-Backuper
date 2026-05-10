package com.configcloudbackuper.server;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.core.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ServerSyncNetworking {
    public static final Identifier CLIENT_UPLOAD_BEGIN_ID = Identifier.of("config-cloud-backuper", "client_upload_begin");
    public static final Identifier CLIENT_UPLOAD_CHUNK_ID = Identifier.of("config-cloud-backuper", "client_upload_chunk");
    public static final Identifier CLIENT_UPLOAD_END_ID = Identifier.of("config-cloud-backuper", "client_upload_end");
    public static final Identifier CLIENT_SERVER_ACTION_ID = Identifier.of("config-cloud-backuper", "client_server_action");
    public static final Identifier SERVER_ACTION_RESULT_ID = Identifier.of("config-cloud-backuper", "server_action_result");
    public static final Identifier SERVER_CAPABILITY_ID = Identifier.of("config-cloud-backuper", "server_capability");
    public static final Identifier SERVER_UPLOAD_SESSION_ACK_ID = Identifier.of("config-cloud-backuper", "client_upload_session_ack");

    private static final ConcurrentHashMap<UUID, Long> NO_UPLOAD_SESSION_WARN_AT_NS = new ConcurrentHashMap<>();

    private ServerSyncNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(UploadBeginPayload.ID, UploadBeginPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadChunkPayload.ID, UploadChunkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadEndPayload.ID, UploadEndPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ServerActionPayload.ID, ServerActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerActionResultPayload.ID, ServerActionResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerCapabilityPayload.ID, ServerCapabilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UploadSessionAckPayload.ID, UploadSessionAckPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(UploadBeginPayload.ID, (payload, context) ->
                context.server().execute(() -> handleUploadBegin(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(UploadChunkPayload.ID, (payload, context) ->
                context.server().execute(() -> handleUploadChunk(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(UploadEndPayload.ID, (payload, context) ->
                context.server().execute(() -> handleUploadEnd(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ServerActionPayload.ID, (payload, context) ->
                context.server().execute(() -> handleServerAction(context.player(), payload)));
    }

    /**
     * 告知客户端服务端协议与支持能力。protocolVersion 约定：
     * - "1"：旧版，客户端视为允许上传（由 begin 再校验）
     * - "2u1" / "2u0"：协议 2；末尾 1=允许客户端上传到服务端，0=已关闭（避免客户端发分片却无会话）
     * - "3u1" / "3u0"：协议 3；含义同 2u*，且服务端在 begin 处理后会发送 {@link UploadSessionAckPayload}，客户端需收到 ok 后再发分片
     */
    public static void sendCapability(ServerPlayerEntity player, boolean clientUploadToServerEnabled) {
        String ver = clientUploadToServerEnabled ? "3u1" : "3u0";
        ServerPlayNetworking.send(player, new ServerCapabilityPayload(true, ver));
    }

    private static void sendUploadSessionAck(ServerPlayerEntity player, boolean ok, String reasonCode) {
        ServerPlayNetworking.send(player, new UploadSessionAckPayload(ok, reasonCode != null ? reasonCode : ""));
    }

    private static void handleUploadBegin(ServerPlayerEntity player, UploadBeginPayload payload) {
        ModConfig cfg = FabricModInitializer.getInstance().getServerModConfigurationManager().read();
        if (!cfg.isClientUploadToServerEnabled()) {
            audit(player, "upload_rejected", "client_upload_disabled");
            player.sendMessage(Text.literal("[ConfigCloudBackuper] 服务端未启用客户端上传功能"), false);
            sendUploadSessionAck(player, false, "client_upload_disabled");
            return;
        }
        String fileName = payload.fileName();
        long expectedSize = payload.expectedSize();
        String expectedSha256 = payload.expectedSha256();
        if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            audit(player, "upload_rejected", "invalid extension: " + fileName);
            player.sendMessage(Text.literal("[ConfigCloudBackuper] 仅允许上传 .zip 备份文件"), false);
            sendUploadSessionAck(player, false, "invalid_extension");
            return;
        }
        long maxUploadBytes = cfg.getClientUploadMaxFileSizeBytes();
        if (expectedSize <= 0 || expectedSize > maxUploadBytes) {
            audit(player, "upload_rejected", "size=" + expectedSize + ", limitBytes=" + maxUploadBytes);
            long limitMb = maxUploadBytes / (1024L * 1024L);
            player.sendMessage(Text.literal("[ConfigCloudBackuper] 文件大小不合法，或超过服务端限制 " + limitMb + " MiB（config-cloud-backuper-server.json → clientUploadMaxFileSizeMb）"), false);
            sendUploadSessionAck(player, false, "invalid_size");
            return;
        }
        if (expectedSha256 == null || !expectedSha256.matches("(?i)^[a-f0-9]{64}$")) {
            audit(player, "upload_rejected", "invalid sha256");
            player.sendMessage(Text.literal("[ConfigCloudBackuper] 缺少或错误的 SHA-256 校验值"), false);
            sendUploadSessionAck(player, false, "invalid_sha256");
            return;
        }
        try {
            ClientUploadStorageManager.begin(player.getUuid(), player.getGameProfile().getName(), fileName, expectedSize, expectedSha256, cfg);
            audit(player, "upload_begin", "file=" + fileName + ", size=" + expectedSize);
            sendUploadSessionAck(player, true, "");
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to begin client upload", e);
            player.sendMessage(Text.literal("[ConfigCloudBackuper] 上传初始化失败: " + e.getMessage()), false);
            ClientUploadStorageManager.closeSession(player.getUuid());
            audit(player, "upload_begin_failed", e.getMessage());
            sendUploadSessionAck(player, false, "begin_io_error");
        }
    }

    private static void handleUploadChunk(ServerPlayerEntity player, UploadChunkPayload payload) {
        byte[] data = payload.data();
        try {
            ClientUploadStorageManager.append(player.getUuid(), data);
        } catch (IOException e) {
            if ("No upload session started".equals(e.getMessage())) {
                long now = System.nanoTime();
                Long prev = NO_UPLOAD_SESSION_WARN_AT_NS.put(player.getUuid(), now);
                if (prev == null || now - prev > TimeUnit.SECONDS.toNanos(3)) {
                    FabricModInitializer.getLogger().warn(
                            "Upload chunk ignored for {} (no session). If the client is outdated, update it; otherwise check earlier "
                                    + "upload_rejected / upload_begin_failed audit lines.",
                            player.getGameProfile().getName());
                    player.sendMessage(Text.literal(
                            "[ConfigCloudBackuper] 上传分片被忽略：无有效会话。请更新客户端模组至与服务端相同版本，并查看是否曾提示仅允许 .zip 等错误。"),
                            false);
                    audit(player, "upload_chunk_failed", e.getMessage());
                }
                return;
            }
            FabricModInitializer.getLogger().error("Failed to append upload chunk", e);
            player.sendMessage(Text.literal("[ConfigCloudBackuper] 上传分片失败: " + e.getMessage()), false);
            ClientUploadStorageManager.closeSession(player.getUuid());
            audit(player, "upload_chunk_failed", e.getMessage());
        }
    }

    private static void handleUploadEnd(ServerPlayerEntity player) {
        ModConfig cfg = FabricModInitializer.getInstance().getServerModConfigurationManager().read();
        try {
            Path saved = ClientUploadStorageManager.finish(player.getUuid(), cfg);
            player.sendMessage(Text.literal("[ConfigCloudBackuper] 上传成功: " + saved.getFileName()), false);
            audit(player, "upload_success", "saved=" + saved);
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to finish client upload", e);
            player.sendMessage(Text.literal("[ConfigCloudBackuper] 上传完成失败: " + e.getMessage()), false);
            ClientUploadStorageManager.closeSession(player.getUuid());
            audit(player, "upload_finish_failed", e.getMessage());
        }
    }

    private static void handleServerAction(ServerPlayerEntity player, ServerActionPayload payload) {
        String action = payload.action();
        ModConfig cfg = FabricModInitializer.getInstance().getServerModConfigurationManager().read();
        if ("status".equals(action)) {
            List<String> lines = List.of(
                    "enabled=" + cfg.isClientUploadToServerEnabled(),
                    "folder=" + cfg.getClientUploadFolder(),
                    "maxPerPlayer=" + cfg.getClientUploadMaxBackupsPerPlayer(),
                    "maxFileSizeMb=" + cfg.getClientUploadMaxFileSizeMb()
            );
            ServerPlayNetworking.send(player, new ServerActionResultPayload(action, true, lines));
            player.sendMessage(Text.literal("[ConfigCloudBackuper][server] enabled=" + cfg.isClientUploadToServerEnabled()), false);
            player.sendMessage(Text.literal("[ConfigCloudBackuper][server] folder=" + cfg.getClientUploadFolder()), false);
            player.sendMessage(Text.literal("[ConfigCloudBackuper][server] maxPerPlayer=" + cfg.getClientUploadMaxBackupsPerPlayer()), false);
            player.sendMessage(Text.literal("[ConfigCloudBackuper][server] maxFileSizeMb=" + cfg.getClientUploadMaxFileSizeMb()), false);
            return;
        }
        if ("list".equals(action)) {
            if (!player.hasPermissionLevel(3)) {
                ServerPlayNetworking.send(player, new ServerActionResultPayload(
                        action,
                        false,
                        List.of("权限不足（需要 OP 等级 > 2）")
                ));
                player.sendMessage(Text.literal("[ConfigCloudBackuper][server] 权限不足（需要 OP 等级 > 2）"), false);
                audit(player, "server_list_denied", "permission");
                return;
            }
            Path playerDir = ClientUploadStorageManager.resolvePlayerDir(cfg, player.getGameProfile().getName());
            if (!Files.isDirectory(playerDir)) {
                ServerPlayNetworking.send(player, new ServerActionResultPayload(
                        action,
                        true,
                        List.of("该玩家无上传备份")
                ));
                player.sendMessage(Text.literal("[ConfigCloudBackuper][server] 该玩家无上传备份"), false);
                return;
            }
            try (var files = Files.list(playerDir)) {
                List<Path> sorted = files.filter(Files::isRegularFile)
                        .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                        .toList();
                List<String> lines = new ArrayList<>();
                lines.add(player.getGameProfile().getName() + " 上传备份: " + sorted.size());
                player.sendMessage(Text.literal("[ConfigCloudBackuper][server] " + player.getGameProfile().getName() + " 上传备份: " + sorted.size()), false);
                int n = Math.min(sorted.size(), 40);
                for (int i = 0; i < n; i++) {
                    Path p = sorted.get(i);
                    lines.add(p.getFileName().toString());
                    player.sendMessage(Text.literal("  " + p.getFileName()), false);
                }
                ServerPlayNetworking.send(player, new ServerActionResultPayload(action, true, lines));
            } catch (IOException e) {
                ServerPlayNetworking.send(player, new ServerActionResultPayload(
                        action,
                        false,
                        List.of("列表读取失败: " + e.getMessage())
                ));
                player.sendMessage(Text.literal("[ConfigCloudBackuper][server] 列表读取失败: " + e.getMessage()), false);
            }
            return;
        }
        ServerPlayNetworking.send(player, new ServerActionResultPayload(
                action,
                false,
                List.of("未知动作: " + action)
        ));
        player.sendMessage(Text.literal("[ConfigCloudBackuper][server] 未知动作: " + action), false);
    }

    private static void audit(ServerPlayerEntity player, String action, String detail) {
        FabricModInitializer.getLogger().info("[AUDIT] player=" + player.getGameProfile().getName()
                + ", uuid=" + player.getUuid() + ", action=" + action + ", detail=" + detail);
    }

    public record UploadBeginPayload(String fileName, long expectedSize, String expectedSha256) implements CustomPayload {
        public static final Id<UploadBeginPayload> ID = new Id<>(CLIENT_UPLOAD_BEGIN_ID);
        public static final PacketCodec<RegistryByteBuf, UploadBeginPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeString(value.fileName, 256);
                    buf.writeLong(value.expectedSize);
                    buf.writeString(value.expectedSha256, 128);
                },
                buf -> new UploadBeginPayload(buf.readString(256), buf.readLong(), buf.readString(128))
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadChunkPayload(byte[] data) implements CustomPayload {
        public static final Id<UploadChunkPayload> ID = new Id<>(CLIENT_UPLOAD_CHUNK_ID);
        public static final PacketCodec<RegistryByteBuf, UploadChunkPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeVarInt(value.data.length);
                    buf.writeByteArray(value.data);
                },
                buf -> {
                    int len = buf.readVarInt();
                    return new UploadChunkPayload(buf.readByteArray(len));
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadEndPayload() implements CustomPayload {
        public static final Id<UploadEndPayload> ID = new Id<>(CLIENT_UPLOAD_END_ID);
        public static final PacketCodec<RegistryByteBuf, UploadEndPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                },
                buf -> new UploadEndPayload()
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ServerActionPayload(String action) implements CustomPayload {
        public static final Id<ServerActionPayload> ID = new Id<>(CLIENT_SERVER_ACTION_ID);
        public static final PacketCodec<RegistryByteBuf, ServerActionPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeString(value.action, 64),
                buf -> new ServerActionPayload(buf.readString(64))
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ServerActionResultPayload(String action, boolean success, List<String> lines) implements CustomPayload {
        public static final Id<ServerActionResultPayload> ID = new Id<>(SERVER_ACTION_RESULT_ID);
        public static final PacketCodec<RegistryByteBuf, ServerActionResultPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeString(value.action, 64);
                    buf.writeBoolean(value.success);
                    buf.writeVarInt(value.lines.size());
                    for (String line : value.lines) {
                        buf.writeString(line, 1024);
                    }
                },
                buf -> {
                    String action = buf.readString(64);
                    boolean success = buf.readBoolean();
                    int size = buf.readVarInt();
                    List<String> lines = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        lines.add(buf.readString(1024));
                    }
                    return new ServerActionResultPayload(action, success, lines);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ServerCapabilityPayload(boolean supported, String protocolVersion) implements CustomPayload {
        public static final Id<ServerCapabilityPayload> ID = new Id<>(SERVER_CAPABILITY_ID);
        public static final PacketCodec<RegistryByteBuf, ServerCapabilityPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeBoolean(value.supported);
                    buf.writeString(value.protocolVersion, 24);
                },
                buf -> new ServerCapabilityPayload(buf.readBoolean(), buf.readString(24))
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * 服务端对 {@link UploadBeginPayload} 的响应；协议主版本不低于 3 时客户端必须先收到 ok 再发送分片。
     *
     * @param reason 失败时的机器可读原因（如 {@code client_upload_disabled}），成功时为空字符串
     */
    public record UploadSessionAckPayload(boolean ok, String reason) implements CustomPayload {
        public static final Id<UploadSessionAckPayload> ID = new Id<>(SERVER_UPLOAD_SESSION_ACK_ID);
        public static final PacketCodec<RegistryByteBuf, UploadSessionAckPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeBoolean(value.ok);
                    buf.writeString(value.reason, 256);
                },
                buf -> new UploadSessionAckPayload(buf.readBoolean(), buf.readString(256))
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
