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

    /** 是否短刀武器：底座为手柄，且已装配的刃型声明 offhandAttack（副手右键出刀） */
    public static boolean isOffhandKnife(ItemStack stack) {
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return false;
        String identity = stack.get(CwcDataComponents.PART_IDENTITY.get());
        if (identity == null) return false;
        Map<String, ItemStack> slots = stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (slots == null || slots.isEmpty()) return false;
        for (ItemStack child : slots.values()) {
            String childId = child.get(CwcDataComponents.PART_IDENTITY.get());
            if (childId == null) continue;
            PartDef childDef = PartRegistry.getPartDef(childId);
            PartTypeDef type = childDef != null ? PartRegistry.getTypeDef(childDef.typeId()) : null;
            if (type != null && type.offhandAttack()) return true;
        }
        return false;
    }

    /** 普攻方式（刃型指定）——遍历已装零件找到 attack 刃型，取其 combat.style。未装配/查不到按 NORMAL */
    public static PartTypeDef.AttackStyle attackStyle(ItemStack stack) {
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return PartTypeDef.AttackStyle.NORMAL;
        String identity = stack.get(CwcDataComponents.PART_IDENTITY.get());
        if (identity == null) return PartTypeDef.AttackStyle.NORMAL;
        Map<String, ItemStack> slots = stack.get(CwcDataComponents.ASSEMBLED_SLOTS.get());
        if (slots == null || slots.isEmpty()) return PartTypeDef.AttackStyle.NORMAL;
        for (ItemStack child : slots.values()) {
            String childId = child.get(CwcDataComponents.PART_IDENTITY.get());
            if (childId == null) continue;
            PartDef childDef = PartRegistry.getPartDef(childId);
            PartTypeDef type = childDef != null ? PartRegistry.getTypeDef(childDef.typeId()) : null;
            if (type != null && "attack".equals(type.data().get("type"))) {
                return type.attackStyle();
            }
        }
        return PartTypeDef.AttackStyle.NORMAL;
    }
}
