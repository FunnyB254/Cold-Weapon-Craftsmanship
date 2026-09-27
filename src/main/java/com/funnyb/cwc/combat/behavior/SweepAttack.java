package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * 横扫（{@code cwc:sweep_attack}）——范围内全额伤害，**挥出即范围攻击**（空挥也算），不依赖主目标命中。
 * <p>
 * 攻击方式一簇里唯一覆写全部四项的行为：几何是"前方锥体 ∪ 贴身球"（不是单体那条距离量法）、
 * 指示器判据是**范围扫描**、结算是范围扫描。三处与服务端共用同一套几何
 * （都在 {@code CwcCombat} 里），否则会出现"指示器说打得到、服务端不让打"的不一致。
 * <p>
 * {@link #hasAnyTarget} 只管"准星处**没有**实体"那一半——准星指着东西时指示器由
 * {@code CwcCombat#hasAnyAttackableTarget} 直接点亮，问不到这里（原版口径）。
 * <p>
 * 指示器与结算的对应关系：准星正对的走**主目标**那条（那一侧不查"是不是我的宠物"，所以准星指着自己的
 * 宠物照打），范围里的走**副目标**那条（查）。
 * <p>
 * 旧数据里 {@code combat.style: "sweep"}（半剑/长剑/标准刃）折算成它。
 */
public final class SweepAttack implements WeaponBehavior {

    @Override
    public String id() {
        return BehaviorRegistry.SWEEP_ATTACK;
    }

    @Override
    public Set<BehaviorField> fields() {
        return EnumSet.of(BehaviorField.ATTACK);
    }

    @Override
    public PartTypeDef.AttackStyle strikeStyle() {
        return PartTypeDef.AttackStyle.SWEEP;
    }

    @Override
    public boolean canHit(Player player, Entity target, ItemStack weapon, InteractionHand hand) {
        return CwcCombat.canHitSweep(player, target, weapon, hand);
    }

    @Override
    public boolean hasAnyTarget(Player player, ItemStack weapon, InteractionHand hand, Entity crosshairTarget) {
        // crosshairTarget 不用：准星正对的那个目标由 hasAnyAttackableTarget 的第 1 条统一判，
        // 本方法只管"范围里有东西"（见 hasSweepTarget 的说明）
        return CwcCombat.hasSweepTarget(player, weapon, hand);
    }

    @Override
    public void strike(ServerPlayer player, InteractionHand hand, ItemStack weapon, int targetId) {
        CwcCombat.strikeSweep(player, hand, weapon, targetId);
    }
}
