package com.configcloudbackuper.config.widget;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.client.ClientServerUploadSender;
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
import java.util.ArrayList;
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
    private static volatile String resultTitle = "";
    private static volatile List<String> resultLines = List.of();
    private static volatile boolean lastSuccess = true;
    private static volatile boolean serverSupported = false;
    private static volatile String serverProtocol = "";
    /** 服务端是否在能力包中允许客户端上传（2u/3u + 末尾 0/1）；旧协议 "1" 视为允许，由 begin 再校验 */
    private static volatile boolean serverAllowsClientUpload = true;

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

        boolean remoteReady = isRemoteActionsReady();
        boolean uploadReady = isUploadToServerReady();
        int disabledColor = 0xFF5C5C5C;
        drawButton(context, statusX, buttonY, remoteReady ? 0xFF3F51B5 : disabledColor, isChinese ? "状态" : "Status");
        drawButton(context, listX, buttonY, remoteReady ? 0xFF009688 : disabledColor, isChinese ? "列表" : "List");
        drawButton(context, uploadX, buttonY, uploadReady ? (isUploading ? 0xFF666666 : 0xFF8E24AA) : disabledColor, isUploading ? (isChinese ? "上传中..." : "Uploading...") : (isChinese ? "上传最新" : "Upload Latest"));

        String supportHint;
        if (!remoteReady) {
            supportHint = isChinese ? "请在支持本模组服务端的环境下使用" : "Use this on a server with this mod installed";
        } else if (!serverAllowsClientUpload) {
            supportHint = isChinese ? "服务端已关闭「客户端上传到服务器」（状态/列表仍可用）" : "Server disabled client upload (status/list still work)";
        } else {
            supportHint = isChinese ? "当前服务端已支持联动" : "Server integration supported";
        }
        int hintColor = !remoteReady ? 0xFFFFB74D : (!serverAllowsClientUpload ? 0xFFFFB74D : 0xFF66BB6A);
        context.drawTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(supportHint + (serverProtocol.isEmpty() ? "" : " (protocol " + serverProtocol + ")")),
                x + 8,
                buttonY - 12,
                hintColor
        );

        if (!resultTitle.isEmpty()) {
            int titleColor = lastSuccess ? 0xFF66BB6A : 0xFFEF5350;
            int textY = buttonY + BUTTON_HEIGHT + 6;
            context.drawTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    Text.literal(resultTitle),
                    x + 8,
                    textY,
                    titleColor
            );
            int lineY = textY + 12;
            for (String line : resultLines) {
                context.drawTextWithShadow(
                        MinecraftClient.getInstance().textRenderer,
                        Text.literal(" - " + line),
                        x + 12,
                        lineY,
                        0xFFE0E0E0
                );
                lineY += 10;
            }
        }
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
            if (!isRemoteActionsReady()) {
                showUnsupportedHint();
                return true;
            }
            requestServerAction("status");
            return true;
        }
        if (inButton(mouseX, mouseY, listX)) {
            if (!isRemoteActionsReady()) {
                showUnsupportedHint();
                return true;
            }
            requestServerAction("list");
            return true;
        }
        if (inButton(mouseX, mouseY, uploadX)) {
            if (!isUploadToServerReady()) {
                if (!isRemoteActionsReady()) {
                    showUnsupportedHint();
                } else {
                    setLocalResult(
                            isChinese ? "服务端联动结果" : "Remote Server Result",
                            false,
                            List.of(isChinese ? "服务端未开启客户端上传，请让管理员打开 clientUploadToServerEnabled。" : "Server has client upload disabled (clientUploadToServerEnabled).")
                    );
                }
                return true;
            }
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
        if (!isRemoteActionsReady()) {
            setLocalResult(
                    isChinese ? "服务端联动结果" : "Remote Server Result",
                    false,
                    List.of(isChinese ? "当前未连接支持该功能的服务端。" : "Server does not support this feature.")
            );
            return;
        }
        ClientPlayNetworking.send(new ServerSyncNetworking.ServerActionPayload(action));
        if ("status".equals(action)) {
            setLocalResult(
                    isChinese ? "服务端联动结果" : "Remote Server Result",
                    true,
                    List.of(isChinese ? "已请求服务端状态，等待返回..." : "Requested server status, waiting for response...")
            );
        } else {
            setLocalResult(
                    isChinese ? "服务端联动结果" : "Remote Server Result",
                    true,
                    List.of(isChinese ? "已请求服务端列表，等待返回..." : "Requested server list, waiting for response...")
            );
        }
    }

    private void uploadLatestBackup() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isUploadToServerReady()) {
            setLocalResult(
                    isChinese ? "服务端联动结果" : "Remote Server Result",
                    false,
                    List.of(isChinese ? "当前未连接支持该功能的服务端。" : "Server does not support this feature.")
            );
            return;
        }
        ModConfig config = FabricModInitializer.getInstance().getClientModConfigurationManager().read();
        Path file = BackupCoordinator.findLatestBackupPath(config);
        if (file == null || !Files.isRegularFile(file)) {
            setLocalResult(
                    isChinese ? "服务端联动结果" : "Remote Server Result",
                    false,
                    List.of(isChinese ? "未找到可上传的本地备份文件。" : "No local backup file found.")
            );
            return;
        }

        isUploading = true;
        setLocalResult(
                isChinese ? "服务端联动结果" : "Remote Server Result",
                true,
                List.of((isChinese ? "开始上传: " : "Uploading: ") + file.getFileName())
        );
        CompletableFuture.runAsync(() -> {
            try {
                byte[] all = Files.readAllBytes(file);
                String sha = HashUtils.sha256HexBytes(all);
                boolean useAck = protocolUsesUploadHandshake(getServerProtocolVersion());
                client.execute(() -> ClientServerUploadSender.send(
                        client,
                        useAck,
                        file.getFileName().toString(),
                        all,
                        sha,
                        () -> {
                            setLocalResult(
                                    isChinese ? "服务端联动结果" : "Remote Server Result",
                                    true,
                                    List.of(isChinese ? "上传分片已发送，等待服务端确认。" : "Upload sent, waiting for server confirmation.")
                            );
                            isUploading = false;
                        },
                        msg -> {
                            FabricModInitializer.getLogger().error("Upload latest backup to server failed: " + msg);
                            setLocalResult(
                                    isChinese ? "服务端联动结果" : "Remote Server Result",
                                    false,
                                    List.of((isChinese ? "上传失败: " : "Upload failed: ") + msg)
                            );
                            isUploading = false;
                        }
                ));
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("Upload latest backup to server failed (read file)", e);
                client.execute(() -> {
                    setLocalResult(
                            isChinese ? "服务端联动结果" : "Remote Server Result",
                            false,
                            List.of((isChinese ? "上传失败: " : "Upload failed: ") + e.getMessage())
                    );
                    isUploading = false;
                });
            }
        });
    }

    public static void updateFromServerResult(String action, boolean success, List<String> lines) {
        String title;
        if ("status".equals(action)) {
            title = "Server Status";
        } else if ("list".equals(action)) {
            title = "Server List";
        } else {
            title = "Server Action";
        }
        setLocalResult(title, success, lines);
    }

    public static void updateServerCapability(boolean supported, String protocolVersion) {
        serverSupported = supported;
        serverProtocol = protocolVersion == null ? "" : protocolVersion;
        serverAllowsClientUpload = parseServerAllowsClientUpload(protocolVersion);
    }

    /**
     * 供客户端命令等在无 GUI 场景下判断：当前连接的服务端是否允许客户端上传备份。
     */
    public static boolean isClientUploadAllowedByServer() {
        return serverAllowsClientUpload;
    }

    private static boolean parseServerAllowsClientUpload(String protocolVersion) {
        if (protocolVersion == null) {
            return true;
        }
        if (protocolVersion.length() >= 4 && (protocolVersion.startsWith("2u") || protocolVersion.startsWith("3u"))) {
            return protocolVersion.charAt(3) == '1';
        }
        return true;
    }

    public static String getServerProtocolVersion() {
        return serverProtocol;
    }

    /** 协议主版本号不低于 3 时，上传需等待服务端对 begin 的 ACK 后再发分片。 */
    public static boolean protocolUsesUploadHandshake(String protocolVersion) {
        if (protocolVersion == null || protocolVersion.isEmpty()) {
            return false;
        }
        char c = protocolVersion.charAt(0);
        return Character.isDigit(c) && (c - '0') >= 3;
    }

    private boolean isRemoteActionsReady() {
        return serverSupported
                && ClientPlayNetworking.canSend(ServerSyncNetworking.ServerActionPayload.ID)
                && ClientPlayNetworking.canSend(ServerSyncNetworking.UploadBeginPayload.ID);
    }

    private boolean isUploadToServerReady() {
        return isRemoteActionsReady() && serverAllowsClientUpload;
    }

    private void showUnsupportedHint() {
        setLocalResult(
                isChinese ? "服务端联动结果" : "Remote Server Result",
                false,
                List.of(isChinese ? "请在支持本模组服务端的环境下使用。" : "Use this on a server with this mod installed.")
        );
    }

    private static void setLocalResult(String title, boolean success, List<String> lines) {
        resultTitle = title;
        lastSuccess = success;
        resultLines = List.copyOf(new ArrayList<>(lines));
    }

    @Override
    public int getItemHeight() {
        return 52 + Math.max(resultLines.size(), 1) * 10;
    }
}

