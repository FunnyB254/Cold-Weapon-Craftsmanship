package com.funnyb.cwc.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;

/**
 * CWC 组装武器——继承 TieredItem。
 * 组装完成时，各零件属性通过 ItemAttributeModifiers 动态写入，因此构造时不设固定属性。
 */
public class CwcWeapon extends TieredItem {

    public CwcWeapon(Tier tier, Properties properties) {
        super(tier, properties);
    }
}
