package com.funnyb.cwc.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.function.Consumer;

/**
 * 可复用的物品栏布局组件。
 * 管理玩家背包（3行×9列）+ 快捷栏（1行×9列）共 36 个槽位的位置和渲染贴图。
 * <p>
 * 这 27+9 个槽位由**一个坐标**定位——即整块的左上角，由 gui/crafting_menu.json 的 {@code inventory} 给出。
 * 内部排列（18px 间距 + 快捷栏额外间隔）是原版几何，硬编码在本类里：它必须与烘焙在面板贴图上的
 * 槽位框逐像素对齐，不是可调项。
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
    /**
     * 快捷栏与背包之间的额外间隔（原版值）。
     * <p>
     * 它和上面的 18px 间距一样，是硬编码的原版几何——面板贴图上烘焙死的槽位框就按这套尺寸画的，
     * 改了必然错位，所以不留配置口子。整块唯一的可调项是左上角坐标（见类注释）。
     */
    private static final int HOTBAR_GAP = 4;

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
        this.hotbarY = startY + ROWS * SLOT_SIZE + HOTBAR_GAP;
    }

    /**
     * 创建所有物品栏槽位（背包 27 格 + 快捷栏 9 格）并通过回调添加。
     * 均为普通原版槽位——入口改成工作方块后，物品栏里不再有"需要保护、禁止取出"的入口物品。
     */
    public void addSlots(Consumer<Slot> slotConsumer, Inventory playerInventory) {
        // 背包区域：3 行 × 9 列
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                slotConsumer.accept(new Slot(playerInventory, col + row * COLS + HOTBAR_SIZE,
                        startX + col * SLOT_SIZE, startY + row * SLOT_SIZE));
            }
        }
        // 快捷栏区域：1 行 × 9 列，与背包之间有间隔
        for (int col = 0; col < HOTBAR_SIZE; col++) {
            slotConsumer.accept(new Slot(playerInventory, col,
                    startX + col * SLOT_SIZE, hotbarY));
        }
    }
}
