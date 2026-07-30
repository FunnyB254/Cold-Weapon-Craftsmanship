package com.funnyb.cwc.crafting;

import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 零件配方注册中心。
 * <p>
 * 按零件类型分组存储所有配方。提供配方查询、库存检查和材料扣除功能。
 * <p>
 * 添加新零件配方只需在此类的配方列表中加入新条目。
 */
public final class PartRecipes {

    /** 刃类零件配方列表——按放入顺序轮询 */
    private static final List<PartRecipe> BLADE_RECIPES = List.of(
            new PartRecipe(
                    new ItemStack(Items.IRON_INGOT, 2),
                    ItemStack.EMPTY,
                    ItemStack.EMPTY,
                    createPartStack(CwcItems.PART.get(), "cwc.standard_blade.iron")
            )
    );

    /** 创建带 PART_IDENTITY 和显示名的零件 ItemStack */
    private static ItemStack createPartStack(Item item, String identity) {
        ItemStack stack = new ItemStack(item);
        stack.set(CwcDataComponents.PART_IDENTITY.get(), identity);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("part." + identity));
        return stack;
    }

    private PartRecipes() {}

    /** @return 所有刃类零件配方（不可变列表） */
    public static List<PartRecipe> getBladeRecipes() {
        return BLADE_RECIPES;
    }

    /**
     * 检查玩家背包中是否有足够材料制造指定配方。
     * 每个槽位的物品独立计数，不计入盔甲槽和副手。
     */
    public static boolean canCraft(Player player, PartRecipe recipe) {
        Inventory inv = player.getInventory();
        return hasEnough(inv, recipe.slot0())
                && hasEnough(inv, recipe.slot1())
                && hasEnough(inv, recipe.slot2());
    }

    /**
     * 从玩家背包中扣除配方所需材料。
     * 在调用前应已通过 {@link #canCraft} 确保材料充足。
     */
    public static void consumeMaterials(Player player, PartRecipe recipe) {
        Inventory inv = player.getInventory();
        consume(inv, recipe.slot0());
        consume(inv, recipe.slot1());
        consume(inv, recipe.slot2());
    }

    /** 检查背包中是否有足够数量的指定物品（空 ItemStack 视为不需要） */
    private static boolean hasEnough(Inventory inv, ItemStack needed) {
        if (needed.isEmpty()) return true;
        int remaining = needed.getCount();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, needed)) {
                remaining -= stack.getCount();
            }
        }
        return remaining <= 0;
    }

    /** 从背包中扣除指定数量的物品 */
    private static void consume(Inventory inv, ItemStack needed) {
        if (needed.isEmpty()) return;
        int remaining = needed.getCount();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, needed)) {
                int toTake = Math.min(remaining, stack.getCount());
                stack.shrink(toTake);
                remaining -= toTake;
            }
        }
    }
}
