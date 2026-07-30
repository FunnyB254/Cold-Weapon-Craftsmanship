package com.funnyb.cwc.menu;

import com.funnyb.cwc.client.Layouts;
import com.funnyb.cwc.item.CreativePartStar;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import java.util.function.Consumer;

/**
 * 可复用的物品栏布局组件。
 * 管理玩家背包（3行×9列）+ 快捷栏（1行×9列）共 36 个槽位的位置和渲染贴图。
 * 以首格左上角为基准，按 18px 间距自动排列所有槽位。
 * <p>
 * 布局参数从 assets/cwc/gui/inventory_layout.json 读取，资源包可覆盖。
 */
public class InventoryLayout {

    /** 快捷栏槽位数 */
    private static final int HOTBAR_SIZE = 9;
    /** 背包列数 */
    private static final int COLS = 9;
    /** 背包行数（不含快捷栏） */
    private static final int ROWS = 3;
    /** 标准槽位渲染尺寸（Minecraft 规范值，不可配置） */
    private static final int SLOT_SIZE = 18;

    /** 背景贴图资源位置 */
    public final ResourceLocation texture;
    /** GUI 渲染宽度（像素） */
    public final int imageWidth;
    /** GUI 渲染高度（像素） */
    public final int imageHeight;
    /** 贴图文件宽度（像素） */
    public final int texWidth;
    /** 贴图文件高度（像素） */
    public final int texHeight;
    /** 背包首格 X 坐标（相对于 GUI 左上角） */
    private final int startX;
    /** 背包首格 Y 坐标（相对于 GUI 左上角） */
    private final int startY;
    /** 快捷栏行 Y 坐标 */
    private final int hotbarY;

    /**
     * @param texturePath 贴图路径（相对于 textures/gui/，如 "textures/gui/crafting.png"）
     * @param imageWidth  GUI 渲染区域宽度
     * @param imageHeight GUI 渲染区域高度
     * @param texWidth    贴图文件宽度
     * @param texHeight   贴图文件高度
     * @param startX      背包首格（左上角）的 X 坐标
     * @param startY      背包首格（左上角）的 Y 坐标
     */
    public InventoryLayout(String texturePath,
                            int imageWidth, int imageHeight, int texWidth, int texHeight,
                            int startX, int startY) {
        this.texture = ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", texturePath);
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.texWidth = texWidth;
        this.texHeight = texHeight;
        this.startX = startX;
        this.startY = startY;
        this.hotbarY = startY + ROWS * SLOT_SIZE + Layouts.inventoryLayout().hotbar_gap;
    }

    /**
     * 创建所有物品栏槽位（背包 27 格 + 快捷栏 9 格）并通过回调添加。
     * 所有槽位均为 LockedSlot——禁止拾取创造零件之星，防止误操作丢出该物品。
     */
    public void addSlots(Consumer<Slot> slotConsumer, Inventory playerInventory) {
        // 背包区域：3 行 × 9 列
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                slotConsumer.accept(new LockedSlot(playerInventory, col + row * COLS + HOTBAR_SIZE,
                        startX + col * SLOT_SIZE, startY + row * SLOT_SIZE));
            }
        }
        // 快捷栏区域：1 行 × 9 列，与背包之间有间隔
        for (int col = 0; col < HOTBAR_SIZE; col++) {
            slotConsumer.accept(new LockedSlot(playerInventory, col,
                    startX + col * SLOT_SIZE, hotbarY));
        }
    }

    /**
     * 物品栏锁定槽位——继承原版 Slot，额外禁止拾取创造零件之星。
     * 该物品右键用于打开选择界面，不应被拖入物品栏或丢出。
     */
    private static class LockedSlot extends Slot {
        public LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        /** 若物品是 CreativePartStar 则禁止拾取 */
        @Override
        public boolean mayPickup(Player player) {
            return !(getItem().getItem() instanceof CreativePartStar);
        }
    }
}
