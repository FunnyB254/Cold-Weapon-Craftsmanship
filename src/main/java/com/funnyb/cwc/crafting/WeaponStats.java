package com.funnyb.cwc.crafting;

import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 武器属性——**从装配树按需推导**，并把少数无法推导的量同步到组件上。
 * <p>
 * 2026-09-17 起属性不再物化：原先 {@code apply} 会把 {@code ATTRIBUTE_MODIFIERS}（伤害/攻速/击退/交互距离）
 * 写进物品栈，于是"属性 = 有没有经过装配台"——创造栏、{@code /give}、其他模组、旧存档产出的武器全是 0 且不报错；
 * 更要紧的是原版属性系统**无条件读取主手物品的 {@code ATTRIBUTE_MODIFIERS}，不检查它是不是武器**，
 * 这就是 BUG-005（裸刃打人）的根。改为推导后这条路径从结构上不存在了。
 * <p>
 * 现在物化量只剩一个：<b>耐久上限</b>。它没法按需——{@code ItemStack.getMaxDamage()} 只读组件，
 * {@code Item} 拦不住。格挡减伤也改为受击时现算（见 {@code CwcCombatEvents.onHurt}）。
 * <p>
 * 承载方式由 {@code CwcWeapon.getDefaultAttributeModifiers(ItemStack)} 调用 {@link #deriveModifiers}——
 * NeoForge 的 {@code ItemStack.getAttributeModifiers()} 在组件为空时正好回落到这个重载，无需自己写分发。
 */
public final class WeaponStats {

    /**
     * modifier 稳定 id——同一 id 在属性表里整体替换，避免叠加。
     * <p>
     * 伤害与攻速**刻意复用原版的 {@link Item#BASE_ATTACK_DAMAGE_ID} 与 {@link Item#BASE_ATTACK_SPEED_ID}**：
     * 原版 tooltip 对这两个 id 有特判（见 {@code ItemStack.addModifierTooltip}）——它会**把玩家基础值加进来
     * 显示"总值"**、不带正负号、用深绿字，与拿在手里的原版武器观感一致。
     * <p>
     * 用自定义 id 则会落到通用分支：显示**增量**并按正负染色，于是出现
     * "＋3 伤害（蓝）/ −2.4 攻速（红）"这种看起来像"惩罚"的显示（负的攻速增量尤其吓人，
     * 而它其实只是"比空手慢"的意思）。
     * <p>
     * **这只是显示层的差别**：数值、运算方式、生效逻辑一概不变。
     * （特判要求 tooltip 带玩家上下文；没有玩家时会回落成增量形式，属正常。）
     */
    private static final ResourceLocation DMG_ID = Item.BASE_ATTACK_DAMAGE_ID;
    private static final ResourceLocation SPD_ID = Item.BASE_ATTACK_SPEED_ID;
    /** 击退与交互距离**没有原版对应 id**，保持自定义——它们本就该按"额外加成"显示 */
    private static final ResourceLocation KB_ID =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "assembled_knockback");
    private static final ResourceLocation REACH_ID =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "assembled_reach");

    private WeaponStats() {}

    /**
     * 按装配树现算主手属性 modifier——**不落组件**。
     * <p>
     * 伤害与攻速**无条件写**（值是 0 也写）。这两笔的 id 是原版 base id，NeoForge 的 tooltip 对 base
     * modifier 不做 0 值过滤、还会把玩家基础值加进去显示**总值**，所以裸手柄也会显示
     * "1 攻击伤害 / 4 攻击速度"——那正是它拿在手里的真实数值（根节点自身的数值也参与聚合，
     * 见 {@link AssemblyTree}）。
     * <p>
     * <b>2026-09-27 拆掉的那道过滤</b>：这里原先第一句是"没装任何零件就返回
     * {@link ItemAttributeModifiers#EMPTY}"。而 {@code AssemblyTree} 一直在算根节点自己的数值，
     * 于是"手柄自己带数值"成了算了却没人读的死数——**既不显示、也不生效、还不报错**。
     * 作者定：拆掉，让根手柄的数值和装了零件的一样活。
     * <p>
     * <b>不加缓存。</b>装配树最多几个节点，而这个方法的调用点只有三处：装备变更（属性表重建）、
     * 伤害结算、tooltip 渲染。tooltip 虽然是每帧重建，代价也就是几十次 map 查找。
     */
    public static ItemAttributeModifiers deriveModifiers(ItemStack stack) {
        AssemblyTree tree = AssemblyTree.of(stack);

        var builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(DMG_ID, tree.damage(), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                // 攻速：零件给的**绝对贡献**（最终攻速 = 玩家基础 4.0 + 这里）。模组不再固定偏移——
                // 那笔"装好零件就 −2.4"过去藏在代码里，于是"手柄/刃谁决定攻速"在数据上无从表达；
                // 现在它由刃的 speed 承担（见 docs/part-format.md 的属性键表）。**0 也写**（理由同伤害）。
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(SPD_ID, tree.speed(), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        // 这两笔**非 0 才写**，与上面两笔相反：它们没有原版 base id（走通用分支），NeoForge 会把 0 值的
        // 通用 modifier 整个过滤掉——写了既不显示，又白白塞进玩家属性表
        if (tree.knockback() != 0.0) {
            builder.add(Attributes.ATTACK_KNOCKBACK,
                    new AttributeModifier(KB_ID, tree.knockback(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        if (tree.reach() != 0.0) {
            builder.add(Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(REACH_ID, tree.reach(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }

    /**
     * 把耐久上限同步到组件——**这是全项目唯一的物化写入**，幂等，可安全重复调用。
     * <p>
     * <b>为什么只有耐久还物化：</b>属性已经彻底改成按需推导（{@link #deriveModifiers}），但耐久推导不了
     * ——{@code ItemStack.getMaxDamage()} 只读组件、{@code Item} 拦不住。而原先它的唯一写点是装配台，
     * 于是"非装配台路径产出的武器没有耐久"（不可损坏、无耐久条、{@code hurtAndBreak} 空转）。
     * 现在由 {@code CwcWeapon.inventoryTick} 每 tick 巡检自愈补上这一半。
     * <p>
     * <b>值相等时不碰组件</b>，所以每 tick 调用也不会产生无谓的同步。
     * <p>
     * 非手柄底座（子装配体 / 裸零件）直接返回：只有 {@link CwcItems#HANDLE_PART} 是武器。
     * 这也正是 BUG-005（裸刃打人）为何从结构上不再可能——属性不再落组件，就没有"某个零件身上带着
     * 武器属性、被原版属性系统无条件读走"这条路了，不再需要"清掉残留组件"的补丁。
     */
    public static void syncDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return;

        AssemblyTree tree = AssemblyTree.of(stack);
        // 只看聚合结果里有没有耐久，不再要求"装过零件"——手柄自身（换掉 cwc:handle 那个不带 data 的形状
        // 之后）也能带 durability，2026-09-27 起那笔数与装了零件的一样生效
        int expected = tree.durability() > 0
                ? Math.max(1, (int) Math.round(tree.durability()))
                : 0;

        if (expected <= 0) {
            // 整棵装配树（含根）都不贡献耐久 → 回落到**物品自带**的上限（底座自己是个会被磨损的物品）。
            // 这里**不能**用 remove()：那会在补丁里写下"移除"、把物品原型上的值也盖掉，于是上限变 0、
            // 裸手柄变得不可损坏——而它与"空手"的差别本来只该是"它有耐久"。写成原型值即可解开这层遮蔽。
            int base = stack.getPrototype().getOrDefault(DataComponents.MAX_DAMAGE, 0);
            if (base <= 0) return;
            if (stack.getOrDefault(DataComponents.MAX_DAMAGE, -1) != base) {
                stack.set(DataComponents.MAX_DAMAGE, base);
            }
            clampDamage(stack, base);
            return;
        }
        if (stack.getOrDefault(DataComponents.MAX_DAMAGE, 0) == expected) return;   // 已一致：不碰组件

        stack.set(DataComponents.MAX_DAMAGE, expected);
        clampDamage(stack, expected);
    }

    /**
     * 拆掉贡献耐久的零件后，已累积的损耗可能超过新的上限——夹一下。
     * 不夹的话 {@code getBarWidth()} 会算出负宽度（它没有 clamp），耐久条渲染会出问题。
     */
    private static void clampDamage(ItemStack stack, int maxDamage) {
        if (stack.getDamageValue() > maxDamage) {
            stack.set(DataComponents.DAMAGE, maxDamage);
        }
    }
}
