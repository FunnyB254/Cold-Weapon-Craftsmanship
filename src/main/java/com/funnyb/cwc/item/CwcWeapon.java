package com.funnyb.cwc.item;

import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.registry.CwcDataComponents;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * CWC 组装武器——继承 TieredItem。
 * 只保留武器本体行为：双手武器右键格挡、武器身份判断（双手/短刀）。
 * 攻击行为（主手强制冷却、副手短刀出刀）统一走 {@link com.funnyb.cwc.combat.CwcCombat} 管线。
 */
public class CwcWeapon extends TieredItem {

    public CwcWeapon(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /**
     * 右手：双手武器右键进入格挡；单手武器 pass（右键留给副手/其他物品）。
     * 副手短刀的右键攻击由客户端拦截改发 CwcOffhandAttackPacket，不进这里。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isTwoHandedStack(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 双手武器使用时长 72000 tick（持续按住 = 持续格挡） */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return isTwoHandedStack(stack) ? 72000 : 0;
    }

    /** 双手武器借原版格挡姿态动画作为视觉反馈 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return isTwoHandedStack(stack) ? UseAnim.BLOCK : UseAnim.NONE;
    }

    /** 是否双手武器底座（类型标记 twoHanded）——供格挡 use 与格挡减伤判断复用 */
    public static boolean isTwoHandedStack(ItemStack stack) {
        String identity = stack.get(CwcDataComponents.PART_IDENTITY.get());
        if (identity == null) return false;
        PartDef def = PartRegistry.getPartDef(identity);
        if (def == null) return false;
        PartTypeDef typeDef = PartRegistry.getTypeDef(def.typeId());
        return typeDef != null && typeDef.twoHanded();
    }

    /** 是否短刀武器：底座为手柄，且装配树中存在声明 offhandAttack 的零件（副手右键出刀） */
    public static boolean isOffhandKnife(ItemStack stack) {
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return false;
        return hasOffhandAttack(stack);
    }

    /** 深度优先找 offhandAttack 声明：先看本节点，再递归子件（与 WeaponStats 的递归口径一致） */
    private static boolean hasOffhandAttack(ItemStack stack) {
        PartTypeDef type = typeOf(stack);
        if (type == null) return false;
        if (type.offhandAttack()) return true;
        Map<String, ItemStack> children = stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (children == null) return false;
        for (ItemStack child : children.values()) {
            if (hasOffhandAttack(child)) return true;
        }
        return false;
    }

    /**
     * 普攻方式（刃型指定）——深度优先取第一个 attack 刃型的 combat.style，先看底座自身再递归子树。
     * 非手柄/未装配/查不到按 NORMAL。
     */
    public static PartTypeDef.AttackStyle attackStyle(ItemStack stack) {
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return PartTypeDef.AttackStyle.NORMAL;
        PartTypeDef.AttackStyle style = findAttackStyle(stack);
        return style != null ? style : PartTypeDef.AttackStyle.NORMAL;
    }

    /** 深度优先找 attack 型的 style：本节点是就取它，否则递归子件；都没有返回 null */
    private static PartTypeDef.AttackStyle findAttackStyle(ItemStack stack) {
        PartTypeDef type = typeOf(stack);
        if (type == null) return null;
        if ("attack".equals(type.data().get("type"))) return type.attackStyle();
        Map<String, ItemStack> children = stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (children == null) return null;
        for (ItemStack child : children.values()) {
            PartTypeDef.AttackStyle s = findAttackStyle(child);
            if (s != null) return s;
        }
        return null;
    }

    /** 读某栈的零件类型定义，无 PART_IDENTITY 或查不到返回 null */
    private static PartTypeDef typeOf(ItemStack stack) {
        String identity = stack.get(CwcDataComponents.PART_IDENTITY.get());
        if (identity == null) return null;
        PartDef def = PartRegistry.getPartDef(identity);
        return def == null ? null : PartRegistry.getTypeDef(def.typeId());
    }
}
