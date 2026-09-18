package com.funnyb.cwc.menu;

import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.crafting.IngredientDef;
import com.funnyb.cwc.crafting.PartRecipes;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartStacks;
import com.funnyb.cwc.registry.CwcMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 制造界面容器——配方浏览 + 库存消耗模式。
 * 配方数据来自 PartDef.recipes()（JSON 内嵌）。
 */
public class CraftingMenu extends AbstractContainerMenu {

    public static final Component TITLE = Component.empty();

    public final InventoryLayout inventoryLayout;
    public final SimpleContainer craftContainer = new SimpleContainer(4);
    private final Inventory playerInventory;

    private String currentPartId;
    private int currentRecipeIndex = 0;

    /** 打开本界面的方块坐标——{@code stillValid} 用它判断玩家是否走远 */
    private final BlockPos tablePos;
    /** 打开时的方块类型——被换成别的方块就关界面 */
    private final Block openBlock;

    /**
     * @param tablePos 打开界面的方块坐标，由服务端经 {@code openMenu(provider, pos)} 写进包里传来
     */
    public CraftingMenu(int containerId, Inventory playerInventory, BlockPos tablePos) {
        super(CwcMenuTypes.CRAFTING.get(), containerId);
        this.playerInventory = playerInventory;
        this.tablePos = tablePos;
        this.openBlock = playerInventory.player.level().getBlockState(tablePos).getBlock();

        var layout = Layouts.craftingMenu();
        this.inventoryLayout = new InventoryLayout(
                "textures/gui/crafting.png",
                layout.image_width, layout.image_height,
                layout.tex_width, layout.tex_height,
                layout.inventory_start_x, layout.inventory_start_y);
        inventoryLayout.addSlots(this::addSlot, playerInventory);

        this.addSlot(new InputSlot(craftContainer, 0, layout.slot_input_0.x, layout.slot_input_0.y));
        this.addSlot(new InputSlot(craftContainer, 1, layout.slot_input_1.x, layout.slot_input_1.y));
        this.addSlot(new InputSlot(craftContainer, 2, layout.slot_input_2.x, layout.slot_input_2.y));

        this.addSlot(new OutputSlot(craftContainer, 3, layout.slot_output.x, layout.slot_output.y,
                p -> {
                    var def = PartRegistry.getPartDef(currentPartId);
                    if (def != null && currentRecipeIndex < def.recipes().size()) {
                        PartRecipes.consumeMaterials(p, def.recipes().get(currentRecipeIndex));
                        updateRecipe();
                    }
                }));
    }

    // ──── 配方管理 ────

    public void selectPart(String partId) {
        this.currentPartId = partId;
        this.currentRecipeIndex = 0;
        updateRecipe();
    }

    public void cycleRecipe() {
        var def = PartRegistry.getPartDef(currentPartId);
        if (def == null || def.recipes().isEmpty()) return;
        currentRecipeIndex = (currentRecipeIndex + 1) % def.recipes().size();
        updateRecipe();
    }

    private void updateRecipe() {
        var def = PartRegistry.getPartDef(currentPartId);
        if (def == null || currentRecipeIndex >= def.recipes().size()) {
            clearCraftSlots();
            return;
        }
        List<IngredientDef> ingredients = def.recipes().get(currentRecipeIndex);
        List<ItemStack> stacks = PartRecipes.toStacks(ingredients);
        for (int i = 0; i < 3; i++) {
            craftContainer.setItem(i, stacks.get(i).copy());
        }

        if (PartRecipes.canCraft(playerInventory.player, ingredients)) {
            // 承载物品（HANDLE_PART / PART）由 PartStacks.itemFor 按类型 role() 统一推导，
            // 不在这里用 id 字符串判定（旧实现是 typeId().contains("handle")，与 WeaponStats 判据不同源）
            craftContainer.setItem(3, PartStacks.partIcon(currentPartId));
        } else {
            craftContainer.setItem(3, ItemStack.EMPTY);
        }

        broadcastChanges();
    }

    private void clearCraftSlots() {
        for (int i = 0; i < 4; i++) {
            craftContainer.setItem(i, ItemStack.EMPTY);
        }
        broadcastChanges();
    }

    /**
     * shift 点击输出槽——每次取一件放入背包并扣一份材料，直到背包装不下或材料不足。
     * <p>
     * 每轮**必须重新校验 canCraft**：循环靠 updateRecipe() 重置输出槽来推进，若只移动副本
     * 而不校验材料，一旦配方损坏（材料解析不出来）导致 canCraft 恒真，就会在一次点击里
     * 把背包刷满成品。额外的迭代上限是兜底，保证即使材料列表异常也一定会终止。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 输出槽在物品栏最后4个槽中的最后一个
        int outputIndex = this.slots.size() - 1;
        if (index != outputIndex) return ItemStack.EMPTY;

        var def = PartRegistry.getPartDef(currentPartId);
        if (def == null || currentRecipeIndex >= def.recipes().size()) return ItemStack.EMPTY;
        var ingredients = def.recipes().get(currentRecipeIndex);

        // 套用原版循环逻辑：每次取一个 → 尝试放入背包 → 成功则扣材料刷新 → 循环
        int maxIterations = Math.max(1, this.slots.size());
        int crafted = 0;
        while (crafted < maxIterations && PartRecipes.canCraft(player, ingredients)) {
            ItemStack output = craftContainer.getItem(3);
            if (output.isEmpty()) break;
            ItemStack toMove = output.copy();
            toMove.setCount(1);
            if (!this.moveItemStackTo(toMove, 0, 36, false)) break;   // 背包满
            PartRecipes.consumeMaterials(player, ingredients);
            crafted++;
            updateRecipe();
        }
        return ItemStack.EMPTY;
    }

    /**
     * 玩家走远或方块被拆后自动关闭界面——与原版工作台同一套
     * （原版 {@code CraftingMenu} 用的是 {@code stillValid(access, player, Blocks.CRAFTING_TABLE)}）。
     * <p>
     * 此前恒返回 true，于是界面永不自动关闭、服务端一直持有菜单状态直到玩家自己按 E。
     * 这里复用原版的静态判定：方块仍是打开时那个 + {@code player.canInteractWithBlock(pos, 4.0)}
     * （按交互距离属性算，不是写死的 64）。
     */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), tablePos), player, openBlock);
    }

    // removed() 不覆写：关闭界面不额外掉落，走普通容器行为
}
