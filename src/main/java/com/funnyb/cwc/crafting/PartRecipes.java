package com.funnyb.cwc.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 零件配方工具类——库存检查、材料扣除、ItemStack 创建。
 * 配方数据来自 PartDef.recipes()（JSON 内嵌）。
 */
public final class PartRecipes {

    private PartRecipes() {}

    /** 将 IngredientDef 列表（含 null）转为 ItemStack 列表（含 EMPTY） */
    public static List<ItemStack> toStacks(List<IngredientDef> ingredients) {
        List<ItemStack> result = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            IngredientDef ing = (i < ingredients.size()) ? ingredients.get(i) : null;
            if (ing == null) {
                result.add(ItemStack.EMPTY);
            } else {
                result.add(ingredientToStack(ing));
            }
        }
        return result;
    }

    // ingredientToStack 方法见下方 package-private 版本

    /** 创建带 PART_IDENTITY、显示名和贴图路由的零件 ItemStack——委托 PartStacks 统一构建 */
    public static ItemStack createPartStack(Item item, String identity) {
        return PartStacks.build(item, identity, "part." + identity);
    }

    public static boolean canCraft(Player player, List<IngredientDef> ingredients) {
        Inventory inv = player.getInventory();
        for (IngredientDef ing : ingredients) {
            if (ing == null) continue;
            if (!hasEnough(inv, ingredientToStack(ing))) return false;
        }
        return true;
    }

    public static void consumeMaterials(Player player, List<IngredientDef> ingredients) {
        consumeMaterials(player, ingredients, 1);
    }

    /** 批量消耗材料，count 为制造件数 */
    public static void consumeMaterials(Player player, List<IngredientDef> ingredients, int count) {
        Inventory inv = player.getInventory();
        for (IngredientDef ing : ingredients) {
            if (ing == null) continue;
            ItemStack needed = ingredientToStack(ing);
            needed.setCount(needed.getCount() * count);
            consume(inv, needed);
        }
    }

    /** 将 IngredientDef 转为 ItemStack */
    public static ItemStack ingredientToStack(IngredientDef ing) {
        if (ing.item() == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ing.item()));
        return new ItemStack(item != null ? item : net.minecraft.world.item.Items.BARRIER, ing.count());
    }

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
