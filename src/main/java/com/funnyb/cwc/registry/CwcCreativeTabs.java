package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

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

    /** CWC 主标签页——图标为创造零件之星，列出所有 CWC 物品 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.coldweaponcraftsmanship"))
                    .icon(() -> new ItemStack(CwcItems.CREATIVE_PART_STAR.get()))
                    .displayItems((params, output) -> {
                        output.accept(CwcItems.CREATIVE_PART_STAR.get());
                    })
                    .build());

    /** 将创造标签页注册器绑定到模组事件总线 */
    public static void init(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
