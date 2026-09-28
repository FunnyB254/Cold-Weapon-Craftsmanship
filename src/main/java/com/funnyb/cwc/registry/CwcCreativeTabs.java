package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartStacks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * CWC 创造模式标签页注册中心。
 * 在创造模式物品栏中新增两个标签页，方便测试时快速获取 CWC 物品：
 * {@link #MAIN_TAB}（工作方块）与 {@link #PARTS_TAB}（零件）。
 */
public class CwcCreativeTabs {

    /** 创造标签页 DeferredRegister */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ColdWeaponCraftsmanship.MODID);

    /** CWC 主标签页——图标为制造台，列出两个工作方块 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.coldweaponcraftsmanship"))
                    .icon(() -> new ItemStack(CwcItems.PART_CRAFTING_TABLE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(CwcItems.PART_CRAFTING_TABLE_ITEM.get());
                        output.accept(CwcItems.ASSEMBLY_TABLE_ITEM.get());
                    })
                    .build());

    /** CWC 零件标签页——图标取零件表里第一件零件（见 {@link #partsTabIcon()}） */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PARTS_TAB = CREATIVE_MODE_TABS.register(
            "parts",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.coldweaponcraftsmanship.parts"))
                    .icon(CwcCreativeTabs::partsTabIcon)
                    .displayItems((params, output) -> acceptAllParts(output))
                    .build());

    /**
     * 把零件表整表填进零件页。
     * <p>
     * 零件与手柄没有原版配方（只能靠制造台现造），放进创造栏方便测试。
     * 必须逐个给出**带 PART_IDENTITY 的具体零件**——旧实现是直接
     * accept(PART.get()) / accept(HANDLE_PART.get())，那两件连身份都没有：
     * 渲染不出任何东西，也不能当装配底座用（BUG-017）。
     * 承载物品（HANDLE_PART / PART）由 PartStacks.itemFor 按类型推导，不在这里判定。
     * <p>
     * 零件表来自 datapack registry（`cwc:part`），会随数据包同步到客户端，
     * 所以单人、多人、专用服务器三边看到的创造栏内容一致
     * （registry 迁移前的"专用服务器上客户端这份是空的"那个限制已不复存在）。
     * 零件定义里没有 id 字段（id 是注册表键），所以遍历 id→定义；partMap() 已按 id 排序
     * （排序结果即"按类型分组、组内按材质"），创造栏里的顺序因此是确定的。
     */
    private static void acceptAllParts(CreativeModeTab.Output output) {
        PartRegistry.partMap().forEach((id, def) -> output.accept(PartStacks.partIcon(id.toString())));
    }

    /**
     * 零件页图标——取零件表里第一件零件（{@code partMap()} 已按 id 排序，所以每次都是同一件：
     * {@code cwc:half_sword/copper}，即"铜手半剑刃"）。想换图标就改这里。
     * <p>
     * 图标是**惰性**取的（{@code CreativeModeTab#getIconItem} 首次调用时才跑），那时零件表已就绪；
     * 万一还没就绪（表为空）就退回裸零件物品，不让"表还没加载"变成崩溃。
     */
    private static ItemStack partsTabIcon() {
        return PartRegistry.partMap().keySet().stream()
                .findFirst()
                .map(id -> PartStacks.partIcon(id.toString()))
                .orElseGet(() -> new ItemStack(CwcItems.PART.get()));
    }

    /** 将创造标签页注册器绑定到模组事件总线 */
    public static void init(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
