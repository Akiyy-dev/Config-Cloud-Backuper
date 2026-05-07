package com.configcloudbackuper.config.widget;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.config.BackupFileManager;
import com.configcloudbackuper.config.ModConfigScreen;
import com.configcloudbackuper.config.model.BackupFileInfo;
import com.configcloudbackuper.webdav.WebDavConfig;
import com.configcloudbackuper.webdav.WebDavUploader;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
 * 备份文件列表控件 - 显示备份文件及其操作按钮（删除、上传到 WebDAV）
 */
public class BackupFileListEntry extends AbstractConfigListEntry<Void> {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private static final int ROW_HEIGHT = 20;

    // 按钮尺寸常量
    private static final int BUTTON_WIDTH = 20;
    private static final int BUTTON_GAP = 2;
    private static final int BUTTON_AREA_WIDTH = 2 * BUTTON_WIDTH + 1 * BUTTON_GAP; // 44px

    // 各列间距
    private static final int TEXT_PADDING_LEFT = 3;
    private static final int SIZE_COLUMN_WIDTH = 60;
    private static final int TIME_COLUMN_WIDTH = 130;

    private final List<BackupFileInfo> backupFiles;
    private final BackupFileManager fileManager;
    private final Runnable onRefresh;
    private final WebDavUploader webDavUploader;
    private final WebDavConfig webDavConfig;
    private final Screen parentScreen;

    // 缓存渲染时的位置信息，供 mouseClicked 使用
    private int cachedX;
    private int cachedEntryWidth;
    private int cachedY;

    // 每行按钮的缓存坐标（按行索引）
    private int[] cachedRowButtonX;

    public BackupFileListEntry(
            List<BackupFileInfo> backupFiles,
            BackupFileManager fileManager,
            Runnable onRefresh,
            WebDavUploader webDavUploader,
            WebDavConfig webDavConfig,
            Screen parentScreen
    ) {
        super(Text.literal(""), false);
        this.backupFiles = backupFiles;
        this.fileManager = fileManager;
        this.onRefresh = onRefresh;
        this.webDavUploader = webDavUploader;
        this.webDavConfig = webDavConfig;
        this.parentScreen = parentScreen;
        this.cachedRowButtonX = new int[backupFiles != null ? backupFiles.size() : 0];
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
        // 缓存位置信息供 mouseClicked 使用
        this.cachedX = x;
        this.cachedEntryWidth = entryWidth;
        this.cachedY = y;

        try {
            if (backupFiles == null || backupFiles.isEmpty()) {
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal("暂无备份文件 / No backup files"),
                        x + entryWidth / 2, y + 6, 0x808080);
                return;
            }

            // 确保缓存数组大小足够
            if (cachedRowButtonX.length < backupFiles.size()) {
                cachedRowButtonX = new int[backupFiles.size()];
            }

            for (int i = 0; i < backupFiles.size(); i++) {
                BackupFileInfo fileInfo = backupFiles.get(i);
                if (fileInfo == null) continue;
                int rowY = y + i * ROW_HEIGHT;
                renderFileRow(context, fileInfo, rowY, i, x, entryWidth, mouseX, mouseY);
            }
        } catch (Exception e) {
            FabricModInitializer.getLogger().error("Error rendering backup file list", e);
        }
    }

    private void renderFileRow(DrawContext context, BackupFileInfo fileInfo,
                                int y, int index, int x, int rowWidth, int mouseX, int mouseY) {
        int rowHeight = ROW_HEIGHT;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // 绘制行背景（交替颜色）
        if (index % 2 == 0) {
            context.fill(x, y, x + rowWidth, y + rowHeight, 0x15FFFFFF);
        }

        // 计算布局：右侧按钮区域固定，左侧文本区域自适应
        int buttonAreaStartX = x + rowWidth - BUTTON_AREA_WIDTH - 2; // 右侧留 2px 边距
        int textAreaWidth = buttonAreaStartX - x - TEXT_PADDING_LEFT - 2;

        // 缓存该行按钮起始 X 坐标供 mouseClicked 使用
        cachedRowButtonX[index] = buttonAreaStartX;

        // --- 文件名（使用像素宽度截断） ---
        String fileName = fileInfo.getFileName();
        // 为大小和时间列预留空间（大小约 60px，时间约 130px）
        int fileNameMaxWidth = Math.max(textAreaWidth - SIZE_COLUMN_WIDTH - TIME_COLUMN_WIDTH - 10, 50);
        String displayName = truncateFileNameByWidth(fileName, fileNameMaxWidth, textRenderer);
        context.drawTextWithShadow(textRenderer,
                Text.literal(displayName), x + TEXT_PADDING_LEFT, y + 6, 0xE0E0E0);

        // --- 文件大小（在文件名右侧） ---
        int sizeX = x + TEXT_PADDING_LEFT + fileNameMaxWidth + 5;
        String sizeText = fileInfo.getFormattedSize();
        context.drawTextWithShadow(textRenderer,
                Text.literal(sizeText), sizeX, y + 6, 0xA0A0A0);

        // --- 修改时间（在大小右侧） ---
        int timeX = sizeX + SIZE_COLUMN_WIDTH;
        String timeText = DATE_FORMATTER.format(
                Instant.ofEpochMilli(fileInfo.getLastModifiedTime().toMillis()));
        context.drawTextWithShadow(textRenderer,
                Text.literal(timeText), timeX, y + 6, 0xA0A0A0);

        // --- 右侧两个操作按钮 ---
        int buttonY = y + 4;

        // 删除按钮（红色）
        int deleteX = buttonAreaStartX;
        context.fill(deleteX, buttonY, deleteX + BUTTON_WIDTH, buttonY + 12, 0x44FF4444);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("✕"), deleteX + BUTTON_WIDTH / 2, buttonY + 2, 0xFF6666);

        // WebDAV 上传按钮（绿色）
        int uploadX = buttonAreaStartX + BUTTON_WIDTH + BUTTON_GAP;
        context.fill(uploadX, buttonY, uploadX + BUTTON_WIDTH, buttonY + 12, 0x4444AA44);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("☁"), uploadX + BUTTON_WIDTH / 2, buttonY + 2, 0x66FF66);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 使用缓存的渲染位置进行点击检测
        if (backupFiles == null || backupFiles.isEmpty()) return false;

        // 计算相对于 entry 起始位置的 Y 偏移
        int relativeY = (int) mouseY - this.cachedY;
        if (relativeY < 0) return false;

        int rowIndex = relativeY / ROW_HEIGHT;
        if (rowIndex < 0 || rowIndex >= backupFiles.size()) return false;

        BackupFileInfo fileInfo = backupFiles.get(rowIndex);
        if (fileInfo == null) return false;

        // 使用缓存的行按钮起始 X 坐标
        if (rowIndex >= cachedRowButtonX.length) return false;
        int buttonAreaStartX = cachedRowButtonX[rowIndex];

        // 删除按钮
        if (mouseX >= buttonAreaStartX && mouseX < buttonAreaStartX + BUTTON_WIDTH) {
            onDeleteClick(fileInfo);
            return true;
        }
        // WebDAV 上传按钮
        int uploadX = buttonAreaStartX + BUTTON_WIDTH + BUTTON_GAP;
        if (mouseX >= uploadX && mouseX < uploadX + BUTTON_WIDTH) {
            onWebDavUploadClick(fileInfo);
            return true;
        }

        return false;
    }

    /**
     * 删除按钮点击处理 - 删除后重建整个配置 Screen 刷新列表
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
            // 重建整个配置 Screen 来刷新列表
            MinecraftClient.getInstance().send(() -> {
                MinecraftClient.getInstance().setScreen(ModConfigScreen.create(parentScreen));
            });
        }, MinecraftClient.getInstance());
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
     * 根据像素宽度截断文件名，超出部分用 "..." 替代
     */
    private String truncateFileNameByWidth(String fileName, int maxWidthPx, TextRenderer textRenderer) {
        if (fileName == null) return "";
        if (textRenderer.getWidth(fileName) <= maxWidthPx) return fileName;

        // 逐字符截断直到宽度符合要求（保留 "..." 的空间）
        String ellipsis = "...";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        int availableWidth = maxWidthPx - ellipsisWidth;

        for (int i = fileName.length(); i > 0; i--) {
            String sub = fileName.substring(0, i);
            if (textRenderer.getWidth(sub) <= availableWidth) {
                return sub + ellipsis;
            }
        }
        return ellipsis;
    }

    @Override
    public int getItemHeight() {
        return ROW_HEIGHT * Math.max(backupFiles == null ? 1 : backupFiles.size(), 1);
    }
}
