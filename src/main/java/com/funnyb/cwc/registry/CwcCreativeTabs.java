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
 * 在创造模式物品栏中新增"冷兵器工艺"标签页，方便测试时快速获取 CWC 物品。
 */
public class CwcCreativeTabs {

    /** 创造标签页 DeferredRegister */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ColdWeaponCraftsmanship.MODID);

    /** CWC 主标签页——图标为制造台，列出所有 CWC 物品 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.coldweaponcraftsmanship"))
                    .icon(() -> new ItemStack(CwcItems.PART_CRAFTING_TABLE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(CwcItems.PART_CRAFTING_TABLE_ITEM.get());
                        output.accept(CwcItems.ASSEMBLY_TABLE_ITEM.get());
                        // 零件与手柄没有原版配方（只能靠制造台现造），放进创造栏方便测试。
                        // 必须逐个给出**带 PART_IDENTITY 的具体零件**——旧实现是直接
                        // accept(PART.get()) / accept(HANDLE_PART.get())，那两件连身份都没有：
                        // 渲染不出任何东西，也不能当装配底座用（BUG-017）。
                        // 承载物品（HANDLE_PART / PART）由 PartStacks.itemFor 按类型推导，不在这里判定。
                        //
                        // 注意：零件表由服务端在 ServerStartingEvent 填充，而本回调在客户端跑。
                        // 单人（集成服务器）两端共用同一份静态注册表所以正常；专用服务器上客户端这份
                        // 是空的，那种场景下这里只会出现两个工作方块（已知限制，属多人架构问题）。
                        // 零件定义里没有 id 字段（id 是注册表键），所以遍历 id→定义；partMap() 已按 id 排序
                        PartRegistry.partMap().forEach((id, def) -> output.accept(PartStacks.partIcon(id.toString())));
                    })
                    .build());

    /** 将创造标签页注册器绑定到模组事件总线 */
    public static void init(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
