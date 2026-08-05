package com.funnyb.cwc.combat;

import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.stats.Stats;

/**
 * CWC 统一攻击管线——主手、副手（以及未来所有攻击方式）共用同一条流水线：
 *
 * <pre>
 * 触发 → 武器校验 → 距离解析(resolveReach) → 冷却校验(isCooldownReady)
 *     → 伤害结算 → 冷却施加(applyCooldown) → 反馈(挥动/音效)
 * </pre>
 *
 * 主手攻击由 vanilla {@code Player.attack} 承载（勾在 AttackEntityEvent，见
 * {@link CwcCombatEvents#onAttack}），管线只做前置校验，伤害仍走原版完整语义；
 * 副手短刀出刀走 {@link #performOffhandAttack} 全管线（无原版副手攻击，伤害用统一例程）。
 * CWC 武器无视原版无敌帧：两处伤害入口命中前都清零目标 {@code invulnerableTime}（见
 * {@link #bypassInvulnerability}）。未来加新攻击方式/特殊标志在此扩展，不再另起一套伤害代码。
 */
public final class CwcCombat {

    /** 原版玩家基础近战交互距离（ENTITY_INTERACTION_RANGE 默认值） */
    public static final double BASE_MELEE_REACH = 3.0;
    /** 空手基础伤害（原版玩家 ATTACK_DAMAGE 基准值） */
    private static final double BASE_ATTACK_DAMAGE = 1.0;

    private CwcCombat() {}

    // ==================== 统一攻击管线：入口 ====================

    /**
     * 主手 CWC 攻击拦截——勾在 AttackEntityEvent（vanilla {@code Player.attack} 内部）。
     * 返回是否放行 vanilla 继续结算；false = 冷却未满，取消攻击（不发伤害、不重置冷却）。
     * 非 CWC 武器一律放行。未来"无视无敌帧"等特殊标志在此对目标做前置处理。
     */
    public static boolean interceptMainHandAttack(Player player, Entity target) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.getItem() != CwcItems.HANDLE_PART.get()) return true; // 非 CWC 武器放行
        if (!isCooldownReady(player, InteractionHand.MAIN_HAND, weapon)) return false;
        // CWC 武器无视原版无敌帧：放行前清零目标 invulnerableTime，随后的 vanilla Player.attack 命中即生效
        bypassInvulnerability(target);
        return true;
    }

    /**
     * 副手短刀右键攻击——服务端权威执行（由 CwcOffhandAttackPacket 触发）。
     * 走完整管线：武器校验 → 距离解析（短刀自身距离规则）→ 冷却校验 → 伤害结算 → 冷却施加 → 挥动反馈。
     * 目标由客户端 hitResult 点选（targetId，-1 空挥），超短刀距离按空挥处理。
     */
    public static void performOffhandAttack(ServerPlayer player, int targetId) {
        ItemStack knife = player.getOffhandItem();
        if (!CwcWeapon.isOffhandKnife(knife)) return;                       // 只认短刀武器
        if (CwcWeapon.isTwoHandedStack(player.getMainHandItem())) return;   // 双手武器占用右键
        if (!isCooldownReady(player, InteractionHand.OFF_HAND, knife)) return; // 副手独立冷却

        ServerLevel level = player.serverLevel();
        Entity target = targetId >= 0 ? level.getEntity(targetId) : null;
        if (target != null && (target == player || !target.isAttackable())) {
            target = null;  // 无效目标按空挥
        }
        if (target != null) {
            // 用与主手原版一致的眼睛到碰撞箱距离判定（而非实体坐标距离），避免不同高度下副手距离偏短
            double reach = resolveReach(player, InteractionHand.OFF_HAND, knife);
            if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > reach * reach) {
                target = null;  // 超出短刀自身攻击距离，按空挥
            }
        }

        if (target != null) {
            applyOffhandDamage(player, target, knife);
        }

        applyCooldown(player, InteractionHand.OFF_HAND, knife);
        // 反馈：副手挥动（广播给其他客户端；攻击者本地的副手挥动由客户端拦截点触发）。
        // 必须用双参重载 swing(hand,false)——ServerPlayer.swing(hand) 会 resetAttackStrengthTicker
        // 重置主手攻击强度，副手出刀不应影响主手冷却。
        player.swing(InteractionHand.OFF_HAND, false);
    }

    // ==================== 管线各阶段（共享） ====================

    /**
     * 距离解析——按手 + 武器：
     * 主手：玩家当前交互距离（武器 reach 已反映在玩家属性上）；
     * 副手：玩家当前交互距离 − 主手 CWC 武器的 reach 加成 + 短刀自身加成。
     * 即：其他来源的 reach（药水/附魔/非 CWC 主手武器）作用到副手，只有主手 CWC 武器的加成不传导。
     */
    public static double resolveReach(Player player, InteractionHand hand, ItemStack weapon) {
        double playerRange = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        if (hand == InteractionHand.MAIN_HAND) {
            return playerRange;
        }
        ItemStack main = player.getMainHandItem();
        double mainBonus = (main.getItem() == CwcItems.HANDLE_PART.get())
                ? stackAttributeModifier(main, Attributes.ENTITY_INTERACTION_RANGE) : 0.0;
        double knifeBonus = stackAttributeModifier(weapon, Attributes.ENTITY_INTERACTION_RANGE);
        return playerRange - mainBonus + knifeBonus;
    }

    /** 冷却校验——按手：主手用原版攻速条，副手用独立冷却键 */
    public static boolean isCooldownReady(Player player, InteractionHand hand, ItemStack weapon) {
        if (hand == InteractionHand.MAIN_HAND) {
            return player.getAttackStrengthScale(0f) >= 1.0f;
        }
        return isOffhandReady(player);
    }

    /** 冷却施加——按手：主手由 vanilla Player.attack 末尾重置攻速条；副手施加独立物品冷却 */
    public static void applyCooldown(ServerPlayer player, InteractionHand hand, ItemStack weapon) {
        if (hand == InteractionHand.MAIN_HAND) {
            return;  // 原版 Player.attack 已重置攻速条
        }
        player.getCooldowns().addCooldown(CwcItems.OFFHAND_COOLDOWN.get(), offhandCooldownTicks(weapon));
    }

    // ==================== 副手就绪辅助（客户端指示器/拦截也用） ====================

    /** 副手短刀是否就绪——独立物品冷却键（OFFHAND_COOLDOWN）未激活即可出刀，与主手/其他 CWC 武器完全隔离 */
    public static boolean isOffhandReady(Player player) {
        return !player.getCooldowns().isOnCooldown(CwcItems.OFFHAND_COOLDOWN.get());
    }

    /** 副手短刀就绪度 0~1（1 = 可攻击），客户端指示器用；数据来自独立冷却键 */
    public static float offhandReadiness(Player player) {
        return 1.0F - player.getCooldowns().getCooldownPercent(CwcItems.OFFHAND_COOLDOWN.get(), 0.0F);
    }

    /** 副手短刀出刀冷却 tick 数——按自身攻速（4 + ATTACK_SPEED 修正）换算 */
    public static int offhandCooldownTicks(ItemStack knife) {
        double attackSpeed = 4.0 + stackAttributeModifier(knife, Attributes.ATTACK_SPEED);
        return Math.max(1, (int) Math.ceil(20.0 / Math.max(0.1, attackSpeed)));
    }

    // ==================== 内部：副手伤害例程 ====================

    /** 副手短刀伤害结算——统一例程：基础伤害 + 锋利附魔 + 暴击 + 击退 + 附魔后效 + 耐久 + 统计/音效 */
    private static void applyOffhandDamage(ServerPlayer player, Entity target, ItemStack knife) {
        ServerLevel level = player.serverLevel();
        DamageSource source = player.damageSources().playerAttack(player);
        // CWC 武器无视原版无敌帧：命中前清零目标 invulnerableTime
        bypassInvulnerability(target);
        // 基础伤害 = 空手 1.0 + 刀自身 ATTACK_DAMAGE 修正
        float damage = (float) (BASE_ATTACK_DAMAGE + stackAttributeModifier(knife, Attributes.ATTACK_DAMAGE));
        // 附魔伤害（锋利等）
        damage = EnchantmentHelper.modifyDamage(level, knife, target, source, damage);
        // 暴击（独立冷却必满 → 冷却系数恒 1.0，无衰减）
        boolean crit = player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable()
                && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger() && target instanceof LivingEntity && !player.isSprinting();
        if (crit) damage *= 1.5F;

        float previousHealth = target instanceof LivingEntity living ? living.getHealth() : 0.0F;
        if (damage > 0.0F && target.hurt(source, damage)) {
            // 击退（刀的 ATTACK_KNOCKBACK 修正；短刀为 0 则不推）
            double knockback = stackAttributeModifier(knife, Attributes.ATTACK_KNOCKBACK);
            if (knockback > 0.0 && target instanceof LivingEntity living) {
                living.knockback(
                        knockback * 0.5,
                        Math.sin(player.getYRot() * Math.PI / 180.0),
                        -Math.cos(player.getYRot() * Math.PI / 180.0)
                );
            }
            // 附魔后效（火焰附加等）
            EnchantmentHelper.doPostAttackEffects(level, target, source);
            // 耐久损耗
            if (target instanceof LivingEntity) {
                knife.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
            }
            // 统计与声音（命中必满冷却 → 强击音效；暴击单独音效）
            player.setLastHurtMob(target);
            if (crit) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 1.0F, 1.0F);
            } else {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_STRONG, player.getSoundSource(), 1.0F, 1.0F);
            }
            if (target instanceof LivingEntity living) {
                player.awardStat(Stats.DAMAGE_DEALT, Math.round((previousHealth - living.getHealth()) * 10.0F));
            }
        }
    }

    /**
     * CWC 武器无视原版无敌帧——命中前清零目标 {@code invulnerableTime}。
     * {@code LivingEntity.hurt} 的 i-frame 检查（invulnerableTime > 10 时仅结算超出上次伤害的部分）基于该字段；
     * 清零后本次命中全额结算。creative/spectator 玩家的 {@code abilities.invulnerable} 是独立更高的保护层，不受影响。
     */
    private static void bypassInvulnerability(Entity target) {
        if (target != null) {
            target.invulnerableTime = 0;
        }
    }

    /** 读某栈在主手槽位上某属性的 ADD_VALUE 修正值（该武器自身的属性加成），无则 0 */
    private static double stackAttributeModifier(ItemStack stack, Holder<Attribute> attribute) {
        if (stack.isEmpty()) return 0.0;
        double[] value = {0.0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.equals(attribute) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                value[0] = mod.amount();
            }
        });
        return value[0];
    }
}
