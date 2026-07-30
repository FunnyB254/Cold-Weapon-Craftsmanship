package com.funnyb.cwc.crafting;

import net.minecraft.world.item.ItemStack;

/**
 * 零件制造配方。
 * <p>
 * 每个配方定义 3 个输入槽的需求（各槽独立表示物品种类+数量）和对应的输出零件。
 * 服务端据此检查玩家背包是否满足材料需求。
 *
 * @param slot0  输入槽 0 所需物品
 * @param slot1  输入槽 1 所需物品
 * @param slot2  输入槽 2 所需物品
 * @param output 成品零件
 */
public record PartRecipe(ItemStack slot0, ItemStack slot1, ItemStack slot2, ItemStack output) {
}
