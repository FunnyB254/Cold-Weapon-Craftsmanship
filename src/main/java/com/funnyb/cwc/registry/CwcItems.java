package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.item.CwcWeapon;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
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

    /** 制造台方块物品——右键打开零件制造界面 */
    public static final DeferredItem<BlockItem> PART_CRAFTING_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem(CwcBlocks.PART_CRAFTING_TABLE);

    /** 装配台方块物品——右键打开武器装配界面 */
    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem(CwcBlocks.ASSEMBLY_TABLE);

    /** 刃/镡/饰类零件——可被插入其他零件 */
    public static final DeferredItem<Item> PART = ITEMS.register("part",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** 手柄类零件——接收其他零件，作为组装武器的属性聚合终点 */
    public static final DeferredItem<Item> HANDLE_PART = ITEMS.register("handle_part",
            () -> new CwcWeapon(Tiers.IRON, new Item.Properties().stacksTo(1)));

    /** 副手短刀出刀冷却的专用键——独立物品冷却，与 HANDLE_PART 完全隔离，不影响主手武器的冷却/格挡 */
    public static final DeferredItem<Item> OFFHAND_COOLDOWN = ITEMS.register("offhand_cooldown",
            () -> new Item(new Item.Properties()));

    /** 将物品注册器绑定到模组事件总线 */
    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
