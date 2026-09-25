package com.funnyb.cwc.item;

import com.funnyb.cwc.combat.behavior.BehaviorField;
import com.funnyb.cwc.combat.behavior.BehaviorRegistry;
import com.funnyb.cwc.combat.behavior.BehaviorResolver;
import com.funnyb.cwc.combat.behavior.WeaponBehavior;
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
 * <p>
 * <b>它自己不再知道任何具体动作。</b>右键动作由装配树按字段与优先级选出行为（{@link BehaviorResolver}），
 * 本类只把原版 {@code Item} 的四个钩子（{@code use} / {@code getUseDuration} / {@code getUseAnimation} /
 * {@code releaseUsing}）按手转发过去——所以"这把武器能做什么"完全是数据的事。
 * <p>
 * 攻击行为（主手强制冷却、副手出刀）统一走 {@link com.funnyb.cwc.combat.CwcCombat} 管线；
 * 属性与身份判定由 {@link AssemblyTree} 提供（一次遍历、槽位声明序）。
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
     * 右键按下——交给**这只手**的字段胜出的行为。
     * <p>
     * 没有任何胜出（这一手没声明 / 冲突）时 {@code pass}，右键照旧落到原版（放方块、交互）。
     * 副手出刀的右键由客户端输入层拦截改发 CwcOffhandAttackPacket，不进这里。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PartTypeDef.BehaviorDecl decl = BehaviorResolver.declFor(stack, BehaviorField.useField(hand));
        WeaponBehavior behavior = decl == null ? null : BehaviorRegistry.get(decl.behavior());
        if (behavior == null) return InteractionResultHolder.pass(stack);   // 放行原版
        if (!behavior.onUse(new WeaponBehavior.UseContext(
                BehaviorField.useField(hand), level, player, hand, stack, decl))) {
            return InteractionResultHolder.pass(stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 保持时长——这个钩子**拿不到手**（原版签名只有物品栈与实体），故按物品栈实际在哪只手上判
     * （见 {@link BehaviorResolver#handOf}；原版把 {@code getUseItem()} 原样传进来，按实例比较是准的）。
     */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        InteractionHand hand = BehaviorResolver.handOf(entity, stack);
        PartTypeDef.BehaviorDecl decl = BehaviorResolver.declFor(stack, BehaviorField.useField(hand));
        WeaponBehavior behavior = decl == null ? null : BehaviorRegistry.get(decl.behavior());
        return behavior == null ? 0 : behavior.useDuration(stack, entity, decl);
    }

    /**
     * 保持中的姿态动画——这个钩子**连实体都没有**，所以按"主手字段优先、其次副手"取声明
     * （见 {@link BehaviorResolver#firstUseDecl}）。内置行为里只有格挡有动画、而格挡挂在
     * {@code mainHandUse} 上，故不会歧义。
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        PartTypeDef.BehaviorDecl decl = BehaviorResolver.firstUseDecl(stack);
        WeaponBehavior behavior = decl == null ? null : BehaviorRegistry.get(decl.behavior());
        return behavior == null ? UseAnim.NONE : behavior.useAnimation(stack, decl);
    }

    /**
     * 松开右键——保持型动作的释放点（蓄力弓的发射之类；格挡没有释放动作，默认实现什么都不做）。
     * <p>
     * 只认玩家：本模组的动作全部以玩家为上下文（{@code UseContext.player} 因此可以是非空的），
     * 非玩家实体拿着武器松手在这里直接忽略。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        InteractionHand hand = BehaviorResolver.handOf(entity, stack);
        BehaviorField field = BehaviorField.useField(hand);
        PartTypeDef.BehaviorDecl decl = BehaviorResolver.declFor(stack, field);
        WeaponBehavior behavior = decl == null ? null : BehaviorRegistry.get(decl.behavior());
        if (behavior == null) return;
        behavior.onReleaseUse(new WeaponBehavior.UseContext(field, level, player, hand, stack, decl), timeLeft);
    }

    /**
     * 是不是**本模组武器底座**——所有"这只手的物品算不算武器"的判断都走这里。
     * <p>
     * 光看"有没有装配树"不够：单个零件（刃/镡）也带 {@code PART_IDENTITY}，所以必须认物品本身。
     * 这条口径与迁移前各处的 {@code getItem() != HANDLE_PART} 完全一致（BUG-005 裸刃不能打人就靠它）。
     */
    public static boolean isWeaponBase(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == CwcItems.HANDLE_PART.get();
    }

    /**
     * 格挡减伤加成（镡等零件提供）——从装配树现算，不再读 {@code BLOCK_VALUE} 组件。
     * 每次受击算一次，代价可忽略。见 {@link com.funnyb.cwc.combat.behavior.BlockBehavior}。
     */
    public static float blockBonus(ItemStack stack) {
        return (float) AssemblyTree.of(stack).block();
    }
}
