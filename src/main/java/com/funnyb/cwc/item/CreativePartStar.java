package com.funnyb.cwc.item;

import com.funnyb.cwc.screen.CreativePartStarScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 创造零件之星——CWC 模组的核心交互物品。
 * 右键使用时在客户端打开选择界面（制造零件 / 组装零件），最大堆叠 1。
 */
public class CreativePartStar extends Item {

    public CreativePartStar(Properties properties) {
        super(properties);
    }

    /** 右键时在客户端打开选择界面，返回成功以消耗右键动作 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new CreativePartStarScreen());
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
