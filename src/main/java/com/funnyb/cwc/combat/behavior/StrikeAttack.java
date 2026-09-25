package com.funnyb.cwc.combat.behavior;

import java.util.EnumSet;
import java.util.Set;

/**
 * 单体直击（{@code cwc:strike_attack}）——一次挥击打**一个**目标，全额伤害、稳定直击、永不跳劈暴击。
 * <p>
 * 这就是 {@link WeaponBehavior} 攻击一簇的**默认实现**，所以本类除 id 与字段外一行不用写
 * （默认风格 {@code NORMAL}、默认几何"眼睛到碰撞箱 ≤ reach"、默认单目标结算）。
 * 旧数据里 {@code combat.style: "normal"}（短刀）折算成它。
 */
public final class StrikeAttack implements WeaponBehavior {

    @Override
    public String id() {
        return BehaviorRegistry.STRIKE_ATTACK;
    }

    @Override
    public Set<BehaviorField> fields() {
        return EnumSet.of(BehaviorField.ATTACK);
    }
}
