package com.funnyb.cwc.combat;

import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * 战斗事件处理——强制攻击冷却（服务器兜底）。
 * <p>
 * 主拦截在客户端输入层（见 {@link com.funnyb.cwc.client.CwcClientEvents#onAttackKey}）：
 * 冷却未满时取消攻击键，根本不发攻击包。此处 AttackEntityEvent 作为服务器兜底防御——
 * 若客户端拦截未生效（第三方客户端/其他攻击入口），在伤害层面阻止冷却未满的攻击。
 * <p>
 * 由 {@link com.funnyb.cwc.ColdWeaponCraftsmanship} 构造器注册到 NeoForge.EVENT_BUS（game bus）。
 */
public class CwcCombatEvents {

    /**
     * 双手武器格挡的**基础**减伤。镡在此之上另加成（见 {@code BLOCK_VALUE}），
     * 所以"不带镡"与"带镡"应当差出一档：基础 25%、带镡 50%。
     */
    private static final float BASE_BLOCK_REDUCTION = 0.25f;

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;              // 服务端权威
        ItemStack weapon = player.getMainHandItem();
        if (weapon.getItem() != CwcItems.HANDLE_PART.get()) return;  // 非 CWC 武器放行 vanilla
        // CWC 主手攻击一律取消 vanilla，转自研管线权威执行（第三方客户端/脚本攻击入口兜底；
        // 正常客户端已在输入层拦截改发 CwcMainHandAttackPacket，不会走到这里）
        event.setCanceled(true);
        if (player instanceof ServerPlayer sp) {
            CwcCombat.performMainHandAttack(sp, event.getTarget().getId());
        }
    }

    /**
     * 双手武器格挡减伤：玩家右键按住格挡中（isUsingItem）时，受到的伤害按
     * 基础 {@link #BASE_BLOCK_REDUCTION} + 镡加成（BLOCK_VALUE）减伤。在护甲计算前触发。
     * <p>
     * 减伤范围对齐原版盾牌：{@code BYPASSES_SHIELD} 类伤害（摔落、火焰、溺水、虚空、饥饿等）
     * 一律不吃格挡——本事件在 {@code LivingEntity.hurt} 最顶部触发，若不排除则会把这些伤害也减半。
     */
    @SubscribeEvent
    public void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;              // 服务端权威
        if (event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) return;  // 绕过护盾的伤害不吃格挡
        if (!player.isUsingItem()) return;                    // 右键按住格挡中
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return;  // 只本模组武器
        if (!CwcWeapon.isTwoHandedStack(stack)) return;       // 只双手武器格挡
        // 格挡加成从装配树现算，不再读 BLOCK_VALUE 组件——物化的派生量会陈旧（改装配件后不更新、
        // 绕过装配台时干脆不存在），而这里每次受击只算一次，代价可忽略
        float reduction = BASE_BLOCK_REDUCTION + CwcWeapon.blockBonus(stack);
        // 上下限都夹：上限 95% 防止无敌；下限 0 防止 BLOCK_VALUE 写成负数时反而放大伤害
        event.setAmount(event.getAmount() * (1 - Mth.clamp(reduction, 0f, 0.95f)));
    }
}
