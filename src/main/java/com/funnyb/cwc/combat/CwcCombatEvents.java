package com.funnyb.cwc.combat;

import com.funnyb.cwc.combat.behavior.BehaviorField;
import com.funnyb.cwc.combat.behavior.BehaviorRegistry;
import com.funnyb.cwc.combat.behavior.BehaviorResolver;
import com.funnyb.cwc.combat.behavior.WeaponBehavior;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * 战斗事件处理——强制攻击冷却（服务器兜底）与**受击转发**。
 * <p>
 * 主拦截在客户端输入层（见 {@link com.funnyb.cwc.client.CwcClientEvents#onAttackKey}）：
 * 冷却未满时取消攻击键，根本不发攻击包。此处 AttackEntityEvent 作为服务器兜底防御——
 * 若客户端拦截未生效（第三方客户端/其他攻击入口），在伤害层面阻止冷却未满的攻击。
 * <p>
 * 两个事件的处理器**都不自己实现动作**：攻击转发给攻击管线（{@link CwcCombat#performMainHandAttack}），
 * 减伤转发给正在使用的那件物品所属的行为（{@link WeaponBehavior#modifyIncomingDamage}）。
 * 行为类不能自己注册事件（事件只注册一次），这里是唯一的转发点。
 * <p>
 * 由 {@link com.funnyb.cwc.ColdWeaponCraftsmanship} 构造器注册到 NeoForge.EVENT_BUS（game bus）。
 */
public class CwcCombatEvents {

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
     * 受击转发——把伤害交给**玩家正在使用的那件物品**在这一手对应的行为（格挡的减伤就在其中，
     * 见 {@code BlockBehavior.modifyIncomingDamage}）。在护甲计算前触发。
     * <p>
     * 减伤范围对齐原版盾牌：{@code BYPASSES_SHIELD} 类伤害（摔落、火焰、溺水、虚空、饥饿等）
     * 一律不吃——本事件在 {@code LivingEntity.hurt} 最顶部触发，若不排除则会把这些伤害也减半。
     * 这条属于"转发层的过滤"，不属于任何行为：所有保持型动作对齐的都是原版盾牌的口径。
     */
    @SubscribeEvent
    public void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;              // 服务端权威
        if (event.getSource().is(DamageTypeTags.BYPASSES_SHIELD)) return;  // 绕过护盾的伤害不吃
        if (!player.isUsingItem()) return;                    // 右键按住中才可能有保持型动作

        // 读**正在使用的**那件物品与那只手，而不是假定主手：保持型动作可以由副手声明
        // （offHandUse），拿主手的物品去问会问错人。物品本身必须是本模组武器底座。
        ItemStack stack = player.getUseItem();
        InteractionHand hand = player.getUsedItemHand();
        BehaviorField field = BehaviorField.useField(hand);
        PartTypeDef.BehaviorDecl decl = BehaviorResolver.declFor(stack, field);
        WeaponBehavior behavior = decl == null ? null : BehaviorRegistry.get(decl.behavior());
        if (behavior == null) return;                         // 不是本模组的保持型动作（原版盾牌、拉弓…）

        event.setAmount(behavior.modifyIncomingDamage(new WeaponBehavior.UseContext(
                field, player.level(), player, hand, stack, decl), event.getAmount()));
    }
}
