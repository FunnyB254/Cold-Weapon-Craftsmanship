package com.funnyb.cwc.combat;

import com.funnyb.cwc.combat.behavior.BehaviorField;
import com.funnyb.cwc.combat.behavior.BehaviorResolver;
import com.funnyb.cwc.combat.behavior.WeaponBehavior;
import com.funnyb.cwc.crafting.PartTypeDef;
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
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * CWC 统一攻击管线——主手、副手（以及未来所有攻击方式）共用同一条流水线：
 *
 * <pre>
 * 触发 → 武器校验 → 距离解析(resolveReach) → 冷却校验(服务端主手用 {@link #isServerCooldownReady}，其余用 {@link #isCooldownReady})
 *     → 伤害结算(applyDamage / applySweep) → 冷却施加 → 反馈(挥动/音效)
 * </pre>
 *
 * 两个入口：
 * <ul>
 *   <li>主手普攻：{@link #performMainHandAttack}（CwcMainHandAttackPacket 触发，权威执行）</li>
 *   <li>副手出刀：客户端发 CwcOffhandAttackPacket，服务端交给本次右键胜出的行为
 *       （{@code cwc:swing_use}，见 {@code SwingBehavior#onServerUse}），它再调本类的
 *       {@link #strikeSingle} 走同一条流水线</li>
 *   <li>第三方客户端兜底：AttackEntityEvent（见 {@link CwcCombatEvents#onAttack}）</li>
 * </ul>
 *
 * 主手不再走 vanilla {@code Player.attack}——因为横扫（sweep）要求"空挥也算范围攻击"，
 * 而 vanilla 空挥只发 ServerboundSwingPacket 不触发攻击事件，无法 hook。主副手同构：
 * 客户端点选目标发自定义包，服务端按**该物品 {@code attack} 字段胜出的攻击方式**
 * （{@link WeaponBehavior}，旧数据由 {@link PartTypeDef.AttackStyle} 折算）权威结算。
 *
 * 普攻方式：单体直击（normal，无暴击）/ 横扫（sweep，范围内全额伤害）/ 跳劈暴击（critical，原版 ×1.5）。
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
     * 走完整管线：武器校验 → 冷却校验（服务端自持时钟）→ 本物品 {@code attack} 字段胜出的
     * {@linkplain WeaponBehavior#strike 攻击方式}结算 → 冷却施加 → 挥动反馈。
     * <p>
     * "打谁、怎么打"全部归攻击方式；本方法只管两件与方式无关的事：**冷却计费**（空挥同样计费，与原版一致）
     * 与**广播挥动**。
     */
    public static void performMainHandAttack(ServerPlayer player, int targetId) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.getItem() != CwcItems.HANDLE_PART.get()) return;            // 只认主手 CWC 武器
        if (!isServerCooldownReady(player, InteractionHand.MAIN_HAND, weapon)) return; // 服务端权威冷却（自持时钟）
        markMainHandAttack(player);   // 出手即计费——空挥同样进冷却，与原版一致

        WeaponBehavior attack = BehaviorResolver.behaviorFor(weapon, BehaviorField.ATTACK);
        if (attack != null) attack.strike(player, InteractionHand.MAIN_HAND, weapon, targetId);
        // attack 字段并列成冲突时没有任何方式胜出 → 按空挥处理（不进伤害、不报错），
        // 但照旧计费与挥动——对玩家而言与"打空了"是同一件事。

        // 攻速条仍归零，但**本模组的冷却已不读它**——冷却由 markMainHandAttack 记下的时钟推进
        // （见 isServerCooldownReady）。保留这一句只是让服务端那根条对原版自己的路径仍然可信。
        player.resetAttackStrengthTicker();
        // 反馈：广播挥动给其他玩家。双参 swing(hand,false) 不会隐式重置攻速条
        player.swing(InteractionHand.MAIN_HAND, false);
    }

    // ==================== 攻击方式（attack 字段）的调用面 ====================
    //
    // 四个消费点（服务端结算、准星点选、指示器、范围扫描）此前按 AttackStyle 枚举 switch；现在改为问
    // "这件物品 attack 字段胜出的行为"。内置的三个方式（combat/behavior/ 下的 Strike/Sweep/CriticalAttack）
    // 只声明风格与几何，真正干活的是下面这几个方法——第三方要加自己的攻击方式，从这里取零件拼即可。

    /**
     * 单体命中结算——默认攻击方式（{@code cwc:strike_attack} / {@code cwc:critical_attack}）的实现；
     * 副手出刀也走它（**出刀不做范围攻击**，横扫只属于把 attack 声明成 sweep 的那种武器）。
     * <p>
     * 风格取该物品攻击方式声明的 {@link #strikeStyle}，所以"短刀在副手用的就是它自己的 attack"这条规则
     * 只有一处实现。
     */
    public static void strikeSingle(ServerPlayer player, InteractionHand hand, ItemStack weapon, int targetId) {
        Entity target = resolveValidTarget(player, targetId, weapon, hand);
        if (target != null) {
            applyDamage(player, target, weapon, hand, strikeStyle(weapon), true);
        }
    }

    /** 横扫结算——{@code cwc:sweep_attack} 的实现（挥出即范围攻击，空挥也算，见 {@link #applySweep}） */
    public static void strikeSweep(ServerPlayer player, InteractionHand hand, ItemStack weapon, int targetId) {
        applySweep(player, resolveValidTarget(player, targetId, weapon, hand), weapon, hand);
    }

    /** 单体几何——默认攻击方式的 {@link WeaponBehavior#canHit} 实现（公共前置已由 {@link #canHitTarget} 做完） */
    public static boolean canHitSingle(Player player, Entity target, ItemStack weapon, InteractionHand hand) {
        double reach = resolveReach(player, hand, weapon);
        return target.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach;
    }

    /** 横扫几何 + 友军规则——{@code cwc:sweep_attack} 的 {@link WeaponBehavior#canHit} 实现 */
    public static boolean canHitSweep(Player player, Entity target, ItemStack weapon, InteractionHand hand) {
        double reach = resolveReach(player, hand, weapon);
        return canHarmAlly(player, target)
                && (isInSweepDualRange(player, target, reach) || isHitByLookRay(player, target, reach));
    }

    /** 该物品攻击方式声明的结算风格——伤害例程据此决定暴击与音效分支（未装配/冲突按 NORMAL） */
    public static PartTypeDef.AttackStyle strikeStyle(ItemStack weapon) {
        WeaponBehavior attack = BehaviorResolver.behaviorFor(weapon, BehaviorField.ATTACK);
        return attack == null ? PartTypeDef.AttackStyle.NORMAL : attack.strikeStyle();
    }

    // ==================== 目标解析（主副手共用的一套） ====================

    /**
     * 目标解析与校验——**主副手共用同一套**。
     * <p>
     * 客户端提供"意图 + 目标 id"，服务端据此取出目标并验距离。此前两个入口各写一遍同样的三行检查
     * （同维度取实体、非自己、可攻击、距离 ≤ reach），既容易漂移也没必要；抽到一起后主副手天然一致。
     * <p>
     * <b>刻意不做的事：不查视线、不查 {@code skipAttackInteraction}、不查攻击者状态。</b>
     * 正常客户端的 {@code mc.hitResult} 是带方块遮挡的射线（命中实体即天然满足视线），原版输入层
     * 也已处理了格挡/旁观等状态——所以那几关只在"改包客户端"场景下才有意义。本模组当前**不以
     * 防作弊为目标**（服务端的冷却门槛同样刻意留了余量，见 {@link #isServerCooldownReady} 的说明），
     * 加了反而有让正常战斗出现"贴墙/露头打不到"的手感回归风险。
     * 若将来决定认真对待多人作弊，这里就是该补的闸门。
     *
     * @return 通过校验的目标；任一项不过就返回 null（**按空挥处理**，不是拒绝整次攻击——原版语义如此）
     */
    private static Entity resolveValidTarget(ServerPlayer player, int targetId, ItemStack weapon, InteractionHand hand) {
        if (targetId < 0) return null;
        Entity target = player.serverLevel().getEntity(targetId);
        if (target == null || target == player || !target.isAttackable()) return null;
        // 骑乘链向下（我骑的、它骑的…）：准星对着也不打。与客户端 canHitTarget 同规则，
        // 服务端据此兜底——两边不一致会让客户端选出的目标被服务端判成空挥。
        if (isRideChainDown(player, target)) return null;

        double reach = resolveReach(player, hand, weapon);
        if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > reach * reach) return null;
        return target;
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

    /** 主手**手动点击**允许出手的最低冷却进度——到九成即可出手 */
    public static final float MAIN_CLICK_MIN_SCALE = 0.9f;

    /** 主手**自动攻击**允许出手的最低冷却进度——必须满冷却 */
    public static final float MAIN_AUTO_MIN_SCALE = 1.0f;

    /** 冷却校验——按手，主手按 {@link #MAIN_AUTO_MIN_SCALE} 取满冷却 */
    public static boolean isCooldownReady(Player player, InteractionHand hand, ItemStack weapon) {
        return isCooldownReady(player, hand, weapon, MAIN_AUTO_MIN_SCALE);
    }

    /**
     * 冷却校验——{@code minScale} 是允许出手的最低冷却进度。
     * <p>
     * 只有主手用这个比例：它走原版攻速条（0~1 连续值）。手动点击传 {@link #MAIN_CLICK_MIN_SCALE}
     * （九成即可），自动攻击传 {@link #MAIN_AUTO_MIN_SCALE}（必须满）。
     * 副手走独立物品冷却，只有"就绪/未就绪"两态、没有百分比，{@code minScale} 对它无效。
     */
    public static boolean isCooldownReady(Player player, InteractionHand hand, ItemStack weapon, float minScale) {
        if (hand == InteractionHand.MAIN_HAND) {
            return player.getAttackStrengthScale(0f) >= minScale;
        }
        return isOffhandReady(player);
    }

    /**
     * 服务端主手冷却的宽限 tick 数。
     * <p>
     * 推导：客户端在**自己的**上次出手后 {@code ceil(0.9 × D)} tick 才放行（{@code D = 20 / 攻速}，
     * 见 {@link #MAIN_CLICK_MIN_SCALE}）；服务端从**它处理上一个包**的那一 tick 起算，两次包之间的
     * 实际间隔就是那个值加上到达抖动（±1 tick）。所以门槛取 {@code ceil(0.9 × D) − 本常量}。
     * <p>
     * 取 3 而不是理论最小的 1~2：多出的余量买的是"绝不误拒正常玩家"（BUG-007 就是门槛贴边导致的），
     * 代价是作弊窗口从 1 tick 涨到 3 tick——按作者指示，作弊者不在当前考虑范围。
     */
    private static final int SERVER_COOLDOWN_SLACK_TICKS = 3;

    /**
     * 每个玩家上一次**主手出手**（本模组结算的）所在的服务端 tick。
     * <p>
     * 与 {@link #LAST_HIT} 同款用 WeakHashMap——玩家对象释放即自动回收，不必手工清理。
     */
    private static final Map<Player, Integer> LAST_MAIN_ATTACK_TICK = new WeakHashMap<>();

    /**
     * 服务端主手冷却判定——**以本模组自己的时钟为准，不读原版攻速条**。
     * <p>
     * <b>为什么不读原版那根条：</b>原版 {@code attackStrengthTicker} 会被**任何** swing 包归零，
     * 而客户端只在"真的打空"（{@code Minecraft.startAttack} 的 MISS 分支）时归零自己那根——
     * 于是挖方块、右键用物品、丢物品、举盾释放这一连串与攻击无关的动作都会造成
     * **服务端归零、客户端不归零**的失同步。这是原版**已确认的 bug**，且分了好几条工单挂着：
     * MC-255058（机制本身）/ MC-116510（秒破方块，即"打掉杂草后攻击"）/ MC-118740（右键）/
     * MC-310957（举盾，已在 26.3 Pre-Release 1 修复）。除最后一条外，其余至今 Open、优先级 Low。
     * <p>
     * 本模组原先照抄那根条当**开关**用（原版拿它当伤害系数，我们拿它当放行/拒收），于是同一次
     * 失同步在原版只是"软一刀"，在本模组被放大成**整刀消失 + 白等一个冷却**（BUG-028）。
     * 改为自持时钟后这类污染从结构上不再可能影响本模组——**任何**与攻击无关的挥手都不再干扰冷却。
     * <p>
     * 门槛取**手动档**（九成）而非自动档（满）是因为包本身不带"这次是点击还是自动"的信息，
     * 服务端只能按较宽的那一档收；自动档由客户端把关，服务端这只是防刷下限。
     * 副手走独立物品冷却且由服务端下发，客户端只会**偏保守**（收到冷却包比服务端施加晚），无需余量。
     * <p>
     * <b>首次判定直接放行</b>——玩家还没被本模组结算过攻击（刚上线、刚换武器）时不该被拦。
     * 这正是 BUG-007 想要的"换武器后第一下立刻有伤害"，只是它不再依赖原版归零的时机。
     * 客户端侧的 {@code mainHandSettled} 仍然保留：它现在等的是服务端**属性**追平
     * （伤害与交互距离读的都是服务端侧属性），与冷却无关。
     */
    public static boolean isServerCooldownReady(Player player, InteractionHand hand, ItemStack weapon) {
        if (hand != InteractionHand.MAIN_HAND) {
            return isOffhandReady(player);
        }
        Integer last = LAST_MAIN_ATTACK_TICK.get(player);
        if (last == null) return true;   // 本模组还没结算过这个玩家的攻击
        return player.tickCount - last >= mainHandCooldownTicks(player);
    }

    /** 服务端主手冷却 tick 数——客户端门槛（九成）换算成 tick，再减掉到达抖动宽限 */
    private static int mainHandCooldownTicks(Player player) {
        return Math.max(1, (int) Math.ceil(player.getCurrentItemAttackStrengthDelay() * MAIN_CLICK_MIN_SCALE)
                - SERVER_COOLDOWN_SLACK_TICKS);
    }

    /** 记下一次主手出手——由 {@link #performMainHandAttack} 通过冷却校验后调用（空挥同样计费） */
    private static void markMainHandAttack(Player player) {
        LAST_MAIN_ATTACK_TICK.put(player, player.tickCount);
    }

    /** 冷却施加——按手：主手由 {@link #markMainHandAttack} 记时钟；副手施加独立物品冷却 */
    public static void applyCooldown(ServerPlayer player, InteractionHand hand, ItemStack weapon) {
        if (hand != InteractionHand.OFF_HAND) {
            return;  // 主手冷却在 performMainHandAttack 里记时钟，不在此处理
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
        // 无敌帧豁免：只在"本攻击者刚打过同一个目标"时清零，见 bypassOwnInvulnerability
        bypassOwnInvulnerability(player, target);

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

        // 冲刺击退（"冲刺 + 满蓄力"这一档）：**主副手都给**（作者 2026-09-20 定）。
        //   主手照原版 Player.attack:1243 —— 冲刺 且 攻速条 >0.9（满蓄力）；
        //   副手没有原版语义可对齐，且它的"充能"本来就由短刀自己的冷却（cwc:offhand_cooldown）把关，
        //   所以只判冲刺，**不去读主手那根攻速条**——否则刚砍完主手就出副手刀时会拿不到这 +1，
        //   副手的手感会被主手节奏牵连（副手整套设计的初衷就是两把刀各走各的冷却）。
        // 与原版一致，这一声**在 hurt 之前**就播（哪怕这一下最终被无敌帧吃掉）。
        boolean sprintKnockback = player.isSprinting()
                && (hand == InteractionHand.OFF_HAND || player.getAttackStrengthScale(0.5F) > 0.9F);
        if (sprintKnockback && damage > 0.0F) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_KNOCKBACK, player.getSoundSource(), 1.0F, 1.0F);
        }

        if (damage > 0.0F && target.hurt(source, damage)) {
            // 击退：复刻原版 Player.attack:1292 的合成，见 BUG-033——
            //   主手 = 玩家 ATTACK_KNOCKBACK 属性（已含主手武器自身修正）+ 击退附魔 + 冲刺 +1；
            //   副手 = 武器自身修正（不读玩家属性，与 resolveReach 同思路）+ **副手那把刀自己**的击退附魔 + 冲刺 +1。
            // 附魔取的是"造成这次伤害的那件物品"（原版语义）：主手刀走主手、短刀走副手，两者不互相借用。
            float baseKnockback = (hand == InteractionHand.MAIN_HAND)
                    ? (float) player.getAttributeValue(Attributes.ATTACK_KNOCKBACK)
                    : (float) stackAttributeModifier(weapon, Attributes.ATTACK_KNOCKBACK);
            float knockback = EnchantmentHelper.modifyKnockback(level, weapon, target, source, baseKnockback)
                    + (sprintKnockback ? 1.0F : 0.0F);
            if (knockback > 0.0F && target instanceof LivingEntity living) {
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

        boolean hitAnySecondary = false;
        for (Entity e : level.getEntitiesOfClass(Entity.class, area, CwcCombat::isSweepCandidate)) {
            if (e == player || e == mainTarget) continue;
            if (!e.isAttackable()) continue;
            if (e instanceof ArmorStand armor && armor.isMarker()) continue;
            if (isSweepFriendly(player, e)) continue;
            if (isInPlayerVehicleGroup(player, e)) continue;   // 骑乘组内（向下/向上/同乘）不被横扫卷进来
            // 双区域任一命中 或 准星射线命中（准星攻击范围是一条射线，线内目标均受伤害）+ 视线检测（墙后扫不到）
            if (!isInSweepDualRange(player, e, reach) && !isHitByLookRay(player, e, reach)) continue;
            if (!hasLineOfSight(level, player, e)) continue;
            if (applyDamage(player, e, weapon, hand, PartTypeDef.AttackStyle.SWEEP, false)) {
                hitAnySecondary = true;
            }
        }

        // 主目标复核：几何（双区域或准星射线）**并且**视线，与副目标同一套——但**友军过滤故意不同**：
        // 这里查 canHarmAlly（队友：友伤开着时准星正对能打中，关掉打不到），**不查 isSweepFriendly**——
        // 准星明确指着的东西就是玩家想打的东西，包括自己的宠物（原版行为：主人可以直接攻击自己的宠物）；
        // 只有横扫那种"不是我瞄准的、顺手卷进来"的目标才需要拦自己的宠物。
        // 主目标曾经"完全不查友军"，BUG-016 那轮补成无条件拦队友；2026-09-19 按作者要求改成
        // **听队伍的 friendlyFire**——与单体路径一致（指示器同源，见 canHarmAlly）。
        // 注意副目标那一路**仍然无条件拦队友**：队友只能被正面打中，见 isSweepFriendly。
        boolean hitPrimary = false;
        if (mainTarget != null && canHarmAlly(player, mainTarget)
                && !isRideChainDown(player, mainTarget)   // 骑乘链向下：准星对着也不打（见 isRideChainDown）
                && (isInSweepDualRange(player, mainTarget, reach) || isHitByLookRay(player, mainTarget, reach))
                && hasLineOfSight(level, player, mainTarget)) {
            // primary=true 的分支内部已经扣了 1 点耐久
            hitPrimary = applyDamage(player, mainTarget, weapon, hand, PartTypeDef.AttackStyle.SWEEP, true);
        }

        // 耐久：**一次挥击最多扣 1 点，扫到几个都只扣 1**（与原版横扫一致的量级），
        // 但"扫到了却一次都不扣"是 bug——见下。
        // 副目标走 primary=false，其内部不扣耐久；主目标若没打中（典型情形：准星指着地面/空气，
        // 只有贴身球与周围锥体吃到伤害）则整次挥击**一个耐久都不掉**，等于扫场永远免费。
        // 所以这里补一刀：扫到任何东西但没走 primary 时，按一次挥击扣 1 点。
        if (hitAnySecondary && !hitPrimary) {
            weapon.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }

        // 横扫挥出即播横扫音效 + 粒子（未命中全空也算）；主目标命中音效由 applyDamage 跳过（避免与 SWEEP 叠加）
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), 1.0F, 1.0F);
        player.sweepAttack();
    }

    /**
     * 横扫**副目标**的友军过滤——只认"**我自己的**宠物与坐骑"，不认别人的。
     * <p>
     * 规则（作者定）：
     * <ul>
     *   <li>横扫**不打自己的**宠物 / 坐骑（认主的 {@link OwnableEntity}，owner 就是我）；</li>
     *   <li>横扫**照打别人的**宠物——**包括队友的**，与原版一致（原版打宠物从不看队伍）；</li>
     *   <li>**队友**：副目标**一律不卷进来**（友伤开着也一样）。队友只能靠**准星正对**打中——
     *       那条走主目标路径，由 {@link #canHarmAlly} 按队伍的 {@code friendlyFire} 裁定；</li>
     *   <li>准星**正对**的宠物**照打**：同上，主目标路径。所以本方法**不得**用到主目标判定上。</li>
     * </ul>
     * <p>
     * 为什么排除自己的宠物：原版横扫那行友军过滤是 {@code player.isAlliedTo(宠物)}，而 {@code Player}
     * 从不重写 {@code isAlliedTo}，于是退化成纯粹的计分板队伍判断；真正写着"驯服动物认主人为盟友"的
     * {@code TamableAnimal.isAlliedTo} 只有**从宠物那一侧**问才返回 true，横扫这一侧从来不问。
     * 原版没暴露是因为它的判定盒挂在**被打中的那个目标**身上（{@code target.inflate(1, 0.25, 1)}）
     * 且要求满蓄力真命中——宠物得贴着目标才吃得到。本模组的横扫挂在**玩家自己**身上（锥体 + 贴身球 +
     * 准星射线）且空挥也结算，面积大一个数量级、触发从"命中时"变成"每次挥"，照抄原版那行代码抄不到
     * 原版的体感，宠物会被顺手扫死。故在自己的判定里补上这一侧。
     * <p>
     * 覆盖范围：{@code OwnableEntity} = {@code TamableAnimal}（狼/猫/鹦鹉等）+ {@code AbstractHorse}
     * （马/驴/骡/羊驼/骆驼等）。**别人（含队友）的宠物都不在内**——与原版一致。
     * <p>
     * 客户端提示的已知误差：马的 owner UUID 只写在 NBT 里、**不同步到客户端**，所以客户端
     * {@link #hasSweepTarget} 认不出"这是我的马"，会把它算成一个候选（指示器可能亮、服务端却不结算）。
     * 只影响那一个图标的亮灭，不涉及任何结算；宠物（{@code TamableAnimal}）的 owner UUID 走实体数据同步，无此问题。
     */
    /**
     * 队友能不能被本模组**准星正对**打中——**听队伍的 {@code friendlyFire}**（作者 2026-09-19 的需求）。
     * <p>
     * 规则：**友伤开着 → 准星正对能打中队友；关掉 → 打不到**。
     * 本模组不再**无条件**拦队友，把这件事交回队伍设置——**主目标**（准星正对）与**单体**（短刀/斧）
     * 两条路径因此行为一致。
     * <p>
     * **不含横扫的副目标**：那一路是"顺手卷进来"的范围伤害，队友**一律不卷**（不分友伤开关），
     * 见 {@link #isSweepFriendly}——队友只能被**正面**打中。
     * <p>
     * **只对玩家目标有这道闸**：原版的 {@code canHarmPlayer} 只活在 {@code ServerPlayer.hurt} 里，
     * 受害者必须是玩家；宠物/坐骑不在其中（那类由 {@link #isSweepFriendly} 的"我自己的宠物"规则单独管，
     * 与原版一致——原版打别人的宠物从不看队伍）。
     * <p>
     * 判定方向照抄原版 {@code Player.canHarmPlayer}：读**攻击者**那一队的设置
     * （{@code team.isAlliedTo(对方队伍) ? team.isAllowFriendlyFire() : true}）。
     * <p>
     * 客户端与服务端**共用这一个方法**：指示器（{@link #canHitTarget} / {@link #hasSweepTarget}）
     * 必须预测出同样的结果，否则又回到 BUG-016 那种"指示器说打得到、服务端不让打"的不一致。
     *
     * @return true = 允许命中（含"不是队友"与"是队友但队伍开着友伤"两种）
     */
    private static boolean canHarmAlly(Player player, Entity target) {
        if (!(target instanceof Player)) return true;   // 非玩家目标：没有这道闸
        if (!player.isAlliedTo(target)) return true;    // 不是队友：照打
        Team team = player.getTeam();
        return team != null && team.isAllowFriendlyFire();
    }

    private static boolean isSweepFriendly(Player player, Entity e) {
        if (player.isAlliedTo(e)) return true;   // 队友：副目标**一律**不卷进来（友伤开着也一样）
        return e instanceof OwnableEntity own && own.getOwner() == player;   // 我的宠物 / 坐骑
    }

    // ==================== 骑乘链保护 ====================

    /**
     * 横扫的候选实体——活体，**外加船与矿车**。
     * <p>
     * 载具不在 {@code LivingEntity} 里，所以要显式加进来：作者要求横扫的**范围**伤害同样作用于船和矿车。
     * 它们吃伤害走的是 {@code VehicleEntity.hurt}（与主目标路径同一条），累计伤害 > 40 即报销。
     * <p>
     * 范围伤害**照样受向下链保护**（{@link #isRideChainDown}）：骑在船上时打不到自己这条船，
     * **下来才能打**（这一条与原版一致——原版的准星拾取同样拦着，见 `docs/bugs.md` BUG-031）。
     */
    private static boolean isSweepCandidate(Entity e) {
        return e instanceof LivingEntity || e instanceof Boat || e instanceof AbstractMinecart;
    }

    /**
     * 目标是否在玩家**向下的骑乘链**上——玩家骑的载具、载具的载具……直到链首。
     * <p>
     * 玩家骑在五层猪塔顶上时，这条链就是整座塔（顶猪 → 第 4 层 → … → 底猪）。
     * <p>
     * 链上的目标**完全不可攻击**（主目标被拒，横扫也不卷进来）：准星正对着它们也不打。
     * 依据是作者定的规则。它们与玩家同处一个碰撞位置，永远落在横扫的贴身球里，
     * 不排除就等于"每次挥击都在打自己骑的东西"——骑猪攻击时猪跟着掉血就是这么来的。
     * <p>
     * **船与矿车同样在这一链里**：骑在船上时打不到自己这条船（准星对着也不行），**下船才能打**。
     * <p>
     * **只保护"向下"这一个方向**：我骑的东西不可打，也不被横扫卷进来。
     * 骑在我头上的、和我同乘的**同样不被横扫卷进来**（见 {@link #isInPlayerVehicleGroup}），
     * 但那不是"打不到"——**准星明确对准时可以打**，由 {@link #pickUpperRideChain} 在客户端补齐点选。
     */
    public static boolean isRideChainDown(Player player, Entity e) {
        for (Entity v = player.getVehicle(); v != null; v = v.getVehicle()) {
            if (v == e) return true;
        }
        return false;
    }

    /**
     * 目标是否与玩家**同处一组载具**——向下的骑乘链 **+** 骑在玩家身上的 **+** 同乘的乘客。
     * <p>
     * 这是**横扫副目标**的过滤条件：横扫只打"不在我这条骑乘组里的东西"。
     * 组内的一律不自动卷进来——它们永远落在贴身球里，不排除就等于每次挥击都在打自己人
     * （骑猪时打猪、头上骑人时打人，都是这么来的）。
     * <p>
     * **但组内不是打不到**：作者要的是"下面的人**准星对准时**能打到上面的人"，所以
     * {@link #pickUpperRideChain} 在客户端为向上链单独补了一条射线点选——原版拾取把整条链都过滤了，
     * 不补的话"赖在别人头上"就是完全安全的挂机位。
     * <p>
     * 判定用原版 {@code isPassengerOfSameVehicle}（比根载具）：同一条链上根载具必然相同，
     * 玩家没骑任何东西时根载具是自己，不会误命中。
     */
    public static boolean isInPlayerVehicleGroup(Player player, Entity e) {
        return player.isPassengerOfSameVehicle(e);
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
     * 补一次**向上骑乘链**的准星点选——原版拾取拿不到它们，只有这条路能让"下面的人打到上面的人"成立。
     * <p>
     * 原版 `ProjectileUtil.getEntityHitResult` 按根载具把**整条骑乘链**都过滤掉了
     * （`getRootVehicle() == shooter.getRootVehicle() && !canRiderInteract()`），而原版没有任何实体
     * 重写 `canRiderInteract`，所以"骑在我头上的"永远成不了 {@code mc.hitResult}。
     * 不补的话，"赖在别人头上"就是完全安全的挂机位——这正是作者要堵的洞。
     * <p>
     * 规则的两半互补：**横扫不自动卷到骑乘组内**（{@link #isInPlayerVehicleGroup}，否则每次挥击都在打
     * 头上的人），**准星明确对准时能打到向上链**（本方法）。服务端那边本来就放行——
     * {@link #isRideChainDown} 只管向下的链。
     * <p>
     * **只补向上链**：候选限定为**骑在我身上的**（{@code hasIndirectPassenger}，直接或间接），
     * 向下的链本就不该打、同乘的乘客是原版刻意不许打（见下），都不进候选；其余实体原版拾取已经覆盖，
     * 这里重做一遍会跟原版的方块遮挡、拾取半径等细节漂移。
     * <p>
     * **同乘的乘客（船/骆驼上并排的两个玩家）刻意不在候选里。** 官方工单
     * **MC-236517「You cannot attack players riding the same boat or camel as you」**：
     * 原版确实打不到，而且 Mojang 在 2025-07-03 把它判为 **Works As Intended**（不是 bug）。
     * 所以那种情形保持原版行为——想打同船的人，先让其中一个下船。
     *
     * @return 准星射线命中的最近的**向上链**实体（骑在我身上的，任意深度）；没有则 null
     */
    public static Entity pickUpperRideChain(Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        AABB area = new AABB(eye, eye).inflate(reach);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : player.level().getEntitiesOfClass(Entity.class, area, candidate ->
                candidate != player && !candidate.isSpectator() && candidate.isPickable()
                        && player.hasIndirectPassenger(candidate))) {
            if (!isHitByLookRay(player, e, reach)) continue;
            double dist = e.distanceToSqr(eye);
            if (dist < bestDist) {
                bestDist = dist;
                best = e;
            }
        }
        return best;
    }

    /**
     * 准星目标能否被当前武器（手）命中——与攻击结算同规则，客户端攻击指示器与点选共用。
     * <p>
     * 本方法只做**公共前置**（非自己 / 可攻击 / 不在我向下的骑乘链上），几何与友军规则问该物品
     * {@code attack} 字段胜出的行为（{@link WeaponBehavior#canHit}）：
     * 横扫是双区域或准星射线 + {@link #canHarmAlly 按队伍友伤裁定}，单体是眼睛到碰撞箱 ≤ reach
     * （不排除友军——单体本就可以打友军）。
     * <p>
     * 视线由准星 {@code hitResult} 天然保证（准星射线命中实体前无方块），无需在此重复判定。
     */
    public static boolean canHitTarget(Player player, Entity target, ItemStack weapon, InteractionHand hand) {
        if (target == null || target == player || !target.isAttackable()) return false;
        if (isRideChainDown(player, target)) return false;   // 骑乘链向下：准星对着也不算可命中
        WeaponBehavior attack = BehaviorResolver.behaviorFor(weapon, BehaviorField.ATTACK);
        return attack != null && attack.canHit(player, target, weapon, hand);
    }

    /**
     * 横扫攻击范围内是否存在可攻击目标——客户端攻击指示器用（**只要有能打到的目标就提示，不要求准星对准**）。
     * 与服务端 {@link #applySweep} 副目标同规则：眼位 inflate(reach) 内的 {@link #isSweepCandidate 候选实体}
     * （活体 / 船 / 矿车），双区域或准星射线命中 + 视线 + {@link #isSweepFriendly 非队友且不是自己的宠物}
     * + {@link #isRideChainDown 不在我向下的骑乘链上} + 可攻击。
     */
    public static boolean hasSweepTarget(Player player, ItemStack weapon, InteractionHand hand) {
        Level level = player.level();
        double reach = resolveReach(player, hand, weapon);
        Vec3 eye = player.getEyePosition();
        AABB area = new AABB(eye, eye).inflate(reach);
        for (Entity e : level.getEntitiesOfClass(Entity.class, area, CwcCombat::isSweepCandidate)) {
            if (e == player || !e.isAttackable()) continue;
            if (e instanceof ArmorStand armor && armor.isMarker()) continue;
            if (isSweepFriendly(player, e)) continue;
            if (isInPlayerVehicleGroup(player, e)) continue;   // 骑乘组内（向下/向上/同乘）不被横扫卷进来
            if (!isInSweepDualRange(player, e, reach) && !isHitByLookRay(player, e, reach)) continue;
            if (!hasLineOfSight(level, player, e)) continue;
            return true;
        }
        return false;
    }

    /**
     * 当前武器（手）是否存在**任意可攻击的目标**——攻击指示器"该不该亮"的统一判定，问该物品
     * {@code attack} 字段胜出的行为（{@link WeaponBehavior#hasAnyTarget}）：
     * 横扫 = 攻击范围内存在可攻击实体（不要求准星对准）；单体 = 准星目标可命中。
     *
     * @param crosshairTarget 客户端准星命中的实体（mc.hitResult），单体判定用；横扫忽略
     */
    public static boolean hasAnyAttackableTarget(Player player, ItemStack weapon, InteractionHand hand, Entity crosshairTarget) {
        WeaponBehavior attack = BehaviorResolver.behaviorFor(weapon, BehaviorField.ATTACK);
        return attack != null && attack.hasAnyTarget(player, weapon, hand, crosshairTarget);
    }

    /**
     * 客户端点选目标 → 目标实体 id（{@code -1} = 空挥）。主手与副手两条出手路径共用，
     * 与 {@link #canHitTarget} 同源，避免两边各写一套而漂移。
     * <p>
     * 两步：① 准星命中的实体若**可命中**就用它；② 否则补一次**向上骑乘链**的点选——
     * 原版拾取把整条骑乘链从点选里过滤掉了（{@code ProjectileUtil} 按根载具比），
     * 所以"骑在我头上的"永远成不了准星结果，只能自己补，让"下面的人准星对准时能打到上面的人"成立。
     * <p>
     * <b>①与②是"否则"关系，不是"有准星实体就返回"</b>：准星命中实体但 {@link #canHitTarget} 不通过时
     * （副手短刀刀长不够、横扫目标不满足 {@link #canHarmAlly} 等），仍要回落到②——写成嵌套 if 会静默
     * 删掉"打骑在自己头上的人"那条路。
     * <p>
     * 视线不在这里查：调用方给的 {@code Entity} 来自 {@code mc.hitResult}，那是带方块遮挡的射线结果，
     * 命中实体即天然满足视线。本类在通用包、不能引用客户端类型，故准星实体由调用方传入
     * （同 {@link #hasAnyAttackableTarget}）。
     *
     * @param crosshairTarget 准星命中的实体，没有则 null
     * @return 目标实体 id；{@code -1} 表示空挥（服务端据此只进冷却 + 广播挥动）
     */
    public static int pickAttackTargetId(Player player, ItemStack weapon, InteractionHand hand, Entity crosshairTarget) {
        if (crosshairTarget != null && canHitTarget(player, crosshairTarget, weapon, hand)) {
            return crosshairTarget.getId();
        }
        // 原版拾取把整条骑乘链过滤掉了，这里补一次只针对向上链的点选
        Entity upper = pickUpperRideChain(player, resolveReach(player, hand, weapon));
        return upper != null ? upper.getId() : -1;
    }

    /** 无敌帧豁免窗口——原版受击保护持续 20 tick，超出这个窗口就与本模组自己的上一次命中无关了 */
    private static final int INVULN_BYPASS_WINDOW_TICKS = 20;

    /** 每个攻击者上一次的命中（目标实体 id + 所在 tick）。WeakHashMap 以免玩家退出后残留条目 */
    private static final Map<Player, HitRecord> LAST_HIT = new WeakHashMap<>();

    /** @param targetId 上一次命中的目标实体 id @param tick 命中时所在的世界 tick */
    private record HitRecord(int targetId, long tick) {}

    /**
     * 无敌帧豁免——**只在"同一攻击者连续命中同一目标"时**清零 {@code invulnerableTime}。
     * <p>
     * 设计意图（刻意保留的特性）：让本模组武器自己的快速连击不被原版的受击保护吞掉——双持快节奏依赖它。
     * 但原实现是**无条件**清零这个 public 字段，于是目标刚从**别的来源**（摔落、火焰、其他怪）得到的
     * 保护也被一并抹掉。那不是这个特性的本意。
     * <p>
     * 判定：该攻击者上一次命中就是同一个目标、且在 {@link #INVULN_BYPASS_WINDOW_TICKS} 之内
     * → 说明当前这段 i-frame 很可能就是本模组自己刚打出来的，清零；否则不动，尊重原版保护。
     * <p>
     * 注意第一次命中永远不清零。这不是缺陷：目标若没被近期打过，{@code invulnerableTime} 本来就是 0，
     * {@code LivingEntity.hurt} 的 i-frame 检查（{@code > 10} 才只结算超出部分）不生效，照常全额结算。
     * 只有"目标刚被别的东西打过"这一种情况会退化成原版行为——正是这次要的效果。
     * <p>
     * creative/spectator 玩家的 {@code abilities.invulnerable} 是独立更高的保护层，不受影响。
     */
    private static void bypassOwnInvulnerability(Player attacker, Entity target) {
        long now = attacker.level().getGameTime();
        int targetId = target.getId();
        HitRecord last = LAST_HIT.get(attacker);
        boolean ownRecentHit = last != null
                && last.targetId() == targetId
                && now - last.tick() <= INVULN_BYPASS_WINDOW_TICKS;
        LAST_HIT.put(attacker, new HitRecord(targetId, now));
        if (ownRecentHit) {
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
