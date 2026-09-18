package com.funnyb.cwc.crafting;

import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 零件/类型图标 ItemStack 构建工具——统一设置 PART_IDENTITY 和显示名。
 * 制造界面、制造输出、装配台显示、渲染器等所有需要"零件物品"的地方都从这里构建，避免重复拼装。
 * <p>
 * 贴图路由由 {@link com.funnyb.cwc.client.renderer.AssembledWeaponRenderer} 依据 PART_IDENTITY 推导。
 * <p>
 * 零件定义里**没有 id 字段**（id 是注册表键），所以"这个 id 该用哪个物品承载、显示名是什么"必须由这里
 * 统一推导——见 {@link #itemFor}。
 */
public final class PartStacks {

    private PartStacks() {}

    /**
     * 零件该用哪个物品承载：{@link CwcItems#HANDLE_PART}（可继续装零件、属性聚合终点）还是
     * {@link CwcItems#PART}（普通零件）。
     * <p>
     * **判据是类型的 {@link PartTypeDef#role()}（data 为空即 handle_part），不是 id 里有没有 "handle" 字样。**
     * 旧实现是 {@code typeId().contains("handle")}，与 {@code WeaponStats}／{@code CwcWeapon} 用的
     * {@code getItem() == HANDLE_PART} 不是同一套判据：今天恰好一致（只有两个类型含 "handle"），
     * 但只要加一个 {@code cwc:handle_guard} 之类，制造台就会产出"HANDLE_PART 物品 + 非手柄定义的零件"
     * 的怪东西——被当底座写属性，行为却由 definition 决定。故统一到此处。
     * <p>
     * 查不到定义（未装配、注册表未就绪、旧存档的非法 id）按普通零件处理。
     */
    public static Item itemFor(String partId) {
        PartDef def = PartRegistry.getPartDef(partId);
        if (def == null) return CwcItems.PART.get();
        PartTypeDef type = PartRegistry.getTypeDef(def.type());
        boolean isHandle = type != null && "handle_part".equals(type.role());
        return isHandle ? CwcItems.HANDLE_PART.get() : CwcItems.PART.get();
    }

    /** 零件类型图标——PART 物品 + 类型身份 + 类型名。仅用于列表里的分类图标，固定用 PART */
    public static ItemStack typeIcon(String typeId) {
        return build(CwcItems.PART.get(), typeId, typeLangKey(typeId));
    }

    /** 零件图标——按 id 推导承载物品 + 零件身份 + 零件名 */
    public static ItemStack partIcon(String partId) {
        return build(itemFor(partId), partId, partLangKey(partId));
    }

    /** 通用构建：物品 + 身份 + 名称 key */
    public static ItemStack build(Item item, String id, String nameKey) {
        ItemStack stack = new ItemStack(item);
        stack.set(CwcDataComponents.PART_IDENTITY.get(), id);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey));
        return stack;
    }

    /**
     * 零件 id → 语言键。
     * <p>
     * 合法 id 用带命名空间的新格式（{@link PartDef#langKey}）；非法 id —— 只可能来自旧存档里点号格式的
     * {@code PART_IDENTITY} —— 回落到旧式拼接，至少不至于显示成裸 key。
     */
    private static String partLangKey(String partId) {
        ResourceLocation id = parse(partId);
        return id == null ? "part." + partId : PartDef.langKey(id);
    }

    /** 类型 id → 语言键，回落规则同 {@link #partLangKey} */
    private static String typeLangKey(String typeId) {
        ResourceLocation id = parse(typeId);
        return id == null ? "type." + typeId : PartTypeDef.langKey(id);
    }

    private static ResourceLocation parse(String id) {
        if (id == null) return null;
        try {
            return ResourceLocation.parse(id);
        } catch (Exception e) {
            return null;
        }
    }
}
