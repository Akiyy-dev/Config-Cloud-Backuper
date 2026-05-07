package com.configcloudbackuper.config.widget;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.core.BackupCoordinator;
import com.configcloudbackuper.core.ModConfig;
import com.configcloudbackuper.server.ServerSyncNetworking;
import com.configcloudbackuper.util.HashUtils;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 服务端联动操作入口（状态 / 列表 / 上传最新）
 */
public class ServerRemoteActionsEntry extends AbstractConfigListEntry<Void> {
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;

    private final boolean isChinese;
    private boolean isUploading = false;

    private int statusX;
    private int listX;
    private int uploadX;
    private int buttonY;

    public ServerRemoteActionsEntry(boolean isChinese) {
        super(Text.literal(""), false);
        this.isChinese = isChinese;
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public Text getDisplayedFieldName() {
        return Text.literal(isChinese ? "服务端联动" : "Remote Server");
    }

    @Override
    public boolean isRequiresRestart() {
        return false;
    }

    @Override
    public void setRequiresRestart(boolean requiresRestart) {
    }

    @Override
    public List<? extends net.minecraft.client.gui.Selectable> narratables() {
        return Collections.emptyList();
    }

    @Override
    public List<? extends net.minecraft.client.gui.Element> children() {
        return Collections.emptyList();
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int totalWidth = BUTTON_WIDTH * 3 + GAP * 2;
        int startX = x + (entryWidth - totalWidth) / 2;
        this.statusX = startX;
        this.listX = startX + BUTTON_WIDTH + GAP;
        this.uploadX = startX + (BUTTON_WIDTH + GAP) * 2;
        this.buttonY = y + (entryHeight - BUTTON_HEIGHT) / 2;

        drawButton(context, statusX, buttonY, 0xFF3F51B5, isChinese ? "状态" : "Status");
        drawButton(context, listX, buttonY, 0xFF009688, isChinese ? "列表" : "List");
        drawButton(context, uploadX, buttonY, isUploading ? 0xFF666666 : 0xFF8E24AA, isUploading ? (isChinese ? "上传中..." : "Uploading...") : (isChinese ? "上传最新" : "Upload Latest"));
    }

    private void drawButton(DrawContext context, int x, int y, int color, String text) {
        context.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, color);
        context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(text),
                x + BUTTON_WIDTH / 2,
                y + (BUTTON_HEIGHT - 8) / 2,
                0xFFFFFFFF
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inButton(mouseX, mouseY, statusX)) {
            requestServerAction("status");
            return true;
        }
        if (inButton(mouseX, mouseY, listX)) {
            requestServerAction("list");
            return true;
        }
        if (inButton(mouseX, mouseY, uploadX)) {
            if (isUploading) {
                return true;
            }
            uploadLatestBackup();
            return true;
        }
        return false;
    }

    private boolean inButton(double mouseX, double mouseY, int x) {
        return mouseX >= x && mouseX <= x + BUTTON_WIDTH && mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;
    }

    private void requestServerAction(String action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ClientPlayNetworking.canSend(ServerSyncNetworking.ServerActionPayload.ID)) {
            notifyClient(isChinese ? "当前未连接支持该功能的服务端。" : "Server does not support this feature.");
            return;
        }
        ClientPlayNetworking.send(new ServerSyncNetworking.ServerActionPayload(action));
        if ("status".equals(action)) {
            notifyClient(isChinese ? "已请求服务端状态。" : "Requested server status.");
        } else {
            notifyClient(isChinese ? "已请求服务端列表。" : "Requested server list.");
        }
    }

    private void uploadLatestBackup() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ClientPlayNetworking.canSend(ServerSyncNetworking.UploadBeginPayload.ID)) {
            notifyClient(isChinese ? "当前未连接支持该功能的服务端。" : "Server does not support this feature.");
            return;
        }
        ModConfig config = FabricModInitializer.getInstance().getModConfigurationManager().read();
        Path file = BackupCoordinator.findLatestBackupPath(config);
        if (file == null || !Files.isRegularFile(file)) {
            notifyClient(isChinese ? "未找到可上传的本地备份文件。" : "No local backup file found.");
            return;
        }

        isUploading = true;
        notifyClient((isChinese ? "开始上传: " : "Uploading: ") + file.getFileName());
        CompletableFuture.runAsync(() -> {
            try {
                byte[] all = Files.readAllBytes(file);
                ClientPlayNetworking.send(new ServerSyncNetworking.UploadBeginPayload(
                        file.getFileName().toString(),
                        all.length,
                        HashUtils.sha256Hex(file)
                ));
                final int chunkSize = 32 * 1024;
                for (int pos = 0; pos < all.length; pos += chunkSize) {
                    int len = Math.min(chunkSize, all.length - pos);
                    byte[] chunk = new byte[len];
                    System.arraycopy(all, pos, chunk, 0, len);
                    ClientPlayNetworking.send(new ServerSyncNetworking.UploadChunkPayload(chunk));
                }
                ClientPlayNetworking.send(new ServerSyncNetworking.UploadEndPayload());
                notifyClient(isChinese ? "上传分片已发送，等待服务端确认。" : "Upload sent, waiting for server confirmation.");
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("Upload latest backup to server failed", e);
                notifyClient((isChinese ? "上传失败: " : "Upload failed: ") + e.getMessage());
            } finally {
                isUploading = false;
            }
        });
    }

    private void notifyClient(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.execute(() -> client.player.sendMessage(Text.literal("[Config Cloud Backuper] " + msg), false));
        }
    }

    @Override
    public int getItemHeight() {
        return 24;
    }
}

