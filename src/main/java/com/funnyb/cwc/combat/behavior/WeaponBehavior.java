package com.funnyb.cwc.combat.behavior;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * 一个"动作"的实现——由装配树按优先级选出（见 {@link BehaviorResolver}），注册在 {@link BehaviorRegistry}。
 * <p>
 * 三层方法，按需覆盖，**没有类型继承**：一个行为只实现它关心的那几个。这样"瞬时型"（副手出刀）与
 * "保持型"（格挡、将来的蓄力弓）用同一个接口表达，不需要两套注册表。
 * <p>
 * <b>实现必须是无状态单例</b>（随 id 注册一次），且**不得引用任何客户端类型**——本包在通用侧，
 * 客户端与服务端共用一个实现。需要跨 tick 记忆的动作请返回 {@link BehaviorMachine}。
 * <p>
 * ⚠ <b>这个接口现在只承载右键两个动作</b>，但形状是按"（手 × 键位）"设计的：左键普攻并进同一套时
 * 不需要改这里的任何签名。
 */
public interface WeaponBehavior {

    /** 行为 id（注册键；JSON 里的 {@code behavior} 字段就是它）。建议带命名空间 */
    String id();

    // —— 客户端半：决定这次按下"要不要接管、发什么" ——

    /**
     * 右键按下时的机会。返回 empty 表示**放行原版**（交给原版 {@code use()} / 交互），
     * 返回 present 表示取消原版并把这个意图发到服务端。
     */
    default Optional<UseIntent> onClientUse(ClientUse ctx) {
        return Optional.empty();
    }

    /**
     * 按住驱动每 tick 的机会（只对瞬时型动作有意义）。返回 empty = 本 tick 不出手；
     * 冷却之类的门槛由行为自己在这里把关。
     */
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

    /** 松开右键（保持型动作的"释放点"——蓄力弓的发射就在这里） */
    default void onReleaseUse(UseContext ctx, int remainingTicks) {}

    // —— 仲裁 + HUD（都是数据，不含绘制） ——

    /**
     * 是否处于"占用这只手"的相位——占用期间另一只手的启动会被拒绝（见 {@link Arbiter}）。
     * <p>
     * <b>状态归原版持有的动作必须读原版</b>（例如格挡用 {@code player.isUsingItem() && getUsedItemHand() == hand}），
     * 不要自己记一个字段，否则就是复刻原版状态（ARCH-6）。
     */
    default boolean occupies(Player player, InteractionHand hand) {
        return false;
    }

    /** 想显示的 HUD 提示；empty = 这只手不画东西。只给数据，位置由 HUD 层决定 */
    default Optional<HudCue> hud(Player player, InteractionHand hand) {
        return Optional.empty();
    }

    // —— 可选状态机 ——

    /** 需要逐 tick 积分的动作返回一个实例；瞬时型/保持型返回 null（见 {@link BehaviorMachine} 的说明） */
    default BehaviorMachine machine(Player player, InteractionHand hand) {
        return null;
    }

    /**
     * @param crosshairTarget 客户端准星命中的实体（没有则 null）。用 {@link Entity} 而不是
     *                        {@code EntityHitResult}，是为了让本包不依赖客户端类型
     */
    record ClientUse(Player player, InteractionHand hand, ItemStack stack, Entity crosshairTarget,
                     BehaviorDecl decl) {}

    /** @param targetId 客户端选定的目标实体 id；{@code -1} = 空挥 */
    record ServerUse(ServerPlayer player, InteractionHand hand, ItemStack stack, BehaviorDecl decl,
                     int targetId) {}

    record UseContext(Level level, Player player, InteractionHand hand, ItemStack stack,
                      BehaviorDecl decl) {}

    /**
     * 一次"出手意图"。
     * <p>
     * {@code hand} 与 {@code button} 是**参数**而不是包类型——这是"将来左键并进来不用改结构"的落点：
     * 协议上今天仍按现有两个包发送（见 {@code BehaviorDispatch}）。
     */
    record UseIntent(InteractionHand hand, Button button, int targetId) {}
}
