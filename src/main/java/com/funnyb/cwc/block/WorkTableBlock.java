package com.funnyb.cwc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * CWC 工作方块——右键打开一个容器界面。
 * <p>
 * 制造台（{@code part_crafting_table}）与装配台（{@code assembly_table}）只有"开哪个菜单"不同，
 * 因此合成同一个类，由构造参数决定菜单工厂，避免两份逐行相同的方块实现。
 * <p>
 * 走 vanilla {@code CraftingTableBlock} 同款写法：{@code useWithoutItem} 本身就在服务端被调用，
 * 所以**不需要网络包**，直接 {@code openMenu} 即可（原先经 OpenCrafting/OpenAssembling 包绕一圈
 * 是物品入口才有的限制）。两个菜单的数据源都是各自的 SimpleContainer，因此**也不需要 BlockEntity**。
 */
public class WorkTableBlock extends Block {

    /** 右键要打开的菜单工厂 */
    private final MenuFactory menuFactory;
    /** 菜单标题（两个菜单目前都是空标题） */
    private final Component title;

    public WorkTableBlock(MenuFactory menuFactory, Component title, Properties properties) {
        super(properties);
        this.menuFactory = menuFactory;
        this.title = title;
    }

    /** 右键方块打开界面；潜行时同样打开（没有别的用途） */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;   // 客户端只反馈"动作被消耗了"
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> menuFactory.create(containerId, inventory), title));
        return InteractionResult.CONSUME;
    }

    /** 菜单工厂——用自定义接口而非 BiFunction，避免 int containerId 装箱 */
    @FunctionalInterface
    public interface MenuFactory {
        AbstractContainerMenu create(int containerId, Inventory inventory);
    }
}
