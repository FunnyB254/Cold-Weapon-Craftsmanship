package com.funnyb.cwc.combat.behavior;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 可选的行为状态机——**只有需要逐 tick 积分的动作才实现它**（例如将来的蓄力弓：蓄力中要减速、要画进度）。
 * <p>
 * <b>不要给"状态本来就归原版持有"的动作写机器。</b>典型反例是格挡：它的状态就是原版的
 * {@code isUsingItem()} 与使用时长，自己再记一个"正在格挡"的字段就是**复刻原版状态**，
 * 属于架构债（见 {@code docs/bugs.md} 的 ARCH-6）。这类动作的 {@link WeaponBehavior#occupies}
 * 应当**读原版**，而不是自己记。
 * <p>
 * 现有两个行为（格挡、副手出刀）都不需要它，所以本接口目前**零实现**。
 */
public interface BehaviorMachine {

    /** 每 tick 推进一次（客户端与服务端各自推进自己那份） */
    void tick(Player player, InteractionHand hand, ItemStack stack, BehaviorDecl decl);

    /** 是否处于"占用这只手"的相位——占用期间另一只手的启动会被拒绝 */
    boolean occupies(Player player, InteractionHand hand);

    /** 丢弃（换武器 / 松手 / 退出世界）：需要收尾的动作在这里做 */
    void discard();
}
