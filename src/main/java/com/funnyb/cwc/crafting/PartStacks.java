package com.funnyb.cwc.crafting;

import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 零件/类型图标 ItemStack 构建工具——统一设置 PART_IDENTITY 和显示名。
 * 制造界面、制造输出等所有需要"零件物品图标"的地方都从这里构建，避免重复拼装。
 * 贴图路由由 {@link com.funnyb.cwc.client.renderer.AssembledWeaponRenderer} 依据 PART_IDENTITY 推导，
 * 不再使用 CUSTOM_MODEL_DATA（旧哈希索引系统已退役）。
 */
public final class PartStacks {

    private PartStacks() {}

    /** 零件类型图标——PART 物品 + 类型身份 + 类型名 */
    public static ItemStack typeIcon(String typeId) {
        return build(CwcItems.PART.get(), typeId, "type." + typeId);
    }

    /** 零件图标——PART 物品 + 零件身份 + 零件名 */
    public static ItemStack partIcon(String partId) {
        return build(CwcItems.PART.get(), partId, "part." + partId);
    }

    /** 通用构建：物品 + 身份 + 名称 key */
    public static ItemStack build(Item item, String id, String nameKey) {
        ItemStack stack = new ItemStack(item);
        stack.set(CwcDataComponents.PART_IDENTITY.get(), id);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey));
        return stack;
    }
}
