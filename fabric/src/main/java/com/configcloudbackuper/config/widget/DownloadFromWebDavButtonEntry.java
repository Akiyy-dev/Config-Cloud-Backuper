package com.configcloudbackuper.config.widget;

import com.configcloudbackuper.FabricModInitializer;
import com.configcloudbackuper.config.ModConfigScreen;
import com.configcloudbackuper.core.ModConfig;
import com.configcloudbackuper.webdav.WebDavConfig;
import com.configcloudbackuper.webdav.WebDavDownloader;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * "从 WebDAV 下载"按钮控件 - 从 WebDAV 服务器下载最新的备份文件
 */
public class DownloadFromWebDavButtonEntry extends AbstractConfigListEntry<Void> {

    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;

    private final WebDavDownloader downloader;
    private final WebDavConfig webDavConfig;
    private final ModConfig modConfig;
    private final Screen parentScreen;
    private boolean isDownloading = false;

    // 缓存渲染时的按钮位置
    private int cachedButtonX;
    private int cachedButtonY;
    private int cachedEntryX;
    private int cachedEntryWidth;

    public DownloadFromWebDavButtonEntry(
            WebDavDownloader downloader,
            WebDavConfig webDavConfig,
            ModConfig modConfig,
            Screen parentScreen
    ) {
        super(Text.literal(""), false);
        this.downloader = downloader;
        this.webDavConfig = webDavConfig;
        this.modConfig = modConfig;
        this.parentScreen = parentScreen;
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
        this.cachedEntryX = x;
        this.cachedEntryWidth = entryWidth;
        this.cachedButtonX = x + (entryWidth - BUTTON_WIDTH) / 2;
        this.cachedButtonY = y + (entryHeight - BUTTON_HEIGHT) / 2;

        // 绘制按钮背景
        int backgroundColor = isDownloading ? 0xFF555555 : 0xFF2196F3;
        int textColor = 0xFFFFFFFF;

        // 绘制按钮矩形
        context.fill(cachedButtonX, cachedButtonY, cachedButtonX + BUTTON_WIDTH, cachedButtonY + BUTTON_HEIGHT, backgroundColor);

        // 绘制按钮文字
        String buttonText = isDownloading ? "⏳ 下载中..." : "⬇ 从 WebDAV 下载 / Download";
        context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(buttonText),
                cachedButtonX + BUTTON_WIDTH / 2,
                cachedButtonY + (BUTTON_HEIGHT - 8) / 2,
                textColor
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 使用缓存的按钮位置进行点击检测
        if (mouseX < cachedButtonX || mouseX > cachedButtonX + BUTTON_WIDTH
                || mouseY < cachedButtonY || mouseY > cachedButtonY + BUTTON_HEIGHT) {
            return false;
        }

        if (isDownloading) return true;

        isDownloading = true;

        // 在后台线程执行下载
        CompletableFuture.runAsync(() -> {
            try {
                Path backupFolder = modConfig.getBackupFolder();
                if (!backupFolder.isAbsolute()) {
                    backupFolder = Path.of(System.getProperty("user.dir")).resolve(backupFolder).normalize();
                }

                String error = downloader.downloadLatestBackup(backupFolder, webDavConfig);
                if (error != null) {
                    FabricModInitializer.getLogger().error("WebDAV download failed: " + error);
                } else {
                    FabricModInitializer.getLogger().info("WebDAV download completed successfully");
                }
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("WebDAV download failed", e);
            }
        }).thenRunAsync(() -> {
            // 在主线程更新 UI - 重建整个配置 Screen 以刷新文件列表
            isDownloading = false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && parentScreen != null) {
                client.setScreen(ModConfigScreen.create(parentScreen));
            }
        }, MinecraftClient.getInstance());

        return true;
    }

    @Override
    public int getItemHeight() {
        return 24;
    }
}
