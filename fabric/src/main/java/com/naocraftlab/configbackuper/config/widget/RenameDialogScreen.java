package com.naocraftlab.configbackuper.config.widget;

import com.naocraftlab.configbackuper.FabricModInitializer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 重命名对话框 - 用于重命名备份文件
 */
public class RenameDialogScreen extends Screen {

    private static final int TITLE_Y = 30;
    private static final int FIELD_Y = 50;
    private static final int FIELD_WIDTH = 300;
    private static final int BUTTON_Y = 80;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_SPACING = 20;

    private final Path filePath;
    private final String currentName;
    private final Runnable onSuccess;

    private TextFieldWidget textField;
    private ButtonWidget confirmButton;
    private ButtonWidget cancelButton;
    private String errorMessage;

    public RenameDialogScreen(Path filePath, String currentName, Runnable onSuccess) {
        super(Text.literal("重命名备份文件 / Rename Backup File"));
        this.filePath = filePath;
        this.currentName = currentName;
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        super.init();

        // 文本框
        int fieldX = (this.width - FIELD_WIDTH) / 2;
        this.textField = new TextFieldWidget(this.textRenderer, fieldX, FIELD_Y, FIELD_WIDTH, 20,
                Text.literal("文件名 / File Name"));
        this.textField.setText(currentName);
        this.textField.setMaxLength(255);
        this.textField.setFocused(true);
        this.addSelectableChild(this.textField);

        // 确认按钮
        int confirmX = (this.width - BUTTON_WIDTH * 2 - BUTTON_SPACING) / 2;
        this.confirmButton = this.addDrawableChild(new ButtonWidget(
                confirmX, BUTTON_Y, BUTTON_WIDTH, 20,
                Text.literal("✓ 确认 / Confirm"),
                button -> onConfirm()));

        // 取消按钮
        int cancelX = confirmX + BUTTON_WIDTH + BUTTON_SPACING;
        this.cancelButton = this.addDrawableChild(new ButtonWidget(
                cancelX, BUTTON_Y, BUTTON_WIDTH, 20,
                Text.literal("✕ 取消 / Cancel"),
                button -> close()));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);

        // 绘制标题
        drawCenteredText(matrices, this.textRenderer, this.title,
                this.width / 2, TITLE_Y, 0xFFFFFF);

        // 绘制文本框
        this.textField.render(matrices, mouseX, mouseY, delta);

        // 绘制错误信息
        if (errorMessage != null && !errorMessage.isEmpty()) {
            drawCenteredText(matrices, this.textRenderer,
                    Text.literal(errorMessage),
                    this.width / 2, BUTTON_Y + 25, 0xFF5555);
        }

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        this.textField.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
            onConfirm();
            return true;
        }
        if (keyCode == 256) { // Escape
            close();
            return true;
        }
        return this.textField.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onConfirm() {
        String newName = this.textField.getText().trim();

        // 验证输入
        if (newName.isEmpty()) {
            errorMessage = "文件名不能为空 / File name cannot be empty";
            return;
        }

        if (newName.contains("/") || newName.contains("\\") || newName.contains(":")) {
            errorMessage = "文件名包含非法字符 / File name contains invalid characters";
            return;
        }

        if (newName.equals(currentName)) {
            close();
            return;
        }

        // 检查目标文件是否已存在（在主线程快速检查）
        Path targetPath = filePath.resolveSibling(newName);
        if (Files.exists(targetPath)) {
            errorMessage = "目标文件已存在 / Target file already exists";
            return;
        }

        // 在后台线程执行文件操作，完成后回到主线程回调
        final Path finalTargetPath = targetPath;
        CompletableFuture.runAsync(() -> {
            try {
                Files.move(filePath, finalTargetPath);
                FabricModInitializer.getLogger().info(
                        "Backup file renamed: " + currentName + " -> " + newName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(v -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
            close();
        }, client).exceptionally(e -> {
            errorMessage = "重命名失败: " + e.getCause().getMessage()
                    + " / Rename failed: " + e.getCause().getMessage();
            FabricModInitializer.getLogger().error("Failed to rename backup file", e.getCause());
            return null;
        });
    }

    private void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
