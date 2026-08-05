package com.funnyb.cwc.combat;

import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

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

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;              // 服务端权威
        // 统一攻击管线：冷却未满则拦截，放行则让 vanilla Player.attack 继续结算伤害
        if (!CwcCombat.interceptMainHandAttack(player, event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /**
     * 双手武器格挡减伤：玩家右键按住格挡中（isUsingItem）时，受到的伤害按
     * 基础 50% + 镡加成（BLOCK_VALUE）减伤。在护甲计算前触发。
     */
    @SubscribeEvent
    public void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;              // 服务端权威
        if (!player.isUsingItem()) return;                    // 右键按住格挡中
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return;  // 只本模组武器
        if (!CwcWeapon.isTwoHandedStack(stack)) return;       // 只双手武器格挡
        float reduction = 0.5f + stack.getOrDefault(CwcDataComponents.BLOCK_VALUE.get(), 0f);
        event.setAmount(event.getAmount() * (1 - Math.min(reduction, 0.95f)));
    }
}
