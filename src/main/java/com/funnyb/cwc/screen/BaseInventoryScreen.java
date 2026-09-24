package com.funnyb.cwc.screen;

import com.funnyb.cwc.menu.InventoryLayout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

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

    public BaseInventoryScreen(T menu, Inventory playerInventory, Component title, InventoryLayout layout) {
        super(menu, playerInventory, title);
        this.layout = layout;
        this.imageWidth = layout.imageWidth;
        this.imageHeight = layout.imageHeight;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(layout.texture, this.leftPos, this.topPos, 0, 0,
                layout.imageWidth, layout.imageHeight, layout.texWidth, layout.texHeight);
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
