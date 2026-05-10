package com.configcloudbackuper.client;

import com.configcloudbackuper.server.ServerSyncNetworking;

import java.util.concurrent.CompletableFuture;

/**
 * 等待服务端对 {@link ServerSyncNetworking.UploadBeginPayload} 的 ACK，再发送分片（协议主版本不低于 3）。
 */
public final class ClientServerUploadSession {
    private static CompletableFuture<ServerSyncNetworking.UploadSessionAckPayload> pending;

    private ClientServerUploadSession() {
    }

    public static synchronized void beginExpectingAck(CompletableFuture<ServerSyncNetworking.UploadSessionAckPayload> future) {
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
        }
        pending = future;
    }

    public static synchronized void acceptAck(ServerSyncNetworking.UploadSessionAckPayload payload) {
        CompletableFuture<ServerSyncNetworking.UploadSessionAckPayload> f = pending;
        pending = null;
        if (f != null && !f.isDone()) {
            f.complete(payload);
        }
    }

    public static synchronized void clearPending() {
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
        }
        pending = null;
    }
}
