package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Set;

/**
 * 一个"行为"的实现——由装配树按字段与优先级选出（见 {@link BehaviorResolver}），注册在 {@link BehaviorRegistry}。
 * <p>
 * <b>管事件，也管"这只手此刻该显示什么"，但不管绘制</b>（作者 2026-09-27 定）。
 * 行为的 {@link #hudReadout} 给出**读数**（进度 + 高亮）——那是逻辑半自己的结论；
 * **画成什么样**由数据里的 {@code hud} 键选一个客户端样式（{@code HudStyle}），
 * **画在哪**由哪只手决定。所以本接口天然不引用任何客户端类型——它在通用侧，引用 {@code GuiGraphics}
 * 会踩 RuntimeDistCleaner（本项目 {@code Layouts} 用 {@code Supplier} 注入就是为躲这条）。
 * <p>
 * 实现必须是**无状态单例**（随 id 注册一次，客户端与服务端共用一个实例）；需要跨 tick 记忆的动作请返回
 * {@link BehaviorMachine}。
 * <p>
 * 三簇方法按需覆盖、没有类型继承：一个行为只实现它关心的那几个，于是"瞬时型"（副手出刀）与
 * "保持型"（格挡、将来的蓄力弓）用同一个接口表达。
 *
 * <h2>职责边界</h2>
 * <b>调用方管输入层，行为管"此刻能不能出手"。</b>输入层 = 界面是否打开、是否在划船、哪只手被占用、
 * 手动点击还是按住（冷却门槛的档位不同）——那些由 {@link BehaviorDispatch} 与 {@code CwcClientEvents}
 * 判断；行为只回答"这次按下要不要接管、发什么"与"冷却满没满"。
 * <p>
 * <b>手动点击与按住连发走同一个入口</b>（{@link #onClientUse}），没有单独的"自动"钩子：对内置的
 * 副手出刀而言两条路径的代码本来就一模一样，拆成两个方法只会让实现方忘了写其中一个，
 * 表现为"点得出来、按住不出来"。将来真正需要"保持中"状态的动作请用 {@link #machine}。
 */
public interface WeaponBehavior {

    /** 行为 id（注册键；JSON 里 {@code behavior} 字段就是它）。建议带自己的命名空间 */
    String id();

    /**
     * 这个行为可以挂在哪些字段上——**加载期校验**：把攻击行为写到 {@code mainHandUse} 上会直接让那条
     * 数据加载失败并报错，而不是运行时静默什么都不做（这类错很难查，同未知行为 id 的处理）。
     */
    Set<BehaviorField> fields();

    // —— 客户端半：决定这次按下"要不要接管、发什么" ——

    /**
     * 右键按下时的机会（按住连发也走这里，见类注释）。返回 {@code empty} = **放行原版**
     * （交给原版 {@code use()} / 交互）；返回 {@code present} = 取消原版，并把这个意图发到服务端。
     * <p>
     * <b>冷却门槛由行为自己把关</b>（本模组所有动作的冷却都物化在物品/玩家身上，行为读得到）。
     */
    default Optional<UseIntent> onClientUse(ClientUse ctx) {
        return Optional.empty();
    }

    // —— 服务端半：收到意图后权威执行 ——

    default void onServerUse(ServerUse ctx) {}

    // —— 保持型动作的原版钩子（两端都会调；由 CwcWeapon 的覆写转发过来） ——

    /** 右键按下时是否接管（返回 true = 原版按"已消费"处理，即 {@code InteractionResultHolder.consume}） */
    default boolean onUse(UseContext ctx) {
        return false;
    }

    /** 使用时长（tick）；0 = 不能保持。拿不到手，需要手就用 {@link UseContext#hand()}（{@link #onUse}） */
    default int useDuration(ItemStack stack, LivingEntity entity, PartTypeDef.BehaviorDecl decl) {
        return 0;
    }

    /**
     * 保持中的姿态动画。
     * <p>
     * ⚠ 这个钩子**拿不到手**（原版 {@code Item.getUseAnimation} 的签名只有物品栈），所以调用方按
     * "主手字段优先、其次副手"取声明（见 {@code BehaviorResolver.firstUseDecl}）。内置行为里只有格挡有
     * 动画、而格挡挂在 {@code mainHandUse} 上，故不会歧义。
     */
    default UseAnim useAnimation(ItemStack stack, PartTypeDef.BehaviorDecl decl) {
        return UseAnim.NONE;
    }

    /** 松开右键——保持型动作的"释放点"（蓄力弓的发射就在这里） */
    default void onReleaseUse(UseContext ctx, int remainingTicks) {}

    // —— 全局事件的转发点 ——

    /**
     * 受击结算：返回（可能被改小/改大的）伤害值，不该管就原样返回。
     * <p>
     * **由 {@code CwcCombatEvents} 集中接收事件后转发**——行为类不能自己注册事件（事件只注册一次）。
     * 这样"减伤"这类语义仍归行为自己，而注册点保持集中。只有**玩家正在使用的那件物品**的
     * {@code mainHandUse}/{@code offHandUse} 行为会收到它。
     */
    default float modifyIncomingDamage(UseContext ctx, float amount) {
        return amount;
    }

    // —— 攻击字段（attack）：这件物品的普攻方式 ——

    /**
     * 本攻击方式的结算风格——伤害例程据此决定暴击与音效分支（见 {@code CwcCombat.applyDamage}）。
     * 默认 {@code NORMAL}：单目标全额、稳定直击、无跳劈暴击。
     */
    default PartTypeDef.AttackStyle strikeStyle() {
        return PartTypeDef.AttackStyle.NORMAL;
    }

    /**
     * 准星目标能否命中（客户端指示器与点选共用）。
     * <p>
     * 默认 = 单体几何（眼睛到碰撞箱 ≤ reach）。**公共前置已由 {@code CwcCombat.canHitTarget} 做完**
     * （非自己、可攻击、不在我向下的骑乘链上），所以这里只回答几何与友军规则；服务端结算走的是同一套
     * （{@code CwcCombat.resolveValidTarget} + 本方法），两边不一致会让客户端选出的目标被服务端判成空挥。
     */
    default boolean canHit(Player player, Entity target, ItemStack weapon, InteractionHand hand) {
        return CwcCombat.canHitSingle(player, target, weapon, hand);
    }

    /**
     * 准星处没有实体时，是否**还有别的**该让指示器点亮的目标。
     * <p>
     * ⚠ <b>指示器在准星处有实体时会直接点亮，根本问不到本方法</b>（原版口径，见
     * {@code CwcCombat#hasAnyAttackableTarget} 的说明）。所以本方法负责的只是"准星没指着东西"那一半：
     * 默认 = 准星目标可命中（单体：不打准星就没有目标，于是恒为 false）；横扫覆盖成**范围扫描**
     * （{@code hasSweepTarget}）——CWC 的横扫判定盒挂在玩家自己身上、空挥也结算，
     * 站在怪堆里不看它们也该提示，这是本模组相对原版**多出来**的那一条提示。
     */
    default boolean hasAnyTarget(Player player, ItemStack weapon, InteractionHand hand, Entity crosshairTarget) {
        return CwcCombat.canHitTarget(player, crosshairTarget, weapon, hand);
    }

    /**
     * 服务端权威结算一次出手。
     * <p>
     * 默认 = 单体命中（{@code targetId} 为 -1 或目标校验不过按空挥处理）；横扫覆盖成范围结算
     * （空挥也算范围攻击）。**冷却与挥动反馈不在这里**——它们对任何攻击方式都一样，由调用方
     * （{@code CwcCombat} 的主/副手入口）负责。
     */
    default void strike(ServerPlayer player, InteractionHand hand, ItemStack weapon, int targetId) {
        CwcCombat.strikeSingle(player, hand, weapon, targetId);
    }

    // —— HUD 读数：这只手这个字段"该显示成什么样" ——

    /**
     * 这只手的 HUD 读数——**进度**（0~1，未就绪到就绪）与**高亮**（此刻打不打得到）。
     * <p>
     * <b>为什么在行为上</b>：这两个数是这只手这个字段的**逻辑半自己的结论**。早先它们散在客户端 HUD 里，
     * HUD 拿着物品栈回头调一遍 {@code CwcCombat} 重新解析才知道胜负——同一个问题算两遍，且"挂载字段"
     * 与"判据字段"可以不一致（副手那条挂在 {@code offHandUse} 上却去问 {@code attack}）。
     * 现在由行为自己回答：问的是谁，答的就是谁。
     * <p>
     * <b>样式是另一回事</b>：怎么画这两个数由数据里的 {@code hud} 键选（见 {@code HudStyle}），
     * 所以本方法只给数、不给形。
     * <p>
     * <b>默认实现 = 主手攻击与副手出刀两条今天要的读法，所以内置行为一个都不用覆盖</b>：
     * <ul>
     *   <li>进度：主手读**玩家攻速条**，副手读**它自己的独立冷却**（两把刀各走各的冷却，这是刻意的）；</li>
     *   <li>高亮：这只手能不能打到东西（{@link CwcCombat#hasAnyAttackableTarget}）。主手那条再 AND
     *       一条原版条件，见下。</li>
     * </ul>
     * <p>
     * <b>主手那条还要多一条原版条件</b>：原版画满格图标除"准星拾取到活体 + 蓄力满"外，还要求这件物品的
     * **攻速延迟 &gt; 5**（{@code Gui.java:465}，见 {@link CwcCombat#attackDelayAllowsFullIcon}）。
     * 它是"**这件物品**"的条件而不是"目标"的条件，所以不放进
     * {@link CwcCombat#hasAnyAttackableTarget}（那一个主副手共用），只 AND 在主手这一支上。
     * 副手那条**没有原版对应物**，不受它约束。
     * <p>
     * <b>返回 {@code empty} = 这只手这个字段不画。</b>保持型动作（格挡这类）没有"进度"可言，
     * 将来要给它做提示时覆盖本方法；用 {@code Optional} 而不是"进度 0"是因为
     * {@code HudReadout(0, false)} 会画出一条空进度条——那是"显示但还没就绪"，不是"不显示"。
     */
    default Optional<HudReadout> hudReadout(Player player, InteractionHand hand, ItemStack stack,
                                            Entity crosshairTarget) {
        boolean hasTarget = CwcCombat.hasAnyAttackableTarget(player, stack, hand, crosshairTarget);
        if (hand == InteractionHand.MAIN_HAND) {
            // 主手那条逐条对齐原版：目标条件在 hasAnyAttackableTarget 里，"攻速延迟 > 5"在这儿
            // （它只影响**满格图标**，不影响进度条——原版那根条只由 f < 1.0 决定；绘制那侧本来就是这个结构）
            return Optional.of(new HudReadout(player.getAttackStrengthScale(0.0F),
                    hasTarget && CwcCombat.attackDelayAllowsFullIcon(player)));
        }
        return Optional.of(new HudReadout(CwcCombat.offhandReadiness(player), hasTarget));
    }

    /** 一次 HUD 读数：进度 + 高亮。见 {@link #hudReadout} */
    record HudReadout(float progress, boolean highlight) {}

    // —— 可选状态机 ——

    /** 需要逐 tick 积分的动作返回一个实例；瞬时型/保持型返回 null（见 {@link BehaviorMachine} 的说明） */
    default BehaviorMachine machine(Player player, InteractionHand hand) {
        return null;
    }

    /**
     * @param field           哪个字段调用的——同一个行为类可以同时挂在多个字段上，所以不假设它知道
     * @param crosshairTarget 客户端准星命中的实体（没有则 null）。用 {@link Entity} 而不是
     *                        {@code EntityHitResult}，是为了让本包不依赖客户端类型
     */
    record ClientUse(BehaviorField field, Player player, InteractionHand hand, ItemStack stack,
                     Entity crosshairTarget, PartTypeDef.BehaviorDecl decl) {}

    /** @param targetId 客户端选定的目标实体 id；{@code -1} = 空挥 */
    record ServerUse(BehaviorField field, ServerPlayer player, InteractionHand hand, ItemStack stack,
                     PartTypeDef.BehaviorDecl decl, int targetId) {}

    record UseContext(BehaviorField field, Level level, Player player, InteractionHand hand,
                      ItemStack stack, PartTypeDef.BehaviorDecl decl) {}

    /**
     * 一次"出手意图"。
     * <p>
     * {@code hand} 与 {@code button} 是**参数**而不是包类型——这是"将来左键并进同一套不用改结构"的落点：
     * 协议上今天仍按现有两个包发送（见 {@code CwcClientEvents.sendIntent}）。
     */
    record UseIntent(InteractionHand hand, Button button, int targetId) {}
}
