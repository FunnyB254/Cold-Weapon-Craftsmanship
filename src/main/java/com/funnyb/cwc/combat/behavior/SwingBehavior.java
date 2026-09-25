package com.funnyb.cwc.combat.behavior;

import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 出刀（{@code cwc:swing_use}）——右键**瞬发**一次攻击，走与主手同一条攻击管线（目标解析、伤害、
 * 反馈全部与主手共用 {@code CwcCombat}），只是冷却挂在**它自己的**物品冷却上、与主手完全隔离。
 * <p>
 * 挂载字段：{@code offHandUse}（副手短刀，旧 {@code offhandAttack: true} 折算）与 {@code mainHandUse}
 * （将来"主手右键出刀"的刀类可以直接指过来）。
 * <p>
 * <b>伤害风格不由本行为决定</b>：它读该物品 {@code attack} 字段胜出的行为（{@code CwcCombat.strikeSingle}
 * 内部就这么做），所以"副手拿的是什么刀、按那只手读"这条规则只有一处实现。
 */
public final class SwingBehavior implements WeaponBehavior {

    @Override
    public String id() {
        return BehaviorRegistry.SWING;
    }

    @Override
    public Set<BehaviorField> fields() {
        return EnumSet.of(BehaviorField.OFF_HAND_USE, BehaviorField.MAIN_HAND_USE);
    }

    /**
     * 冷却就绪才接管；未就绪返回 {@code empty} 放行原版——原版 {@code useItem} 对冷却中的物品本来
     * 也只是 pass，所以"放行"在这条路径上是无害的（与迁移前的行为一致）。
     */
    @Override
    public Optional<UseIntent> onClientUse(ClientUse ctx) {
        Player player = ctx.player();
        if (!CwcCombat.isCooldownReady(player, ctx.hand(), ctx.stack())) return Optional.empty();

        // 客户端点选目标（-1 空挥）。判定与服务端结算同源（含骑乘链补点），详见 CwcCombat.pickAttackTargetId
        int targetId = CwcCombat.pickAttackTargetId(player, ctx.stack(), ctx.hand(), ctx.crosshairTarget());

        // 本地先记一次冷却：服务端的冷却要一个往返才同步回来，不补的话按住右键会在空窗期每 tick 重发包，
        // 准星指示器也会滞后一 tick。数值与服务端那份同源（CwcCombat.offhandCooldownTicks）。
        player.getCooldowns().addCooldown(CwcItems.OFFHAND_COOLDOWN.get(), CwcCombat.offhandCooldownTicks(ctx.stack()));

        return Optional.of(new UseIntent(ctx.hand(), Button.USE, targetId));
    }

    @Override
    public void onServerUse(ServerUse ctx) {
        ServerPlayer player = ctx.player();
        ItemStack knife = ctx.stack();
        if (!CwcCombat.isCooldownReady(player, ctx.hand(), knife)) return;   // 服务端权威冷却

        // 单体结算（出刀不做范围攻击——横扫只属于攻击字段声明为 sweep 的那种）
        CwcCombat.strikeSingle(player, ctx.hand(), knife, ctx.targetId());
        CwcCombat.applyCooldown(player, ctx.hand(), knife);

        // 反馈：必须用双参重载 swing(hand,false)——ServerPlayer.swing(hand) 会 resetAttackStrengthTicker
        // 重置主手攻击强度，副手出刀不应影响主手冷却。
        player.swing(ctx.hand(), false);
    }
}
