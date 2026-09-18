package com.funnyb.cwc.crafting;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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
            if (ing == null || ing.isNone()) {
                result.add(ItemStack.EMPTY);
            } else {
                result.add(ingredientToStack(ing));
            }
        }
        return result;
    }

    // ingredientToStack 方法见下方 package-private 版本

    public static boolean canCraft(Player player, List<IngredientDef> ingredients) {
        Inventory inv = player.getInventory();
        for (IngredientDef ing : ingredients) {
            // JSON 里的 null 占位 = 该格无要求；codec 把它统一成 IngredientDef.NONE 哨兵
            if (ing == null || ing.isNone()) continue;
            ItemStack needed = ingredientToStack(ing);
            if (needed.isEmpty()) return false;                 // 材料解析失败 = 配方损坏，拒绝制造
            if (!hasEnough(inv, needed)) return false;
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
            if (ing == null || ing.isNone()) continue;
            ItemStack needed = ingredientToStack(ing);
            needed.setCount(needed.getCount() * count);
            consume(inv, needed);
        }
    }

    /**
     * 将 IngredientDef 转为 ItemStack；解析失败一律返回 {@link ItemStack#EMPTY}。
     * <p>
     * 调用方必须把 EMPTY 当作"配方损坏"处理，**不能**当成"无材料要求"：
     * {@code BuiltInRegistries.ITEM} 是 defaulted 注册表（默认值 minecraft:air），
     * 未知 id 返回 AIR 而非 null，直接构造只会得到空栈，而 hasEnough/consume 对空栈是放行的
     * —— 那等于配方不需要任何材料。同理非法 id 字符串（含空格/大写）会抛
     * {@link ResourceLocationException}，也必须在此拦下，否则会冒到数据包处理器导致玩家断连。
     */
    public static ItemStack ingredientToStack(IngredientDef ing) {
        if (ing.isNone()) return ItemStack.EMPTY;
        ResourceLocation loc;
        try {
            loc = ResourceLocation.parse(ing.itemId());
        } catch (ResourceLocationException e) {
            ColdWeaponCraftsmanship.LOGGER.error(
                    "Invalid item id '{}' in recipe ingredient, treating recipe as broken", ing.item());
            return ItemStack.EMPTY;
        }
        if (!BuiltInRegistries.ITEM.containsKey(loc)) {
            ColdWeaponCraftsmanship.LOGGER.error(
                    "Unknown item '{}' in recipe ingredient, treating recipe as broken", loc);
            return ItemStack.EMPTY;
        }
        // count <= 0 同样会得到空栈（= 免费），至少按 1 计
        return new ItemStack(BuiltInRegistries.ITEM.get(loc), Math.max(1, ing.count()));
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
