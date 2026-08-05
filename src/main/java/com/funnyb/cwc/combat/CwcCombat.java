package com.funnyb.cwc.combat;

import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * CWC 统一攻击管线——主手、副手（以及未来所有攻击方式）共用同一条流水线：
 *
 * <pre>
 * 触发 → 武器校验 → 距离解析(resolveReach) → 冷却校验(isCooldownReady)
 *     → 伤害结算(applyDamage / applySweep) → 冷却施加 → 反馈(挥动/音效)
 * </pre>
 *
 * 三个入口：
 * <ul>
 *   <li>主手普攻：{@link #performMainHandAttack}（CwcMainHandAttackPacket 触发，权威执行）</li>
 *   <li>副手短刀出刀：{@link #performOffhandAttack}（CwcOffhandAttackPacket 触发）</li>
 *   <li>第三方客户端兜底：AttackEntityEvent（见 {@link CwcCombatEvents#onAttack}）</li>
 * </ul>
 *
 * 主手不再走 vanilla {@code Player.attack}——因为横扫（sweep）要求"空挥也算范围攻击"，
 * 而 vanilla 空挥只发 ServerboundSwingPacket 不触发攻击事件，无法 hook。主副手同构：
 * 客户端点选目标发自定义包，服务端按刃型普攻方式（{@link PartTypeDef.AttackStyle}）权威结算。
 *
 * 普攻方式（刃型声明，见 combat.style）：normal 稳定直击无暴击 / sweep 范围内全额伤害 / critical 原版跳劈暴击。
 * CWC 武器无视原版无敌帧：所有伤害入口命中前都清零目标 {@code invulnerableTime}。
 */
public final class CwcCombat {

    /** 空手基础伤害（原版玩家 ATTACK_DAMAGE 基准值） */
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    /** 横扫前方锥体水平/垂直半角（±75°） */
    private static final double SWEEP_CONE_HALF_ANGLE = Math.toRadians(75.0);
    /** 横扫贴身球半径 = reach / 2（"攻击范围除以 2 是球的半径"） */
    private static final double SWEEP_BALL_RADIUS_DIVISOR = 2.0;
    /** 视线判挡余量：clip 命中方块距眼睛比目标近超过此值才判挡（服务端误差空间） */
    private static final double SWEEP_LOS_SLACK = 0.5;

    private CwcCombat() {}

    // ==================== 统一攻击管线：入口 ====================

    /**
     * 主手普攻——服务端权威（由 CwcMainHandAttackPacket 触发）。
     * 走完整管线：武器校验 → 冷却校验（攻速条满）→ 目标解析/距离校验 → 按刃型普攻方式结算：
     * sweep 空挥也结算范围伤害，normal/critical 无目标仅挥空进冷却。
     */
    public static void performMainHandAttack(ServerPlayer player, int targetId) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.getItem() != CwcItems.HANDLE_PART.get()) return;            // 只认主手 CWC 武器
        if (!isCooldownReady(player, InteractionHand.MAIN_HAND, weapon)) return; // 服务端权威冷却

        ServerLevel level = player.serverLevel();
        Entity target = targetId >= 0 ? level.getEntity(targetId) : null;
        if (target != null && (target == player || !target.isAttackable())) {
            target = null;  // 无效目标按空挥
        }
        if (target != null) {
            double reach = resolveReach(player, InteractionHand.MAIN_HAND, weapon);
            if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > reach * reach) {
                target = null;  // 超出攻击距离，按空挥
            }
        }

        PartTypeDef.AttackStyle style = CwcWeapon.attackStyle(weapon);
        switch (style) {
            case SWEEP -> applySweep(player, target, weapon, InteractionHand.MAIN_HAND);
            case NORMAL, CRITICAL -> {
                if (target != null) {
                    applyDamage(player, target, weapon, InteractionHand.MAIN_HAND, style, true);
                }
            }
        }

        // 主手冷却：攻速条归零（vanilla Player.attack 末尾的 resetAttackStrengthTicker 由这里接管）
        player.resetAttackStrengthTicker();
        // 反馈：广播挥动给其他玩家。双参 swing(hand,false) 不会隐式重置攻速条（已手动归零）
        player.swing(InteractionHand.MAIN_HAND, false);
    }

    /**
     * 副手短刀右键攻击——服务端权威执行（由 CwcOffhandAttackPacket 触发）。
     * 走完整管线：武器校验 → 距离解析 → 冷却校验 → 伤害结算（按短刀普攻方式，normal）→ 冷却施加 → 挥动反馈。
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
            applyDamage(player, target, knife, InteractionHand.OFF_HAND, CwcWeapon.attackStyle(knife), true);
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

    /** 冷却施加——按手：主手由 performMainHandAttack 显式 resetAttackStrengthTicker；副手施加独立物品冷却 */
    public static void applyCooldown(ServerPlayer player, InteractionHand hand, ItemStack weapon) {
        if (hand != InteractionHand.OFF_HAND) {
            return;  // 主手冷却在 performMainHandAttack 末尾归零攻速条，不在此处理
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

    // ==================== 伤害结算 ====================

    /**
     * 统一伤害例程——主副手共用，按普攻方式分发。
     *
     * @param primary true = 主结算目标（耗耐久/统计/饥饿/命中音效/粒子）；false = 横扫副目标（全额伤害但静默）
     * @return 是否命中（目标 hurt 成功）
     */
    private static boolean applyDamage(ServerPlayer player, Entity target, ItemStack weapon,
                                       InteractionHand hand, PartTypeDef.AttackStyle style, boolean primary) {
        ServerLevel level = player.serverLevel();
        DamageSource source = player.damageSources().playerAttack(player);
        // CWC 武器无视原版无敌帧：命中前清零目标 invulnerableTime
        bypassInvulnerability(target);

        // 基础伤害按手区分：主手 = 玩家 ATTACK_DAMAGE 属性（含武器 DMG modifier + 力量药水）；
        // 副手 = 空手 1.0 + 刀自身修正（刀在 OFFHAND 槽，其 MAINHAND 修饰不计入玩家属性）
        float base = (hand == InteractionHand.MAIN_HAND)
                ? (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : (float) (BASE_ATTACK_DAMAGE + stackAttributeModifier(weapon, Attributes.ATTACK_DAMAGE));

        // 锋利等附魔伤害（复刻 ServerPlayer.getEnchantedDamage → EnchantmentHelper.modifyDamage）
        float enchantBonus = EnchantmentHelper.modifyDamage(level, weapon, target, source, base) - base;

        // 暴击：仅 critical 刃型保留原版跳劈暴击（空中下落 ×1.5）；normal/sweep 稳定直击永不暴击
        boolean crit = style == PartTypeDef.AttackStyle.CRITICAL
                && player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable()
                && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger() && target instanceof LivingEntity && !player.isSprinting();

        float damage = base;
        if (crit) damage *= 1.5F;
        damage += enchantBonus;  // 附魔加成不参与暴击倍率（原版语义）

        Vec3 preMove = target.getDeltaMovement();
        float previousHealth = target instanceof LivingEntity living ? living.getHealth() : 0.0F;

        if (damage > 0.0F && target.hurt(source, damage)) {
            // 击退：主手读玩家 ATTACK_KNOCKBACK 属性；副手读武器自身修正（与 resolveReach 同思路）
            double knockback = (hand == InteractionHand.MAIN_HAND)
                    ? player.getAttributeValue(Attributes.ATTACK_KNOCKBACK)
                    : stackAttributeModifier(weapon, Attributes.ATTACK_KNOCKBACK);
            if (knockback > 0.0 && target instanceof LivingEntity living) {
                living.knockback(
                        knockback * 0.5,
                        Math.sin(player.getYRot() * Math.PI / 180.0),
                        -Math.cos(player.getYRot() * Math.PI / 180.0)
                );
            }
            // 附魔后效（火焰附加等）
            EnchantmentHelper.doPostAttackEffects(level, target, source);
            // 击打玩家补发运动包（复刻 vanilla Player.attack：否则客户端看不到击退）
            if (target instanceof ServerPlayer sp && sp.hurtMarked) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                sp.hurtMarked = false;
                sp.setDeltaMovement(preMove);
            }

            if (primary) {
                // 耐久损耗
                if (target instanceof LivingEntity) {
                    weapon.hurtAndBreak(1, player,
                            hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
                player.setLastHurtMob(target);
                // 统计与伤害指示粒子
                if (target instanceof LivingEntity living) {
                    float dealt = previousHealth - living.getHealth();
                    player.awardStat(Stats.DAMAGE_DEALT, Math.round(dealt * 10.0F));
                    if (dealt > 2.0F) {
                        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                                target.getX(), target.getY(0.5), target.getZ(),
                                (int) (dealt * 0.5), 0.1, 0.0, 0.1, 0.2);
                    }
                }
                // 饥饿消耗（与原版攻击一致）
                player.getFoodData().addExhaustion(0.1F);
                // 音效：暴击播 CRIT + 红粒子；sweep 主目标不播 STRONG（横扫音效由 applySweep 统一播，避免叠加）；
                // normal/critical 命中播强击音效；附魔加成>0 播附魔暴击粒子
                if (crit) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 1.0F, 1.0F);
                    player.crit(target);
                } else if (style != PartTypeDef.AttackStyle.SWEEP) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_ATTACK_STRONG, player.getSoundSource(), 1.0F, 1.0F);
                }
                if (enchantBonus > 0.0F) player.magicCrit(target);
            }
            return true;
        }
        if (primary) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_NODAMAGE, player.getSoundSource(), 1.0F, 1.0F);
        }
        return false;
    }

    /**
     * 横扫（sweep）——范围内全额伤害，挥出即范围攻击，**未命中（全空）也算**。
     * 范围判定为**双区域**：前方锥体（顶点玩家眼睛、轴 = 视线、水平/垂直半角各 ±75°、半径 = 手长 reach）
     * 或贴身球（球心 = 玩家碰撞箱中心、半径 = reach/2），目标在**任一**区域内即吃伤害（见
     * {@link #isInSweepDualRange}）；两个区域都做视线检测（{@link #hasLineOfSight}），墙后扫不到。
     * 副目标静默全额（不耗耐久/统计/音效），主目标走 primary 结算一次完整反馈；
     * 主目标服务端复核不通过（被墙挡/不在双区域）按空挥降级，横扫范围结算照旧。
     */
    private static void applySweep(ServerPlayer player, Entity mainTarget, ItemStack weapon, InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        double reach = resolveReach(player, hand, weapon);

        // 候选 AABB：眼位 inflate(reach) 同时覆盖前方锥体与贴身球（两者半径都 ≤ reach）
        Vec3 eye = player.getEyePosition();
        AABB area = new AABB(eye, eye).inflate(reach);

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (e == player || e == mainTarget) continue;
            if (!e.isAttackable()) continue;
            if (e instanceof ArmorStand armor && armor.isMarker()) continue;
            if (player.isAlliedTo(e)) continue;
            // 双区域任一命中 或 准星射线命中（准星攻击范围是一条射线，线内目标均受伤害）+ 视线检测（墙后扫不到）
            if (!isInSweepDualRange(player, e, reach) && !isHitByLookRay(player, e, reach)) continue;
            if (!hasLineOfSight(level, player, e)) continue;
            applyDamage(player, e, weapon, hand, PartTypeDef.AttackStyle.SWEEP, false);
        }

        // 主目标服务端复核（防第三方客户端隔墙点选）：通过双区域 + 视线（多采样）才走 primary，否则按空挥。
        // 额外准星射线豁免：准星方向射线命中目标碰撞箱也可攻击（主目标由客户端准星点选，尊重瞄准结果）
        if (mainTarget != null && isInSweepDualRange(player, mainTarget, reach)
                && (hasLineOfSight(level, player, mainTarget) || isHitByLookRay(player, mainTarget, reach))) {
            applyDamage(player, mainTarget, weapon, hand, PartTypeDef.AttackStyle.SWEEP, true);
        }

        // 横扫挥出即播横扫音效 + 粒子（未命中全空也算）；主目标命中音效由 applyDamage 跳过（避免与 SWEEP 叠加）
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1.0F, 1.0F);
        player.sweepAttack();
    }

    /**
     * 横扫双区域判定——目标在任一区域内即命中（OR），客户端/服务端共用同一几何：
     * <ul>
     *   <li>区域 A 前方锥体：顶点玩家眼睛、轴 = 玩家视线、水平/垂直半角各 ±75°
     *       （{@link #SWEEP_CONE_HALF_ANGLE}）、半径 = 手长（reach），距离量法 = 眼睛到碰撞箱 ≤ reach；</li>
     *   <li>区域 B 贴身球：球心 = 玩家碰撞箱中心、半径 = reach/2（{@link #SWEEP_BALL_RADIUS_DIVISOR}）。</li>
     * </ul>
     * 视线检测不在此方法内（客户端主目标由 hitResult 天然保证，服务端另行走 {@link #hasLineOfSight}）。
     */
    public static boolean isInSweepDualRange(Player player, Entity target, double reach) {
        // 区域 B（贴身球）：球心 = 玩家碰撞箱中心，半径 = reach/2
        double ballRadius = reach / SWEEP_BALL_RADIUS_DIVISOR;
        if (target.getBoundingBox().distanceToSqr(player.getBoundingBox().getCenter()) <= ballRadius * ballRadius) {
            return true;
        }

        // 区域 A（前方锥体）
        Vec3 eye = player.getEyePosition();
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
        if (toTarget.lengthSqr() < 1.0E-4) return true;   // 目标中心与眼睛几乎重合，直接命中
        Vec3 look = player.getLookAngle();

        // 水平夹角：toTarget 与 look 在 y=0 平面投影的夹角；任一投影为 0（正上方/正下方或垂直仰视）时跳过水平限制
        double horizDist = Math.hypot(toTarget.x, toTarget.z);
        double horizLookDist = Math.hypot(look.x, look.z);
        if (horizDist > 1.0E-4 && horizLookDist > 1.0E-4) {
            double cosH = (toTarget.x * look.x + toTarget.z * look.z) / (horizDist * horizLookDist);
            if (Math.acos(Mth.clamp(cosH, -1.0, 1.0)) > SWEEP_CONE_HALF_ANGLE) return false;
        }

        // 垂直夹角：toTarget 与 look 的俯仰角差（y 分量的 asin）
        double targetLen = Math.sqrt(toTarget.lengthSqr());
        double pitchTarget = Math.asin(Mth.clamp(toTarget.y / targetLen, -1.0, 1.0));
        double pitchLook = Math.asin(Mth.clamp(look.y, -1.0, 1.0));
        if (Math.abs(pitchTarget - pitchLook) > SWEEP_CONE_HALF_ANGLE) return false;

        // 半径 = 手长（reach）
        return target.getBoundingBox().distanceToSqr(eye) <= reach * reach;
    }

    /**
     * 视线检测——**看到碰撞箱任意采样点即可命中**（墙后扫不到）。服务端沿用原本的简单算法（单条方块射线）：
     * 采样点 = 碰撞箱中心（保留）+ 目标眼睛位置（活体）/中心（非活体）+ 碰撞箱 8 个角，对每个采样点做单条射线，
     * 任一可见即判目标可见（"角度刁钻也要细扣"）。判挡保留误差空间（{@link #SWEEP_LOS_SLACK}）：
     * 命中方块距眼睛比采样点近超过余量才算挡，目标贴墙/露头不误判。
     */
    private static boolean hasLineOfSight(Level level, Player player, Entity target) {
        Vec3 eye = player.getEyePosition();
        AABB bb = target.getBoundingBox();
        // 采样点：碰撞箱中心（保留）+ 原本参考点（活体眼睛位置 / 非活体中心）+ 8 个角
        Vec3[] samples = new Vec3[10];
        samples[0] = bb.getCenter();
        samples[1] = (target instanceof LivingEntity living) ? living.getEyePosition() : bb.getCenter();
        double[] x = {bb.minX, bb.maxX};
        double[] y = {bb.minY, bb.maxY};
        double[] z = {bb.minZ, bb.maxZ};
        int idx = 2;
        for (double sy : y) {
            for (double sx : x) {
                for (double sz : z) {
                    samples[idx++] = new Vec3(sx, sy, sz);
                }
            }
        }
        for (Vec3 hit : samples) {
            if (hasLineOfSightToPoint(level, player, eye, hit)) return true;
        }
        return false;
    }

    /** 单点视线判定——原本的遮挡算法：眼睛到采样点做方块射线，命中且比采样点近超过余量才判挡 */
    private static boolean hasLineOfSightToPoint(Level level, Player player, Vec3 eye, Vec3 hit) {
        if (hit.distanceToSqr(eye) < 1.0E-4) {
            return true;   // 采样点与眼睛几乎重合（贴身），可见
        }
        BlockHitResult blockHit = level.clip(new ClipContext(eye, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return eye.distanceTo(blockHit.getLocation()) >= eye.distanceTo(hit) - SWEEP_LOS_SLACK;
    }

    /**
     * 准星射线命中判定——从玩家眼睛沿视线方向发射长度 reach 的射线（准星攻击范围是一条射线，
     * 处于射线内的目标均会受到伤害），目标碰撞箱与射线段相交即命中。
     * 纯几何判定（AABB.clip），不含方块遮挡——可见性由调用方另行判定（hasLineOfSight）。
     */
    private static boolean isHitByLookRay(Player player, Entity target, double reach) {
        Vec3 eye = player.getEyePosition();
        return target.getBoundingBox().clip(eye, eye.add(player.getLookAngle().scale(reach))).isPresent();
    }

    /**
     * 准星目标能否被当前武器（手）命中——与攻击结算同规则，客户端攻击指示器用：
     * <ul>
     *   <li>sweep（横扫）：双区域（前方锥体/贴身球）或准星射线命中，且非友军（横扫服务端本就不打友军）；</li>
     *   <li>normal/critical（单体）：眼睛到碰撞箱距离 ≤ reach（与主手点选同量法），不排除友军（单体可打友军）。</li>
     * </ul>
     * 视线由准星 {@code hitResult} 天然保证（准星射线命中实体前无方块），无需在此重复判定。
     */
    public static boolean canHitTarget(Player player, Entity target, ItemStack weapon, InteractionHand hand) {
        if (target == null || target == player || !target.isAttackable()) return false;
        double reach = resolveReach(player, hand, weapon);
        return switch (CwcWeapon.attackStyle(weapon)) {
            case SWEEP -> !player.isAlliedTo(target)
                    && (isInSweepDualRange(player, target, reach) || isHitByLookRay(player, target, reach));
            case NORMAL, CRITICAL ->
                    target.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach;
        };
    }

    /**
     * 横扫攻击范围内是否存在可攻击目标——客户端攻击指示器用（**只要有能打到的目标就提示，不要求准星对准**）。
     * 与服务端 {@link #applySweep} 副目标同规则：眼位 inflate(reach) 内活体，双区域或准星射线命中 + 视线 + 非友军 + 可攻击。
     */
    public static boolean hasSweepTarget(Player player, ItemStack weapon, InteractionHand hand) {
        Level level = player.level();
        double reach = resolveReach(player, hand, weapon);
        Vec3 eye = player.getEyePosition();
        AABB area = new AABB(eye, eye).inflate(reach);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (e == player || !e.isAttackable()) continue;
            if (e instanceof ArmorStand armor && armor.isMarker()) continue;
            if (player.isAlliedTo(e)) continue;
            if (!isInSweepDualRange(player, e, reach) && !isHitByLookRay(player, e, reach)) continue;
            if (!hasLineOfSight(level, player, e)) continue;
            return true;
        }
        return false;
    }

    /**
     * 当前武器（手）是否存在**任意可攻击的目标**——攻击指示器"提示可攻击"的统一判定：
     * 横扫 = 攻击范围内存在可攻击实体（不要求准星对准）；单体（normal/critical）= 准星目标可命中。
     *
     * @param crosshairTarget 客户端准星命中的实体（mc.hitResult），单体判定用；横扫忽略
     */
    public static boolean hasAnyAttackableTarget(Player player, ItemStack weapon, InteractionHand hand, Entity crosshairTarget) {
        return switch (CwcWeapon.attackStyle(weapon)) {
            case SWEEP -> hasSweepTarget(player, weapon, hand);
            case NORMAL, CRITICAL -> canHitTarget(player, crosshairTarget, weapon, hand);
        };
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
