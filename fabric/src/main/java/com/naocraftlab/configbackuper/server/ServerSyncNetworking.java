package com.naocraftlab.configbackuper.server;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.core.ModConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class ServerSyncNetworking {
    public static final Identifier CLIENT_UPLOAD_BEGIN = new Identifier("config-backuper", "client_upload_begin");
    public static final Identifier CLIENT_UPLOAD_CHUNK = new Identifier("config-backuper", "client_upload_chunk");
    public static final Identifier CLIENT_UPLOAD_END = new Identifier("config-backuper", "client_upload_end");
    public static final Identifier CLIENT_SERVER_ACTION = new Identifier("config-backuper", "client_server_action");

    private ServerSyncNetworking() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_UPLOAD_BEGIN, (server, player, handler, buf, responseSender) ->
                server.execute(() -> handleUploadBegin(player, buf)));
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_UPLOAD_CHUNK, (server, player, handler, buf, responseSender) ->
                server.execute(() -> handleUploadChunk(player, buf)));
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_UPLOAD_END, (server, player, handler, buf, responseSender) ->
                server.execute(() -> handleUploadEnd(player)));
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_SERVER_ACTION, (server, player, handler, buf, responseSender) ->
                server.execute(() -> handleServerAction(player, buf)));
    }

    private static void handleUploadBegin(ServerPlayerEntity player, PacketByteBuf buf) {
        ModConfig cfg = FabricModInitializer.getInstance().getModConfigurationManager().read();
        if (!cfg.isClientUploadToServerEnabled()) {
            player.sendMessage(Text.literal("[ConfigBackuper] 服务端未启用客户端上传功能"), false);
            return;
        }
        String fileName = buf.readString(256);
        long expectedSize = buf.readLong();
        try {
            ClientUploadStorageManager.begin(player.getUuid(), player.getGameProfile().getName(), fileName, expectedSize, cfg);
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to begin client upload", e);
            player.sendMessage(Text.literal("[ConfigBackuper] 上传初始化失败: " + e.getMessage()), false);
            ClientUploadStorageManager.closeSession(player.getUuid());
        }
    }

    private static void handleUploadChunk(ServerPlayerEntity player, PacketByteBuf buf) {
        int len = buf.readVarInt();
        byte[] data = buf.readByteArray(len);
        try {
            ClientUploadStorageManager.append(player.getUuid(), data);
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to append upload chunk", e);
            player.sendMessage(Text.literal("[ConfigBackuper] 上传分片失败: " + e.getMessage()), false);
            ClientUploadStorageManager.closeSession(player.getUuid());
        }
    }

    private static void handleUploadEnd(ServerPlayerEntity player) {
        ModConfig cfg = FabricModInitializer.getInstance().getModConfigurationManager().read();
        try {
            Path saved = ClientUploadStorageManager.finish(player.getUuid(), cfg);
            player.sendMessage(Text.literal("[ConfigBackuper] 上传成功: " + saved.getFileName()), false);
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to finish client upload", e);
            player.sendMessage(Text.literal("[ConfigBackuper] 上传完成失败: " + e.getMessage()), false);
            ClientUploadStorageManager.closeSession(player.getUuid());
        }
    }

    private static void handleServerAction(ServerPlayerEntity player, PacketByteBuf buf) {
        String action = buf.readString(64);
        ModConfig cfg = FabricModInitializer.getInstance().getModConfigurationManager().read();
        if ("status".equals(action)) {
            player.sendMessage(Text.literal("[ConfigBackuper][server] enabled=" + cfg.isClientUploadToServerEnabled()), false);
            player.sendMessage(Text.literal("[ConfigBackuper][server] folder=" + cfg.getClientUploadFolder()), false);
            player.sendMessage(Text.literal("[ConfigBackuper][server] maxPerPlayer=" + cfg.getClientUploadMaxBackupsPerPlayer()), false);
            return;
        }
        if ("list".equals(action)) {
            Path playerDir = ClientUploadStorageManager.resolvePlayerDir(cfg, player.getGameProfile().getName());
            if (!Files.isDirectory(playerDir)) {
                player.sendMessage(Text.literal("[ConfigBackuper][server] 该玩家无上传备份"), false);
                return;
            }
            try (var files = Files.list(playerDir)) {
                List<Path> sorted = files.filter(Files::isRegularFile)
                        .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                        .toList();
                player.sendMessage(Text.literal("[ConfigBackuper][server] " + player.getGameProfile().getName() + " 上传备份: " + sorted.size()), false);
                int n = Math.min(sorted.size(), 40);
                for (int i = 0; i < n; i++) {
                    Path p = sorted.get(i);
                    player.sendMessage(Text.literal("  " + p.getFileName()), false);
                }
            } catch (IOException e) {
                player.sendMessage(Text.literal("[ConfigBackuper][server] 列表读取失败: " + e.getMessage()), false);
            }
            return;
        }
        player.sendMessage(Text.literal("[ConfigBackuper][server] 未知动作: " + action), false);
    }

}
