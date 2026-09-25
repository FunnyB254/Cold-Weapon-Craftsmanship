package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.item.CwcWeapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 右键路径的公共分派——**"这只手的右键此刻该交给谁"只有这一处答案**，客户端与服务端共用。
 * <p>
 * 客户端拿它决定"要不要取消原版、发什么包"（见 {@code CwcClientEvents}），服务端拿它把收到的意图
 * 交给权威实现（见 {@code CwcNetwork}）。两边走同一段前置，所以不会出现"客户端认为是短刀、服务端
 * 认为是别的"这类不一致。
 * <p>
 * <b>这里只做输入层判断</b>（是不是本模组武器底座、这只手有没有被另一只手占用、这个字段有没有胜出行为），
 * "冷却满没满""打谁"归行为自己。
 */
public final class BehaviorDispatch {

    private BehaviorDispatch() {}

    /**
     * 取出这只手右键的胜出行为。
     *
     * @return null = **放行原版**：不是本模组武器底座 / 这一手没有声明行为 / 这只手被另一只手的武器屏蔽
     */
    public static Resolved resolve(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CwcWeapon.isWeaponBase(stack)) return null;
        // 另一只手的武器声明了 disableOffHand（旧 twoHanded 折算）→ 这只手的右键归它
        if (BehaviorResolver.otherHandBlocks(player, hand)) return null;

        BehaviorField field = BehaviorField.useField(hand);
        PartTypeDef.BehaviorDecl decl = BehaviorResolver.declFor(stack, field);
        if (decl == null) return null;
        WeaponBehavior behavior = BehaviorRegistry.get(decl.behavior());
        if (behavior == null) return null;   // 正常不可能：行为 id 在加载期已校验
        return new Resolved(field, stack, decl, behavior);
    }

    /**
     * 服务端半——把客户端的出手意图交给胜出的行为权威执行。
     * <p>
     * 物品栈取**服务端自己那份**（{@code player.getItemInHand(hand)}），不信客户端对物品的任何声称。
     */
    public static void serverUse(ServerPlayer player, InteractionHand hand, int targetId) {
        Resolved resolved = resolve(player, hand);
        if (resolved == null) return;
        resolved.behavior().onServerUse(new WeaponBehavior.ServerUse(
                resolved.field(), player, hand, resolved.stack(), resolved.decl(), targetId));
    }

    /** @param stack 这只手上的物品栈——客户端是预测中的那份，服务端是权威的那份 */
    public record Resolved(BehaviorField field, ItemStack stack, PartTypeDef.BehaviorDecl decl,
                           WeaponBehavior behavior) {}
}
