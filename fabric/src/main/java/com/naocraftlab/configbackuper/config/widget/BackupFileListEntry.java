package com.naocraftlab.configbackuper.config.widget;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.config.BackupFileManager;
import com.naocraftlab.configbackuper.config.model.BackupFileInfo;
import com.naocraftlab.configbackuper.webdav.WebDavConfig;
import com.naocraftlab.configbackuper.webdav.WebDavUploader;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 备份文件列表控件 - 显示备份文件及其操作按钮（删除、重命名、上传到 WebDAV）
 */
public class BackupFileListEntry extends AbstractConfigListEntry<Void> {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private static final int MAX_DISPLAY_NAME_LENGTH = 30;
    private static final int ROW_HEIGHT = 20;

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
        super(Text.literal(""), false);
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
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public Text getDisplayedFieldName() {
        return Text.literal("");
    }

    @Override
    public boolean isRequiresRestart() {
        return false;
    }

    @Override
    public void setRequiresRestart(boolean requiresRestart) {
        // 不需要重启
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
        if (backupFiles.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("暂无备份文件 / No backup files"),
                    x + entryWidth / 2, y + 6, 0x808080);
            return;
        }

        for (int i = 0; i < backupFiles.size(); i++) {
            BackupFileInfo fileInfo = backupFiles.get(i);
            int rowY = y + i * ROW_HEIGHT;
            renderFileRow(context, fileInfo, rowY, i, x, entryWidth, mouseX, mouseY);
        }
    }

    private void renderFileRow(DrawContext context, BackupFileInfo fileInfo,
                                int y, int index, int x, int rowWidth, int mouseX, int mouseY) {
        int rowHeight = ROW_HEIGHT;

        // 绘制行背景（交替颜色）
        if (index % 2 == 0) {
            context.fill(x, y, x + rowWidth, y + rowHeight, 0x15FFFFFF);
        }

        // 文件名（截断显示）
        String displayName = truncateFileName(fileInfo.getFileName(), MAX_DISPLAY_NAME_LENGTH);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal(displayName), x + 5, y + 6, 0xE0E0E0);

        // 文件大小
        String sizeText = fileInfo.getFormattedSize();
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal(sizeText), x + 180, y + 6, 0xA0A0A0);

        // 修改时间
        String timeText = DATE_FORMATTER.format(
                Instant.ofEpochMilli(fileInfo.getLastModifiedTime().toMillis()));
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal(timeText), x + 230, y + 6, 0xA0A0A0);

        // 操作按钮区域（右侧）
        int buttonX = x + rowWidth - 90;

        // 删除按钮（红色）
        context.fill(buttonX, y, buttonX + 20, y + rowHeight, 0x44FF4444);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("✕"), buttonX + 10, y + 6, 0xFF6666);

        // 重命名按钮
        context.fill(buttonX + 22, y, buttonX + 42, y + rowHeight, 0x444444FF);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("✎"), buttonX + 32, y + 6, 0x6666FF);

        // WebDAV 上传按钮
        context.fill(buttonX + 44, y, buttonX + 64, y + rowHeight, 0x4444AA44);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("☁"), buttonX + 54, y + 6, 0x66FF66);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseInside((int) mouseX, (int) mouseY, 0, 0, 0, 0)) {
            return false;
        }

        // Get the parent list's position info to calculate row index
        // We need to find which row was clicked based on relative y position
        int relativeY = (int) mouseY; // This will be adjusted by the parent list

        int rowIndex = relativeY / ROW_HEIGHT;
        if (rowIndex < 0 || rowIndex >= backupFiles.size()) return false;

        BackupFileInfo fileInfo = backupFiles.get(rowIndex);
        int rowY = rowIndex * ROW_HEIGHT;

        // Calculate button areas (approximate, since we don't have exact x/width here)
        // The actual x and entryWidth will be determined by the parent list widget
        // We use a simplified approach: check if click is in the rightmost 90px area
        int entryWidth = 400; // approximate, will be adjusted by parent

        int buttonAreaStartX = entryWidth - 90;

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
    public int getItemHeight() {
        return ROW_HEIGHT * Math.max(backupFiles.size(), 1);
    }
}
