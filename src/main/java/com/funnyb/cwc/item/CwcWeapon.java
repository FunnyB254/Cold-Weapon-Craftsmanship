package com.funnyb.cwc.item;

import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.registry.CwcDataComponents;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * CWC 组装武器——继承 TieredItem。
 * 双手武器：右键进入格挡（使用状态），屏蔽副手交互；单手武器保持原版行为（右键留给副手）。
 */
public class CwcWeapon extends TieredItem {

    public CwcWeapon(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /** 双手武器：右键开始使用（格挡）；单手武器 pass（不占右键，副手交互保留） */
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
}
