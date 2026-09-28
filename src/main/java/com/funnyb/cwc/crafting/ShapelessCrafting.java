package com.funnyb.cwc.crafting;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 3×3 输入格的**无序**合成判据与扣减——材料放哪格都行，只看格子里有没有那么多。
 * <p>
 * 与 {@link PartRecipes} 那套（从**背包**里按物品数扣）是两回事，所以另立一份：
 * 工作台式的手感是"玩家把材料摆进格子"，那么判定与扣减都必须只认格子。
 * <p>
 * 数量口径按作者定的：**按物品总数**——`count: 4` 表示格子里那种物品合起来够 4 个即可
 * （堆 4 个放一格也算数），不是原版那种"一格子一件"。
 * <p>
 * 材料认的是 {@link ItemStack#isSameItemSameComponents}（与 {@code PartRecipes.hasEnough} 同一判据），
 * 比原版按物品 id 认更严；配方里只写 id，所以解析出来的都是无组件的普通物品，今天两种认法等价。
 */
public final class ShapelessCrafting {

    private ShapelessCrafting() {}

    /**
     * 这份配方被这 9 格满足了吗。
     * <p>
     * 同一种材料在配方里写了两条会**先加起来**再判——否则两条各自都能被同一堆材料满足，
     * 合起来却不够（旧实现有这个洞：判定各算各的、扣减却是累计的，于是会少扣）。
     *
     * @param grid 3×3 的九个格子，索引 0..8
     */
    public static boolean matches(List<IngredientDef> recipe, List<ItemStack> grid) {
        List<ItemStack> need = requirements(recipe);
        if (need == null) return false;                     // 坏配方（认不出的材料）→ 不匹配，别当成"无需材料"
        for (ItemStack want : need) {
            if (countOf(grid, want) < want.getCount()) return false;
        }
        return true;
    }

    /**
     * 扣掉这一炉的材料。**只动格子，不碰背包**；多余的留在格子里（同原版的余料）。
     * 调用前必须先 {@link #matches} 过——这里不做"够不够"的判断。
     */
    public static void consume(List<IngredientDef> recipe, List<ItemStack> grid) {
        List<ItemStack> need = requirements(recipe);
        if (need == null) return;
        for (ItemStack want : need) {
            int remaining = want.getCount();
            for (ItemStack slot : grid) {
                if (remaining <= 0) break;
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, want)) continue;
                int take = Math.min(remaining, slot.getCount());
                slot.shrink(take);
                remaining -= take;
            }
        }
    }

    /**
     * 配方归并成"这种材料一共要几个"；材料认不出来时返回 null 表示坏配方。
     * <p>
     * 归并靠逐个比对（{@code ItemStack} 没有值语义的 equals，当不了 Map 的键）。配方至多几条，够用。
     */
    private static List<ItemStack> requirements(List<IngredientDef> recipe) {
        List<ItemStack> need = new ArrayList<>(recipe.size());
        for (IngredientDef ing : recipe) {
            if (ing == null || ing.isNone()) continue;      // 空对象 = 这一格不要求材料
            ItemStack one = PartRecipes.ingredientToStack(ing);
            if (one.isEmpty()) return null;                 // 认不出的物品 id = 坏配方
            boolean merged = false;
            for (ItemStack already : need) {
                if (ItemStack.isSameItemSameComponents(already, one)) {
                    already.grow(one.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) need.add(one);
        }
        return need;
    }

    /** 格子里这种物品的总数 */
    private static int countOf(List<ItemStack> grid, ItemStack want) {
        int total = 0;
        for (ItemStack slot : grid) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, want)) total += slot.getCount();
        }
        return total;
    }
}
