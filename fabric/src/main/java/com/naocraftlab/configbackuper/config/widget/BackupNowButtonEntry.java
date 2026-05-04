package com.naocraftlab.configbackuper.config.widget;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.core.BackupLimiter;
import com.naocraftlab.configbackuper.core.ConfigBackuper;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * "一键备份"按钮控件 - 在配置界面中显示一个立即执行备份的按钮
 */
public class BackupNowButtonEntry extends AbstractConfigEntry<Void> {

    private final ConfigBackuper backuper;
    private final BackupLimiter limiter;
    private final Runnable onSuccess;
    private boolean isBackingUp = false;

    public BackupNowButtonEntry(ConfigBackuper backuper, BackupLimiter limiter, Runnable onSuccess) {
        this.backuper = backuper;
        this.limiter = limiter;
        this.onSuccess = onSuccess;
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
        return new PressableWidget(0, 0, 150, 20,
                Text.literal(isBackingUp ? "⏳ 备份中..." : "📦 立即备份 / Backup Now")) {

            @Override
            public void onPress() {
                if (isBackingUp) return;

                isBackingUp = true;
                setMessage(Text.literal("⏳ 备份中..."));

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
                    setMessage(Text.literal("📦 立即备份 / Backup Now"));
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                }, MinecraftClient.getInstance());
            }

            @Override
            public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                super.renderButton(matrices, mouseX, mouseY, delta);
            }
        };
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
