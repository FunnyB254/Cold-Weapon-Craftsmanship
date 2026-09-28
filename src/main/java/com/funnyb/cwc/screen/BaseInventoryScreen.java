package com.funnyb.cwc.screen;

import com.funnyb.cwc.menu.InventoryLayout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * CWC 物品栏界面的抽象基类。
 * <p>
 * 职责：
 * <ul>
 *   <li>渲染底层背景贴图（由 InventoryLayout 指定纹理和尺寸）</li>
 *   <li>物品渲染走原版默认——在所有界面（工匠台、背包、创造物品栏）中显示一致</li>
 *   <li>不渲染原版标题文字（由子类自行绘制）</li>
 * </ul>
 */
public class BaseInventoryScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected final InventoryLayout layout;

    /** 左侧零件数值浮窗——内容由 {@link #infoPanelPart()} 决定 */
    private final PartInfoPanel infoPanel = new PartInfoPanel();

    public BaseInventoryScreen(T menu, Inventory playerInventory, Component title, InventoryLayout layout) {
        super(menu, playerInventory, title);
        this.layout = layout;
        this.imageWidth = layout.imageWidth;
        this.imageHeight = layout.imageHeight;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 浮窗必须画在主面板【之前】：主面板的左边缘（1px 黑外轮廓 + 2px 白高光）要压住它的右端，
        // 才读得出"从底下抽出来"那层意思。这个顺序就是那条观感的全部，所以只在基类这一处写死，
        // 两个子类不各自负责一遍（它们只回答"显示哪个零件"）。
        infoPanel.render(guiGraphics, this.leftPos, this.topPos, infoPanelPart());
        guiGraphics.blit(layout.texture, this.leftPos, this.topPos, 0, 0,
                layout.imageWidth, layout.imageHeight, layout.texWidth, layout.texHeight);
    }

    /**
     * 浮窗要显示的"当前零件"。返回 {@link ItemStack#EMPTY} 即隐藏——显示条件因此完全落在子类手里：
     * 制造界面给选中的材料变体（没选就是空），装配界面给底座槽里的物品（空槽就是空）。
     */
    protected ItemStack infoPanelPart() {
        return ItemStack.EMPTY;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 不绘制默认标题
    }

    // renderSlot 使用原版默认渲染，使零件在所有界面中显示一致

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
