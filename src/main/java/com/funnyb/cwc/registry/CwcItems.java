package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.item.CreativePartStar;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * CWC 物品注册中心。
 * 所有自定义物品在此声明并通过 {@link #init(IEventBus)} 注册到 NeoForge 事件总线。
 */
public class CwcItems {

    /** 物品 DeferredRegister，命名空间为 cwc */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ColdWeaponCraftsmanship.MODID);

    /** 创造零件之星——堆叠 1，右键打开选择界面 */
    public static final DeferredItem<Item> CREATIVE_PART_STAR = ITEMS.register("creative_part_star",
            () -> new CreativePartStar(new Item.Properties().stacksTo(1)));

    /** 刃/镡/饰类零件——可被插入其他零件 */
    public static final DeferredItem<Item> PART = ITEMS.register("part",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** 手柄类零件——接收其他零件 */
    public static final DeferredItem<Item> HANDLE_PART = ITEMS.register("handle_part",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** 将物品注册器绑定到模组事件总线 */
    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
