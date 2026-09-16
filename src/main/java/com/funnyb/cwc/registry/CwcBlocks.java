package com.funnyb.cwc.registry;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.block.WorkTableBlock;
import com.funnyb.cwc.menu.AssemblingMenu;
import com.funnyb.cwc.menu.CraftingMenu;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * CWC 方块注册中心。
 * <p>
 * 注册器 {@link ColdWeaponCraftsmanship#BLOCKS} 已在主类声明并绑定到模组总线，此处只管填内容。
 * 方块物品（BlockItem）由 {@link CwcItems} 用 {@code registerSimpleBlockItem} 生成。
 */
public class CwcBlocks {

    /** 工作方块通用属性——木质手感的工作台 */
    private static final BlockBehaviour.Properties TABLE_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5F)
            .sound(SoundType.WOOD);

    /** 制造台——右键打开零件制造界面 */
    public static final DeferredBlock<WorkTableBlock> PART_CRAFTING_TABLE =
            ColdWeaponCraftsmanship.BLOCKS.registerBlock("part_crafting_table",
                    props -> new WorkTableBlock(CraftingMenu::new, CraftingMenu.TITLE, props),
                    TABLE_PROPERTIES);

    /** 装配台——右键打开武器装配界面 */
    public static final DeferredBlock<WorkTableBlock> ASSEMBLY_TABLE =
            ColdWeaponCraftsmanship.BLOCKS.registerBlock("assembly_table",
                    props -> new WorkTableBlock(AssemblingMenu::new, AssemblingMenu.TITLE, props),
                    TABLE_PROPERTIES);

    private CwcBlocks() {}
}
