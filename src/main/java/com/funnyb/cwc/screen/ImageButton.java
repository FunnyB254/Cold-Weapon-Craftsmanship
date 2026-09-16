package com.funnyb.cwc.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 图标按钮——键体用【原版 widget/button 九宫格精灵】绘制，字形用独立贴图叠在上面。
 * <p>
 * 键体交给原版是为了悬停反馈：原版会按状态换成 button_highlighted（悬停）/
 * button_disabled（禁用），这就是原版按钮那种「亮起来」的按下感。之前自己 fill 一块
 * 半透明白，或者把键体烘焙进面板贴图，都做不到逐像素一致，换资源包也不会跟着变。
 * <p>
 * 字形贴图按按钮尺寸整张 blit，所以尺寸必须一致：16x16 的按钮配 16x16 的字形。
 */
public class ImageButton extends Button {

    /** 字形贴图——透明底，键体由原版精灵负责 */
    private final ResourceLocation icon;

    /**
     * @param x       按钮左上角 X 坐标
     * @param y       按钮左上角 Y 坐标
     * @param width   按钮宽度（须等于字形贴图宽）
     * @param height  按钮高度（须等于字形贴图高）
     * @param icon    字形贴图，见 {@link GuiIcons}
     * @param onPress 点击时触发的回调
     */
    public ImageButton(int x, int y, int width, int height, ResourceLocation icon, Runnable onPress) {
        super(x, y, width, height, Component.empty(), b -> onPress.run(), DEFAULT_NARRATION);
        this.icon = icon;
    }

    /** 原版键体（含悬停/禁用态）→ 字形。标题是空的，super 不会画出文字。 */
    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(icon, getX(), getY(), 0, 0, getWidth(), getHeight(), getWidth(), getHeight());
    }

    /**
     * 只认鼠标悬停，不认键盘焦点。
     * <p>
     * 原版把「焦点」也算作高亮：{@code AbstractContainerEventHandler.mouseClicked} 命中后会给
     * 控件设焦点，之后哪怕鼠标移开，{@code AbstractButton.renderWidget} 取到的仍是
     * button_highlighted，键体一直亮着那条白边框，看着像卡住了。
     * 本项目的图标按钮没有键盘导航需求，把焦点排除掉，高亮就只跟鼠标走。
     */
    @Override
    public boolean isHoveredOrFocused() {
        return isHovered();
    }
}
