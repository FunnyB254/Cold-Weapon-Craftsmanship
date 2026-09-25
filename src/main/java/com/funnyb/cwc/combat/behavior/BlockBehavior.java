package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.item.CwcWeapon;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import java.util.EnumSet;
import java.util.Set;

/**
 * 格挡（{@code cwc:block_use}）——右键**按住**进入保持状态，期间受到的伤害按基础值 + 镡加成减免。
 * <p>
 * 借原版 {@code UseAnim.BLOCK} 姿态与前伸的使用状态（{@code isUsingItem}）作为全部状态载体：
 * 本行为**不自己记"正在格挡"**，那就是复刻原版状态（见 {@link BehaviorMachine} 的说明）。
 * <p>
 * 挂载字段：{@code mainHandUse}（双手武器，旧 {@code twoHanded: true} 折算）与 {@code offHandUse}
 * （副手小盾/护手之类的第三种用法，今天没有数据用它）。
 */
public final class BlockBehavior implements WeaponBehavior {

    /**
     * 格挡的**基础**减伤。镡在此之上另加成（{@link CwcWeapon#blockBonus}），
     * 所以"不带镡"与"带镡"应当差出一档：基础 25%、带镡 50%。
     */
    private static final float BASE_REDUCTION = 0.25f;

    /** 减伤上限——防止无敌；下限 0 防止加成写成负数时反而放大伤害 */
    private static final float MAX_REDUCTION = 0.95f;

    /** 按住不放的时长（原版的"几乎无限"写法） */
    private static final int HOLD_TICKS = 72000;

    @Override
    public String id() {
        return BehaviorRegistry.BLOCK;
    }

    @Override
    public Set<BehaviorField> fields() {
        return EnumSet.of(BehaviorField.MAIN_HAND_USE, BehaviorField.OFF_HAND_USE);
    }

    @Override
    public boolean onUse(UseContext ctx) {
        ctx.player().startUsingItem(ctx.hand());
        return true;   // 原版按"已消费"处理，右键不再落到方块/实体交互上
    }

    @Override
    public int useDuration(ItemStack stack, LivingEntity entity, PartTypeDef.BehaviorDecl decl) {
        return HOLD_TICKS;   // 持续按住 = 持续格挡
    }

    @Override
    public UseAnim useAnimation(ItemStack stack, PartTypeDef.BehaviorDecl decl) {
        return UseAnim.BLOCK;   // 借原版格挡姿态作为视觉反馈
    }

    /**
     * 减伤——**减伤范围对齐原版盾牌**（{@code BYPASSES_SHIELD} 类伤害一律不吃格挡）这件事由
     * {@code CwcCombatEvents} 在转发之前判掉，本方法只管"格挡该减多少"。
     * <p>
     * 格挡加成从装配树现算（{@link CwcWeapon#blockBonus}），不再读物化的组件——改装配件后不更新、
     * 绕过装配台时干脆不存在，而这里每次受击只算一次，代价可忽略。
     */
    @Override
    public float modifyIncomingDamage(UseContext ctx, float amount) {
        float reduction = BASE_REDUCTION + CwcWeapon.blockBonus(ctx.stack());
        return amount * (1f - Mth.clamp(reduction, 0f, MAX_REDUCTION));
    }
}
