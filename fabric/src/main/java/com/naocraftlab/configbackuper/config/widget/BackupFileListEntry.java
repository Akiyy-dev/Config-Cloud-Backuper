package com.naocraftlab.configbackuper.config.widget;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.config.BackupFileManager;
import com.naocraftlab.configbackuper.config.model.BackupFileInfo;
import com.naocraftlab.configbackuper.webdav.WebDavConfig;
import com.naocraftlab.configbackuper.webdav.WebDavUploader;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 备份文件列表控件 - 显示备份文件及其操作按钮（删除、重命名、上传到 WebDAV）
 */
public class BackupFileListEntry extends AbstractConfigEntry<Void> {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private static final int MAX_DISPLAY_NAME_LENGTH = 30;

    private final List<BackupFileInfo> backupFiles;
    private final BackupFileManager fileManager;
    private final Runnable onRefresh;
    private final WebDavUploader webDavUploader;
    private final WebDavConfig webDavConfig;

    public BackupFileListEntry(
            List<BackupFileInfo> backupFiles,
            BackupFileManager fileManager,
            Runnable onRefresh,
            WebDavUploader webDavUploader,
            WebDavConfig webDavConfig
    ) {
        this.backupFiles = backupFiles;
        this.fileManager = fileManager;
        this.onRefresh = onRefresh;
        this.webDavUploader = webDavUploader;
        this.webDavConfig = webDavConfig;
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public AbstractConfigEntry<Void> setValue(Void value) {
        return this;
    }

    @Override
    public PressableWidget getItemWidget() {
        return new PressableWidget(0, 0, 400, 20 * Math.max(backupFiles.size(), 1),
                Text.literal("")) {

            @Override
            public void onPress() {
                // 不作为按钮点击处理，由子按钮处理
            }

            @Override
            public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                if (backupFiles.isEmpty()) {
                    drawCenteredText(matrices, MinecraftClient.getInstance().textRenderer,
                            Text.literal("暂无备份文件 / No backup files"),
                            this.x + this.width / 2, this.y + 6, 0x808080);
                    return;
                }

                int y = this.y;
                for (int i = 0; i < backupFiles.size(); i++) {
                    BackupFileInfo fileInfo = backupFiles.get(i);
                    renderFileRow(matrices, fileInfo, y, i, mouseX, mouseY, delta);
                    y += 20;
                }
            }

            private void renderFileRow(MatrixStack matrices, BackupFileInfo fileInfo,
                                       int y, int index, int mouseX, int mouseY, float delta) {
                int x = this.x;
                int rowWidth = this.width;
                int rowHeight = 20;

                // 绘制行背景（交替颜色）
                if (index % 2 == 0) {
                    fill(matrices, x, y, x + rowWidth, y + rowHeight, 0x15FFFFFF);
                }

                // 文件名（截断显示）
                String displayName = truncateFileName(fileInfo.getFileName(), MAX_DISPLAY_NAME_LENGTH);
                drawStringWithShadow(matrices, MinecraftClient.getInstance().textRenderer,
                        Text.literal(displayName), x + 5, y + 6, 0xE0E0E0);

                // 文件大小
                String sizeText = fileInfo.getFormattedSize();
                drawStringWithShadow(matrices, MinecraftClient.getInstance().textRenderer,
                        Text.literal(sizeText), x + 180, y + 6, 0xA0A0A0);

                // 修改时间
                String timeText = DATE_FORMATTER.format(
                        Instant.ofEpochMilli(fileInfo.getLastModifiedTime().toMillis()));
                drawStringWithShadow(matrices, MinecraftClient.getInstance().textRenderer,
                        Text.literal(timeText), x + 230, y + 6, 0xA0A0A0);

                // 操作按钮区域（右侧）
                int buttonX = x + rowWidth - 90;

                // 删除按钮（红色）
                fill(matrices, buttonX, y, buttonX + 20, y + 20, 0x44FF4444);
                drawCenteredText(matrices, MinecraftClient.getInstance().textRenderer,
                        Text.literal("✕"), buttonX + 10, y + 6, 0xFF6666);

                // 重命名按钮
                fill(matrices, buttonX + 22, y, buttonX + 42, y + 20, 0x444444FF);
                drawCenteredText(matrices, MinecraftClient.getInstance().textRenderer,
                        Text.literal("✎"), buttonX + 32, y + 6, 0x6666FF);

                // WebDAV 上传按钮
                fill(matrices, buttonX + 44, y, buttonX + 64, y + 20, 0x4444AA44);
                drawCenteredText(matrices, MinecraftClient.getInstance().textRenderer,
                        Text.literal("☁"), buttonX + 54, y + 6, 0x66FF66);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (!isMouseOver(mouseX, mouseY)) return false;

                int rowIndex = (int) ((mouseY - this.y) / 20);
                if (rowIndex < 0 || rowIndex >= backupFiles.size()) return false;

                BackupFileInfo fileInfo = backupFiles.get(rowIndex);
                int rowY = this.y + rowIndex * 20;
                int rowWidth = this.width;

                // 计算点击区域
                int buttonAreaStartX = this.x + rowWidth - 90;

                // 删除按钮
                if (mouseX >= buttonAreaStartX && mouseX < buttonAreaStartX + 20) {
                    onDeleteClick(fileInfo);
                    return true;
                }
                // 重命名按钮
                if (mouseX >= buttonAreaStartX + 22 && mouseX < buttonAreaStartX + 42) {
                    onRenameClick(fileInfo);
                    return true;
                }
                // WebDAV 上传按钮
                if (mouseX >= buttonAreaStartX + 44 && mouseX < buttonAreaStartX + 64) {
                    onWebDavUploadClick(fileInfo);
                    return true;
                }

                return false;
            }
        };
    }

    /**
     * 删除按钮点击处理
     */
    private void onDeleteClick(BackupFileInfo fileInfo) {
        CompletableFuture.runAsync(() -> {
            boolean success = fileManager.deleteBackupFile(fileInfo.getPath());
            if (success) {
                FabricModInitializer.getLogger().info(
                        "Deleted backup file: " + fileInfo.getFileName());
            } else {
                FabricModInitializer.getLogger().error(
                        "Failed to delete backup file: " + fileInfo.getFileName());
            }
        }).thenRunAsync(() -> {
            if (onRefresh != null) {
                onRefresh.run();
            }
        }, MinecraftClient.getInstance());
    }

    /**
     * 重命名按钮点击处理
     */
    private void onRenameClick(BackupFileInfo fileInfo) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(new RenameDialogScreen(
                    fileInfo.getPath(),
                    fileInfo.getFileName(),
                    onRefresh));
        }
    }

    /**
     * WebDAV 上传按钮点击处理
     */
    private void onWebDavUploadClick(BackupFileInfo fileInfo) {
        CompletableFuture.runAsync(() -> {
            String error = webDavUploader.uploadBackup(fileInfo.getPath(), webDavConfig);
            if (error != null) {
                FabricModInitializer.getLogger().error(
                        "WebDAV upload failed for " + fileInfo.getFileName() + ": " + error);
            } else {
                FabricModInitializer.getLogger().info(
                        "WebDAV upload completed: " + fileInfo.getFileName());
            }
        }).thenRunAsync(() -> {
            if (onRefresh != null) {
                onRefresh.run();
            }
        }, MinecraftClient.getInstance());
    }

    /**
     * 截断过长的文件名
     */
    private String truncateFileName(String fileName, int maxLength) {
        if (fileName.length() <= maxLength) return fileName;
        return fileName.substring(0, maxLength - 3) + "...";
    }

    @Override
    public Text getDisplayFieldName() {
        return Text.literal("");
    }

    @Override
    public List<Text> getItemTooltip() {
        return Collections.emptyList();
    }

    @Override
    public boolean isRequiresRestart() {
        return false;
    }
}
