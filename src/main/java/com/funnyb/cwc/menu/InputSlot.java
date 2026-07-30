package com.funnyb.cwc.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 输入槽位——用于展示配方所需材料，玩家无法放入或取出物品。
 * 物品由服务端逻辑控制填入和清空。
 */
public class InputSlot extends Slot {

    public InputSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    /** 禁止玩家手动放入任何物品 */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    /** 禁止玩家手动取出物品 */
    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
