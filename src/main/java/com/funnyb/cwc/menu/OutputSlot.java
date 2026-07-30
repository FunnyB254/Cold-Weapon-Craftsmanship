package com.funnyb.cwc.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 输出槽位——展示制造/组装结果。
 * 玩家取出成品时，通过回调自动消耗输入槽中的材料。
 */
public class OutputSlot extends Slot {

    private final MaterialConsumer onTake;

    /**
     * 材料消耗回调接口。
     * 当玩家从输出槽取出物品时触发，由实现方定义具体的材料扣除逻辑。
     */
    @FunctionalInterface
    public interface MaterialConsumer {
        void consumeMaterials(Player player);
    }

    /**
     * @param container 物品容器
     * @param index     容器内槽位索引
     * @param x         槽位在 GUI 中的 X 坐标
     * @param y         槽位在 GUI 中的 Y 坐标
     * @param onTake    取出成品时触发的材料消耗回调
     */
    public OutputSlot(Container container, int index, int x, int y, MaterialConsumer onTake) {
        super(container, index, x, y);
        this.onTake = onTake;
    }

    /** 禁止玩家手动放入物品（物品由服务端逻辑填入） */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    /** 取出成品时先调父类逻辑，再触发材料消耗回调 */
    @Override
    public void onTake(Player player, ItemStack stack) {
        super.onTake(player, stack);
        onTake.consumeMaterials(player);
    }
}
