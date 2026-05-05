package com.naocraftlab.configbackuper.config.widget;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.config.ModConfigScreen;
import com.naocraftlab.configbackuper.core.BackupCoordinator;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * "一键备份"按钮控件 - 在配置界面中显示一个立即执行备份的按钮
 */
public class BackupNowButtonEntry extends AbstractConfigListEntry<Void> {

    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parentScreen;
    private boolean isBackingUp = false;

    // 缓存渲染时的按钮位置，供 mouseClicked 使用
    private int cachedButtonX;
    private int cachedButtonY;
    private int cachedEntryX;
    private int cachedEntryWidth;

    public BackupNowButtonEntry(Screen parentScreen) {
        super(Text.literal(""), false);
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
        int backgroundColor = isBackingUp ? 0xFF555555 : 0xFF4CAF50;
        int textColor = 0xFFFFFFFF;

        // 绘制按钮矩形
        context.fill(cachedButtonX, cachedButtonY, cachedButtonX + BUTTON_WIDTH, cachedButtonY + BUTTON_HEIGHT, backgroundColor);

        // 绘制按钮文字
        String buttonText = isBackingUp ? "⏳ 备份中..." : "📦 立即备份 / Backup Now";
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

        if (isBackingUp) return true;

        isBackingUp = true;

        // 在后台线程执行备份
        CompletableFuture.runAsync(() -> {
            try {
                BackupCoordinator.runLocalBackupCleanupAndWebDavIfEnabled(FabricModInitializer.getInstance());
                FabricModInitializer.getLogger().info("Manual backup completed successfully");
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("Manual backup failed", e);
            }
        }).thenRunAsync(() -> {
            // 在主线程更新 UI - 重建整个配置 Screen 以刷新文件列表
            isBackingUp = false;
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
