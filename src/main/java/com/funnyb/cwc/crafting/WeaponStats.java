package com.funnyb.cwc.crafting;

import com.funnyb.cwc.registry.CwcDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Map;

/**
 * 武器属性聚合——按 ASSEMBLED_SLOTS 把已装零件属性按槽位权值加权累加，
 * 写入底座武器的 ATTRIBUTE_MODIFIERS（攻击伤害/攻击速度）和 MAX_DAMAGE（耐久）。
 */
public final class WeaponStats {

    /** 攻击速度基础 modifier（与原版剑一致，实际攻速 = 4 + modifier） */
    private static final double BASE_ATTACK_SPEED = -2.4;

    /** modifier 稳定 id——同一 id 重算时整体替换，避免叠加 */
    private static final ResourceLocation DMG_ID =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "assembled_damage");
    private static final ResourceLocation SPD_ID =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "assembled_speed");
    private static final ResourceLocation KB_ID =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "assembled_knockback");
    private static final ResourceLocation REACH_ID =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "assembled_reach");

    private WeaponStats() {}

    /**
     * 按底座武器的 ASSEMBLED_SLOTS 重算属性并写入组件（幂等）。
     * 无已装零件时移除属性组件，回落物品默认属性。
     */
    public static void apply(ItemStack base) {
        if (base.isEmpty()) return;
        String identity = base.get(CwcDataComponents.PART_IDENTITY.get());
        if (identity == null) return;
        PartDef partDef = PartRegistry.getPartDef(identity);
        if (partDef == null) return;
        PartTypeDef typeDef = PartRegistry.getTypeDef(partDef.typeId());
        if (typeDef == null) return;

        Map<String, ItemStack> assembled = base.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (assembled == null || assembled.isEmpty()) {
            base.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            base.remove(DataComponents.MAX_DAMAGE);
            base.remove(CwcDataComponents.BLOCK_VALUE.get());
            return;
        }

        double damage = 0;
        double speed = 0;
        double durability = 0;
        for (Map.Entry<String, ItemStack> entry : assembled.entrySet()) {
            String partId = entry.getValue().get(CwcDataComponents.PART_IDENTITY.get());
            if (partId == null) continue;
            PartDef def = PartRegistry.getPartDef(partId);
            PartTypeDef.SlotDef slotDef = findSlot(typeDef, entry.getKey());
            if (def == null || slotDef == null) continue;
            damage += attr(def, "damage") * slotDef.weight("damage");
            speed += attr(def, "speed") * slotDef.weight("speed");
            durability += attr(def, "durability") * slotDef.weight("durability");
        }

        // 找刃型（type == "attack" 的已装零件）的战斗特征，写入攻击范围/击退加成
        double reach = 0;
        double knockback = 0;
        for (Map.Entry<String, ItemStack> entry : assembled.entrySet()) {
            String partId = entry.getValue().get(CwcDataComponents.PART_IDENTITY.get());
            if (partId == null) continue;
            PartDef def = PartRegistry.getPartDef(partId);
            if (def == null) continue;
            PartTypeDef ptype = PartRegistry.getTypeDef(def.typeId());
            if (ptype == null) continue;
            if ("attack".equals(ptype.data().get("type"))) {
                reach = ptype.combatReach();
                knockback = ptype.combatKnockback();
                break;
            }
        }

        var builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(DMG_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(SPD_ID, BASE_ATTACK_SPEED + speed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        // 加成非 0 才写入，避免无刃/平衡刃武器带多余 modifier
        if (knockback != 0) {
            builder.add(Attributes.ATTACK_KNOCKBACK,
                    new AttributeModifier(KB_ID, knockback, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        if (reach != 0) {
            builder.add(Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        ItemAttributeModifiers modifiers = builder.build();
        base.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);

        // 聚合镡（guard）的格挡减伤加成，写入 BLOCK_VALUE（双手武器格挡时使用）
        float block = 0;
        for (Map.Entry<String, ItemStack> entry : assembled.entrySet()) {
            String partId = entry.getValue().get(CwcDataComponents.PART_IDENTITY.get());
            if (partId == null) continue;
            PartDef def = PartRegistry.getPartDef(partId);
            if (def == null) continue;
            PartTypeDef ptype = PartRegistry.getTypeDef(def.typeId());
            if (ptype == null) continue;
            if ("guard".equals(ptype.data().get("type"))) {
                block += (float) attr(def, "block");
            }
        }
        base.set(CwcDataComponents.BLOCK_VALUE.get(), block);

        if (durability > 0) {
            base.set(DataComponents.MAX_DAMAGE, (int) Math.max(1, Math.round(durability)));
        } else {
            base.remove(DataComponents.MAX_DAMAGE);
        }
    }

    /** 读取零件 data 的属性值：数值直接用，金属公式只用 base，缺失按 0 */
    private static double attr(PartDef def, String key) {
        Object v = def.data().get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof MetalParser.Formula f) return f.base;
        return 0.0;
    }

    /** 按槽位名在类型定义中查找 SlotDef，找不到返回 null */
    private static PartTypeDef.SlotDef findSlot(PartTypeDef typeDef, String slotName) {
        for (PartTypeDef.SlotDef s : typeDef.slots()) {
            if (s.name().equals(slotName)) return s;
        }
        return null;
    }
}
