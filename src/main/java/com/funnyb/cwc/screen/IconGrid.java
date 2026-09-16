package com.funnyb.cwc.screen;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.layout.Layouts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * 可滚动图标网格——用于制造界面右侧的零件列表。
 * <p>
 * 特性：
 * <ul>
 *   <li>格子排列（列数可配），每个格子渲染一个 ItemStack 图标</li>
 *   <li>鼠标滚轮上下滚动，拖拽滚动条滑块快速跳转</li>
 *   <li>点击图标选中零件——选中后显示 select.png 覆盖层并打印日志</li>
 *   <li>不裁剪溢出：允许零件贴图超出 16×16 格子完整显示（如 32×32 手半剑），溢出部分可能覆盖相邻格子/背景</li>
 *   <li>悬停检测仅在网格可见区域内生效（防止列表外误触发 tooltip）</li>
 * </ul>
 * 列数、图标尺寸、行高、滑块尺寸从 assets/cwc/gui/icon_grid.json 读取，资源包可覆盖。
 * <p>
 * 使用方式：构造→setItems→每帧 setPosition + render，getHoveredItem 获取悬停物品，
 * getSelectedItem 获取当前选中的零件。
 */
public class IconGrid {

    private int x;
    private int y;
    private final int width;
    private final int height;
    /** 网格名称——用于日志标识 */
    private final String name;

    /** 滚动条滑块贴图 */
    private static final ResourceLocation SLIDER_TEX =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "textures/gui/slider.png");
    /** 选中高亮贴图——覆盖在选中图标上方 */
    private static final ResourceLocation SELECT_TEX =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "textures/gui/select.png");

    private List<ItemStack> items = List.of();
    private int scrollOffset = 0;
    private boolean dragging = false;
    private int dragOffsetFromThumbTop = 0;

    /** 当前选中物品的索引，-1 表示未选中 */
    private int selectedIndex = -1;
    /** 选择变更回调 */
    private java.util.function.Consumer<ItemStack> onSelectionChanged;

    public IconGrid(String name, int x, int y, int width, int height) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** 每帧渲染前更新网格在屏幕上的位置 */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** 设置显示的物品列表，并将滚动位置和选中状态重置 */
    public void setItems(List<ItemStack> items) {
        this.items = items;
        this.scrollOffset = 0;
        this.selectedIndex = -1;
    }

    public void onSelectionChanged(java.util.function.Consumer<ItemStack> callback) {
        this.onSelectionChanged = callback;
    }

    /** @return 当前选中的物品，未选中时返回 EMPTY */
    public ItemStack getSelectedItem() {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            return items.get(selectedIndex);
        }
        return ItemStack.EMPTY;
    }

    // ──── 布局参数（cols/尺寸一律夹到 ≥1：取自资源包的布局 JSON，为 0 时 rows() 除零崩溃、网格不可点） ────

    private int cols() { return Math.max(1, Layouts.iconGrid().cols); }
    private int iconSize() { return Math.max(1, Layouts.iconGrid().icon_size); }
    private int rowHeight() { return Math.max(1, Layouts.iconGrid().row_height); }
    private int thumbWidth() { return Layouts.iconGrid().thumb_width; }
    private int thumbHeight() { return Layouts.iconGrid().thumb_height; }
    private int rows() { return (items.size() + cols() - 1) / cols(); }
    private int maxScroll() { return Math.max(0, rows() * rowHeight() - height); }

    /** 根据鼠标坐标获取对应的物品索引，-1 表示未命中 */
    private int getIndexAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return -1;
        }
        int col = (int) ((mouseX - x) / iconSize());
        int row = (int) ((mouseY - y + scrollOffset) / rowHeight());
        if (col >= 0 && col < cols() && row >= 0) {
            int index = row * cols() + col;
            if (index < items.size()) {
                return index;
            }
        }
        return -1;
    }

    // ──── 鼠标事件 ────

    /** 滚轮滚动——仅在鼠标位于网格内时处理 */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        scrollOffset = (int) Math.clamp(scrollOffset - scrollY * rowHeight(), 0, maxScroll());
        return true;
    }

    /** 鼠标按下——优先处理滑块拖拽，其次处理物品选中 */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滑块拖拽
        if (isOnThumb((int) mouseX, (int) mouseY)) {
            dragging = true;
            dragOffsetFromThumbTop = (int) mouseY - currentThumbY();
            return true;
        }
        // 物品选中——左键点击有效物品
        if (button == 0) {
            int idx = getIndexAt(mouseX, mouseY);
            if (idx >= 0) {
                selectedIndex = idx;
                ItemStack sel = items.get(idx);
                ColdWeaponCraftsmanship.LOGGER.info("[{}] selected: [{}] {}",
                        name, idx, sel.getHoverName().getString());
                if (onSelectionChanged != null) onSelectionChanged.accept(sel);
                return true;
            }
        }
        return false;
    }

    private int currentThumbY() {
        int ms = maxScroll();
        if (ms > 0) {
            return y + (int) ((float) scrollOffset / ms * (height - thumbHeight()));
        }
        return y;
    }

    private boolean isOnThumb(int mx, int my) {
        int ms = maxScroll();
        int trackHeight = height;
        int th = thumbHeight();
        int tbX = x + width - thumbWidth();
        int tbY = y + (ms > 0 ? (int) ((float) scrollOffset / ms * (trackHeight - th)) : 0);
        return mx >= tbX && mx < tbX + thumbWidth() && my >= tbY && my < tbY + th;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            int ms = maxScroll();
            if (ms > 0) {
                int trackHeight = height - thumbHeight();
                float ratio = (float) (mouseY - dragOffsetFromThumbTop - y) / trackHeight;
                scrollOffset = (int) Math.clamp(ratio * ms, 0, ms);
            }
            return true;
        }
        return false;
    }

    /** 获取鼠标悬停位置对应的物品——用于 tooltip */
    public ItemStack getHoveredItem(double mouseX, double mouseY) {
        int idx = getIndexAt(mouseX, mouseY);
        return idx >= 0 ? items.get(idx) : ItemStack.EMPTY;
    }

    // ──── 渲染 ────

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 不裁剪：允许贴图超出格子完整显示（如 32×32 手半剑），滚动滚出的残影随之可见属预期
        int rows = rows();
        int cs = cols();
        int is = iconSize();
        int rh = rowHeight();
        for (int r = 0; r < rows; r++) {
            int itemY = y - scrollOffset + r * rh;
            for (int c = 0; c < cs; c++) {
                int idx = r * cs + c;
                if (idx >= items.size()) break;
                int itemX = x + c * is;
                guiGraphics.renderItem(items.get(idx), itemX, itemY);

                // 选中覆盖层——16×16 贴图覆盖在选中图标上
                if (idx == selectedIndex) {
                    guiGraphics.blit(SELECT_TEX, itemX, itemY, 0, 0, is, is, is, is);
                }
            }
        }

        // 滚动条滑块
        int ms = maxScroll();
        if (ms > 0) {
            int th = thumbHeight();
            int tw = thumbWidth();
            int trackHeight = height;
            int thumbY = y + (int) ((float) scrollOffset / ms * (trackHeight - th));
            guiGraphics.blit(SLIDER_TEX, x + width - tw, thumbY, 0, 0, tw, th, tw, th);
        }
    }
}
