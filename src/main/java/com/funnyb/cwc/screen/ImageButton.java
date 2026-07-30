package com.funnyb.cwc.screen;

import com.funnyb.cwc.client.Layouts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 透明按钮控件——视觉由背景贴图提供，按钮本身只负责点击检测和 hover 高亮。
 * hover 颜色从 assets/cwc/gui/image_button.json 读取，资源包可覆盖。
 */
public class ImageButton extends AbstractButton {

    /** 点击时执行的回调 */
    private final Runnable onPress;

    /**
     * @param x       按钮左上角 X 坐标
     * @param y       按钮左上角 Y 坐标
     * @param width   按钮宽度
     * @param height  按钮高度
     * @param onPress 点击时触发的回调
     */
    public ImageButton(int x, int y, int width, int height, Runnable onPress) {
        super(x, y, width, height, Component.empty());
        this.onPress = onPress;
    }

    /** 按下时调用回调 */
    @Override
    public void onPress() {
        onPress.run();
    }

    /**
     * 不渲染可见的按钮贴图（贴图由父级 Screen 在背景中统一绘制）。
     * hover 时叠加半透明白色高亮。
     */
    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (isHovered()) {
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height,
                    Layouts.imageButton().hover_color);
        }
    }

    /** 无障碍叙述文本——使用默认按钮叙述 */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
