package com.naocraftlab.configbackuper.config.widget;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.core.BackupLimiter;
import com.naocraftlab.configbackuper.core.ConfigBackuper;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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

    private final ConfigBackuper backuper;
    private final BackupLimiter limiter;
    private final Runnable onSuccess;
    private boolean isBackingUp = false;

    public BackupNowButtonEntry(ConfigBackuper backuper, BackupLimiter limiter, Runnable onSuccess) {
        super(Text.literal(""), false);
        this.backuper = backuper;
        this.limiter = limiter;
        this.onSuccess = onSuccess;
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
        // 绘制按钮背景
        int buttonWidth = 150;
        int buttonHeight = 20;
        int buttonX = x + (entryWidth - buttonWidth) / 2;
        int buttonY = y + (entryHeight - buttonHeight) / 2;

        // 按钮颜色
        int backgroundColor = isBackingUp ? 0xFF555555 : 0xFF4CAF50;
        int textColor = 0xFFFFFFFF;

        // 绘制按钮矩形
        context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, backgroundColor);

        // 绘制按钮文字
        String buttonText = isBackingUp ? "⏳ 备份中..." : "📦 立即备份 / Backup Now";
        context.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(buttonText),
                buttonX + buttonWidth / 2,
                buttonY + (buttonHeight - 8) / 2,
                textColor
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseInside((int) mouseX, (int) mouseY, 0, 0, 0, 0)) {
            return false;
        }

        if (isBackingUp) return true;

        isBackingUp = true;

        // 在后台线程执行备份
        CompletableFuture.runAsync(() -> {
            try {
                backuper.performBackup();
                limiter.removeOldBackups();
                FabricModInitializer.getLogger().info("Manual backup completed successfully");
            } catch (Exception e) {
                FabricModInitializer.getLogger().error("Manual backup failed", e);
            }
        }).thenRunAsync(() -> {
            // 在主线程更新 UI
            isBackingUp = false;
            if (onSuccess != null) {
                onSuccess.run();
            }
        }, MinecraftClient.getInstance());

        return true;
    }

    @Override
    public int getItemHeight() {
        return 24;
    }
}
