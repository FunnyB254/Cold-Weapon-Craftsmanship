package com.funnyb.cwc.item;

import com.funnyb.cwc.crafting.AssemblyTree;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.crafting.WeaponStats;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * CWC 组装武器底座（{@link CwcItems#HANDLE_PART}）。
 * 职责只剩三件：双手武器右键格挡、武器身份的判定（双手/短刀/普攻方式）、属性按需推导。
 * 攻击行为（主手强制冷却、副手短刀出刀）统一走 {@link com.funnyb.cwc.combat.CwcCombat} 管线。
 * <p>
 * 身份与派生量全部由 {@link AssemblyTree} 提供——一次遍历、槽位声明序。此前 {@code hasOffhandAttack} /
 * {@code findAttackStyle} 是与 {@code WeaponStats} 并行的两份递归，遍历顺序还不一致，现已合并。
 */
public class CwcWeapon extends TieredItem {

    public CwcWeapon(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /**
     * 属性从装配树按需推导——**这是 {@code ATTRIBUTE_MODIFIERS} 组件的替代品**。
     * <p>
     * NeoForge 的 {@code ItemStack.getAttributeModifiers()} 在组件为空时正好回落到本重载
     * （{@code IItemStackExtension.getAttributeModifiers}），所以只要不写组件，重写这里就够了。
     * 属性不再物化的收益见 {@link WeaponStats}。
     */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return WeaponStats.deriveModifiers(stack);
    }

    /**
     * 耐久自愈——见 {@link WeaponStats#syncDurability}。
     * <p>
     * 属性改成按需推导后，耐久是**唯一**仍然物化的量（它推导不了），因此仍依赖"有人写过组件"。
     * 而唯一的写点是装配台，于是非装配台路径产出的武器（{@code /give}、其他模组、将来的数据包配方）
     * 会没有耐久。这里每 tick 巡检一次补上；值一致时 {@code syncDurability} 直接返回、不碰组件，
     * 所以没有同步开销。
     * <p>
     * 只覆盖玩家背包里的物品——箱子里的要等被拿起来才自愈，可接受。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;   // 服务端权威，客户端等同步
        if (!stack.has(CwcDataComponents.ASSEMBLED_SLOTS.get())) return;   // 裸手柄无事可做，省一次树遍历
        WeaponStats.syncDurability(stack);
    }

    /**
     * 右手：双手武器右键进入格挡；单手武器 pass（右键留给副手/其他物品）。
     * 副手短刀的右键攻击由客户端拦截改发 CwcOffhandAttackPacket，不进这里。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isTwoHandedStack(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 双手武器使用时长 72000 tick（持续按住 = 持续格挡） */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return isTwoHandedStack(stack) ? 72000 : 0;
    }

    /** 双手武器借原版格挡姿态动画作为视觉反馈 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return isTwoHandedStack(stack) ? UseAnim.BLOCK : UseAnim.NONE;
    }

    /** 是否双手武器底座（类型标记 twoHanded）——供格挡 use 与格挡减伤判断复用 */
    public static boolean isTwoHandedStack(ItemStack stack) {
        PartTypeDef type = AssemblyTree.of(stack).rootType();
        return type != null && type.twoHanded();
    }

    /** 是否短刀武器：底座为手柄，且装配树中存在声明 offhandAttack 的零件（副手右键出刀） */
    public static boolean isOffhandKnife(ItemStack stack) {
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return false;
        return AssemblyTree.of(stack).offhandAttack();
    }

    /**
     * 普攻方式（刃型指定）——装配树中第一个 attack 型节点的 combat.style。
     * 非手柄/未装配/查不到按 NORMAL。
     */
    public static PartTypeDef.AttackStyle attackStyle(ItemStack stack) {
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return PartTypeDef.AttackStyle.NORMAL;
        return AssemblyTree.of(stack).attackStyle();
    }

    /**
     * 格挡减伤加成（镡等零件提供）——从装配树现算，不再读 {@code BLOCK_VALUE} 组件。
     * 每次受击算一次，代价可忽略。见 {@link com.funnyb.cwc.combat.CwcCombatEvents#onHurt}。
     */
    public static float blockBonus(ItemStack stack) {
        return (float) AssemblyTree.of(stack).block();
    }
}
