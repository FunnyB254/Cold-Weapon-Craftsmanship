package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * 一个"行为"的实现——由装配树按字段与优先级选出（见 {@link BehaviorResolver}），注册在 {@link BehaviorRegistry}。
 * <p>
 * <b>只管事件，不管绘制。</b>HUD 是**另一笔独立的声明**（JSON 里同一个字段对象下的 {@code hud}），
 * 由客户端侧的 HUD 注册表按 hud id 解释。所以本接口天然不引用任何客户端类型——它在通用侧，
 * 引用 {@code GuiGraphics} 会踩 RuntimeDistCleaner（本项目 {@code Layouts} 用 {@code Supplier} 注入就是为躲这条）。
 * <p>
 * 实现必须是**无状态单例**（随 id 注册一次，客户端与服务端共用一个实例）；需要跨 tick 记忆的动作请返回
 * {@link BehaviorMachine}。
 * <p>
 * 三层方法按需覆盖、没有类型继承：一个行为只实现它关心的那几个，于是"瞬时型"（副手出刀）与
 * "保持型"（格挡、将来的蓄力弓）用同一个接口表达。
 */
public interface WeaponBehavior {

    /** 行为 id（注册键；JSON 里 {@code behavior} 字段就是它）。建议带自己的命名空间 */
    String id();

    // —— 客户端半：决定这次按下"要不要接管、发什么" ——

    /**
     * 右键按下时的机会。返回 {@code empty} = **放行原版**（交给原版 {@code use()} / 交互）；
     * 返回 {@code present} = 取消原版，并把这个意图发到服务端。
     */
    default Optional<UseIntent> onClientUse(ClientUse ctx) {
        return Optional.empty();
    }

    /** 按住驱动每 tick 的机会（只对瞬时型动作有意义）。{@code empty} = 本 tick 不出手；冷却门槛由行为自己把关 */
    default Optional<UseIntent> onClientAuto(ClientUse ctx) {
        return Optional.empty();
    }

    // —— 服务端半：收到意图后权威执行 ——

    default void onServerUse(ServerUse ctx) {}

    // —— 保持型动作的原版钩子（两端都会调；由 CwcWeapon 的三个覆写转发过来） ——

    default boolean onUse(UseContext ctx) {
        return false;
    }

    default int useDuration(UseContext ctx) {
        return 0;
    }

    default UseAnim useAnimation(UseContext ctx) {
        return UseAnim.NONE;
    }

    /** 松开右键——保持型动作的"释放点"（蓄力弓的发射就在这里） */
    default void onReleaseUse(UseContext ctx, int remainingTicks) {}

    // —— 全局事件的转发点 ——

    /**
     * 受击结算：返回（可能被改小/改大的）伤害值，不该管就原样返回。
     * <p>
     * **由 {@code CwcCombatEvents} 集中接收事件后转发**——行为类不能自己注册事件（事件只注册一次）。
     * 这样"减伤"这类语义仍归行为自己，而注册点保持集中。
     */
    default float modifyIncomingDamage(UseContext ctx, float amount) {
        return amount;
    }

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
     * 协议上今天仍按现有两个包发送（见 {@code BehaviorDispatch}）。
     */
    record UseIntent(InteractionHand hand, Button button, int targetId) {}
}
