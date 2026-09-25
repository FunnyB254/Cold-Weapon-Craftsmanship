package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.crafting.PartTypeDef;

import java.util.EnumSet;
import java.util.Set;

/**
 * 跳劈暴击（{@code cwc:critical_attack}）——单目标，但**保留原版跳劈暴击**：空中下落时 ×1.5
 * （另有暴击音效与红粒子）。几何与结算同 {@link StrikeAttack}，唯一差别就是这个风格标记。
 * <p>
 * 旧数据里 {@code combat.style: "critical"} 折算成它（当前没有数据用它）。
 */
public final class CriticalAttack implements WeaponBehavior {

    @Override
    public String id() {
        return BehaviorRegistry.CRITICAL_ATTACK;
    }

    @Override
    public Set<BehaviorField> fields() {
        return EnumSet.of(BehaviorField.ATTACK);
    }

    @Override
    public PartTypeDef.AttackStyle strikeStyle() {
        return PartTypeDef.AttackStyle.CRITICAL;
    }
}
