package com.configcloudbackuper.client;

import com.configcloudbackuper.server.ServerSyncNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * 在客户端主线程上发送上传包；若启用握手（协议主版本不低于 3），则先等 {@link ServerSyncNetworking.UploadSessionAckPayload} 再发分片。
 */
public final class ClientServerUploadSender {
    private static final int CHUNK = 32 * 1024;
    private static final int ACK_TIMEOUT_SEC = 30;

    private ClientServerUploadSender() {
    }

    /**
     * 必须在客户端主线程调用。
     */
    public static void send(
            MinecraftClient client,
            boolean useAckHandshake,
            String fileName,
            byte[] data,
            String sha256Hex,
            Runnable onChunksFullySent,
            Consumer<String> onError
    ) {
        try {
            if (useAckHandshake) {
                CompletableFuture<ServerSyncNetworking.UploadSessionAckPayload> ack = new CompletableFuture<>();
                ClientServerUploadSession.beginExpectingAck(ack);
                ClientPlayNetworking.send(new ServerSyncNetworking.UploadBeginPayload(fileName, data.length, sha256Hex));
                ack.orTimeout(ACK_TIMEOUT_SEC, TimeUnit.SECONDS).whenComplete((payload, err) -> {
                    client.execute(() -> {
                        try {
                            if (err != null) {
                                ClientServerUploadSession.clearPending();
                                String msg = err instanceof TimeoutException
                                        ? "服务端未响应上传握手（请确认客户端与服务端模组版本一致且为最新）"
                                        : String.valueOf(err.getCause() != null ? err.getCause().getMessage() : err.getMessage());
                                onError.accept(msg != null ? msg : "upload handshake failed");
                                return;
                            }
                            if (payload == null || !payload.ok()) {
                                String r = payload != null ? payload.reason() : "";
                                onError.accept(!r.isEmpty() ? localizeRejectReason(r) : "服务端拒绝了本次上传（详见聊天栏提示）");
                                return;
                            }
                            sendChunksAndEnd(data);
                            onChunksFullySent.run();
                        } catch (Exception e) {
                            onError.accept(e.getMessage() != null ? e.getMessage() : "upload failed");
                        }
                    });
                });
            } else {
                ClientPlayNetworking.send(new ServerSyncNetworking.UploadBeginPayload(fileName, data.length, sha256Hex));
                sendChunksAndEnd(data);
                onChunksFullySent.run();
            }
        } catch (Exception e) {
            ClientServerUploadSession.clearPending();
            onError.accept(e.getMessage() != null ? e.getMessage() : "upload failed");
        }
    }

    private static void sendChunksAndEnd(byte[] data) {
        for (int pos = 0; pos < data.length; pos += CHUNK) {
            int len = Math.min(CHUNK, data.length - pos);
            byte[] chunk = new byte[len];
            System.arraycopy(data, pos, chunk, 0, len);
            ClientPlayNetworking.send(new ServerSyncNetworking.UploadChunkPayload(chunk));
        }
        ClientPlayNetworking.send(new ServerSyncNetworking.UploadEndPayload());
    }

    private static String localizeRejectReason(String code) {
        if (code == null || code.isEmpty()) {
            return "服务端拒绝了本次上传";
        }
        return switch (code) {
            case "client_upload_disabled" -> "服务端未启用客户端上传（clientUploadToServerEnabled）";
            case "invalid_extension" -> "仅允许上传 .zip 备份";
            case "invalid_size" -> "文件大小不合法或超过服务端限制";
            case "invalid_sha256" -> "SHA-256 校验值无效";
            case "begin_io_error" -> "服务端创建上传会话失败（磁盘/权限等）";
            default -> code;
        };
    }
}
