package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.crafting.AssemblyTree;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 双手仲裁——"另一只手是不是正占着手"的**唯一实现**。
 * <p>
 * 取代原先散在七处的 {@code CwcWeapon.isTwoHandedStack(主手)}：那个谓词把两个不同的问题混在了一起——
 * "格挡这个动作是什么"（行为侧）与"副手能不能启动"（仲裁侧）。拆开之后行为侧问
 * {@code selection.behavior().occupies(...)}，仲裁侧只问这里。
 * <p>
 * 等价关系（迁移时要逐条对照）：{@code otherHandBlocks(OFF_HAND)} ≡ 旧的
 * {@code isTwoHandedStack(主手) && 主手正在使用中}。唯一不同的是"主手是**非 CWC** 物品且正在被使用"
 * （吃东西、拉弓）——那在今天是不可达的（原版主手消费掉右键时，副手那一轮根本不会发生），
 * 但这属于要在游戏里确认的项，不是可以想当然的假设。
 */
public final class Arbiter {

    private Arbiter() {}

    /**
     * 这只手是否被某个胜出行为占住。
     * <p>
     * 刻意**不自己记状态**，而是问行为——状态归原版持有的动作（格挡）就该从原版读得到。
     */
    public static boolean occupied(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return false;
        BehaviorResolver.Selection selection = BehaviorResolver.select(AssemblyTree.of(stack), hand, Button.USE);
        return selection != null && selection.behavior().occupies(player, hand);
    }

    /** 另一只手是否占着手——双手互斥的唯一判据 */
    public static boolean otherHandBlocks(Player player, InteractionHand hand) {
        return occupied(player, hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND);
    }
}
