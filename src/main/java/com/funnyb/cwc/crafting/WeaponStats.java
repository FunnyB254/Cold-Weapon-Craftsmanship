package com.funnyb.cwc.crafting;

import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Map;

/**
 * 武器属性聚合——沿 ASSEMBLED_SLOTS **递归遍历整棵装配树**，把每个节点自身的属性
 * 按"它所在槽位"的权重加权累加，写入底座武器的 ATTRIBUTE_MODIFIERS（攻击伤害/攻击速度）
 * 与 MAX_DAMAGE（耐久），并聚合格挡减伤 BLOCK_VALUE。
 * <p>
 * 递归是必须的：渲染器 {@code AssembledWeaponRenderer.collectChildren} 本来就递归任意深度，
 * 若聚合只看顶层，"刃装镡"这类子装配体再插进手柄时，镡会被画出来但数值完全不生效
 * （看得见却不生效）。根节点自身也计入，权重固定 1.0 —— 底座没有"所在槽位"。
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

    /** 一趟遍历同时收集的全部聚合量 */
    private static final class Accumulator {
        double damage;
        double speed;
        double durability;
        double block;
        double reach;
        double knockback;
        /** reach/knockback 只取遇到的第一个 attack 型节点，取到后不再覆盖 */
        boolean attackFound;
    }

    /**
     * 按底座武器的整棵装配树重算属性并写入组件（幂等）。
     * 未装配任何零件时移除属性组件，回落物品默认属性——即"属性只在装配后产生"。
     * <p>
     * **只有底座是 {@link CwcItems#HANDLE_PART} 时才写入**。属性组件一旦写上去，原版属性系统就会
     * 把它无条件计入"手持该物品"的玩家属性——它不检查这物品是不是武器。而装配台底座槽接受任意
     * 零件（为了拼"刃+镡"这类子装配体），若照写，这把刃拿到主手左键时就会经原版
     * {@code Player.attack} 打出武器伤害（{@code CwcCombatEvents.onAttack} 只拦 HANDLE_PART，
     * 非底座一律放行原版）。
     * <p>
     * 不写入**不影响最终数值**：递归聚合读的是 {@link PartRegistry} 里的 {@link PartDef#data()}，
     * 与物品栈上有没有属性组件无关——子装配体插进手柄后该贡献的一分不少。
     */
    public static void apply(ItemStack base) {
        if (base.isEmpty()) return;
        PartDef baseDef = defOf(base);
        if (baseDef == null) return;   // 非本模组零件：完全不碰

        // 不是手柄底座（子装配体/裸零件）→ 清掉可能残留的属性，绝不写入
        if (base.getItem() != CwcItems.HANDLE_PART.get()) {
            clearStats(base);
            return;
        }

        Map<String, ItemStack> assembled = base.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (assembled == null || assembled.isEmpty()) {
            clearStats(base);
            return;
        }

        Accumulator acc = new Accumulator();
        accumulate(base, null, acc);   // 根节点无父槽位，权重 1.0

        var builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(DMG_ID, acc.damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(SPD_ID, BASE_ATTACK_SPEED + acc.speed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        // 加成非 0 才写入，避免无刃/平衡刃武器带多余 modifier
        if (acc.knockback != 0) {
            builder.add(Attributes.ATTACK_KNOCKBACK,
                    new AttributeModifier(KB_ID, acc.knockback, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        if (acc.reach != 0) {
            builder.add(Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(REACH_ID, acc.reach, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        base.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());

        base.set(CwcDataComponents.BLOCK_VALUE.get(), (float) acc.block);

        if (acc.durability > 0) {
            base.set(DataComponents.MAX_DAMAGE, (int) Math.max(1, Math.round(acc.durability)));
        } else {
            base.remove(DataComponents.MAX_DAMAGE);
        }
    }

    /**
     * 移除本模组写入的全部属性组件——非手柄底座的物品不该带任何武器属性。
     * 组件不存在时 {@code remove} 是空操作，可安全重复调用。
     */
    private static void clearStats(ItemStack stack) {
        stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        stack.remove(DataComponents.MAX_DAMAGE);
        stack.remove(CwcDataComponents.BLOCK_VALUE.get());
    }

    /**
     * 递归累加：先计入本节点自身属性，再按各子槽位的权重递归子件。
     *
     * @param slot 本节点所在的槽位定义，用于取该槽的属性权重；**根节点传 null**（权重按 1.0）
     */
    private static void accumulate(ItemStack node, PartTypeDef.SlotDef slot, Accumulator acc) {
        PartDef def = defOf(node);
        if (def == null) return;
        PartTypeDef type = PartRegistry.getTypeDef(def.typeId());
        if (type == null) return;

        double wDamage = weight(slot, "damage");
        double wSpeed = weight(slot, "speed");
        double wDurability = weight(slot, "durability");
        acc.damage += attr(def, "damage") * wDamage;
        acc.speed += attr(def, "speed") * wSpeed;
        acc.durability += attr(def, "durability") * wDurability;

        String partType = type.data().get("type");
        // 格挡减伤不走槽位权重（与原实现一致，裸加）
        if ("guard".equals(partType)) {
            acc.block += attr(def, "block");
        }
        if (!acc.attackFound && "attack".equals(partType)) {
            acc.reach = type.combatReach();
            acc.knockback = type.combatKnockback();
            acc.attackFound = true;
        }

        Map<String, ItemStack> children = node.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (children == null || children.isEmpty()) return;
        for (Map.Entry<String, ItemStack> entry : children.entrySet()) {
            PartTypeDef.SlotDef slotDef = findSlot(type, entry.getKey());
            if (slotDef == null) continue;   // 槽位未在父类型中声明：孤儿条目，整支跳过
            accumulate(entry.getValue(), slotDef, acc);
        }
    }

    /** 槽位权重；根节点（slot == null）按 1.0 全量计入 */
    private static double weight(PartTypeDef.SlotDef slot, String attribute) {
        return slot == null ? 1.0 : slot.weight(attribute);
    }

    /** 读取零件 data 的属性值：数值直接用，金属公式只用 base，缺失按 0 */
    private static double attr(PartDef def, String key) {
        Object v = def.data().get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof MetalParser.Formula f) return f.base;
        return 0.0;
    }

    /** 读某栈的零件定义，无 PART_IDENTITY 或查不到返回 null */
    private static PartDef defOf(ItemStack stack) {
        String id = stack.get(CwcDataComponents.PART_IDENTITY.get());
        return id == null ? null : PartRegistry.getPartDef(id);
    }

    /** 按槽位名在类型定义中查找 SlotDef，找不到返回 null */
    private static PartTypeDef.SlotDef findSlot(PartTypeDef typeDef, String slotName) {
        for (PartTypeDef.SlotDef s : typeDef.slots()) {
            if (s.name().equals(slotName)) return s;
        }
        return null;
    }
}
