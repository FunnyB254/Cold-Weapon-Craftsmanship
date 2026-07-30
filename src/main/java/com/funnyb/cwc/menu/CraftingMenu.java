package com.funnyb.cwc.menu;

import com.funnyb.cwc.client.Layouts;
import com.funnyb.cwc.crafting.PartRecipe;
import com.funnyb.cwc.crafting.PartRecipes;
import com.funnyb.cwc.registry.CwcMenuTypes;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 制造界面容器——配方浏览 + 库存消耗模式。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>界面打开 → 服务端调用 {@link #initRecipes()} 加载默认配方</li>
 *   <li>输入槽展示配方所需物品（幽灵物品，玩家不可手动取放）</li>
 *   <li>服务端检查玩家背包——材料充足则输出槽出现成品</li>
 *   <li>玩家点击切换按钮 → {@code CycleRecipePacket} → 服务端轮询下一个配方</li>
 *   <li>玩家取出成品 → 自动从背包扣除材料 → 刷新库存检查</li>
 * </ol>
 * 控件坐标从 assets/cwc/gui/crafting_menu.json 读取，资源包可覆盖。
 */
public class CraftingMenu extends AbstractContainerMenu {

    public static final Component TITLE = Component.empty();

    /** 物品栏布局组件——管理 36 个玩家物品槽位 */
    public final InventoryLayout inventoryLayout;

    /** 制造容器——4 格（3 输入 + 1 输出） */
    public final SimpleContainer craftContainer = new SimpleContainer(4);

    /** 玩家背包引用——用于库存检查和材料扣除 */
    private final Inventory playerInventory;

    /** 当前配方索引——在 {@link PartRecipes#getBladeRecipes()} 列表中轮询 */
    private int currentRecipeIndex = 0;

    /**
     * 客户端构造器——通过 FriendlyByteBuf 从服务端同步。
     * NeoForge 自动调用此重载（由 IMenuTypeExtension.create 提供）。
     * 客户端不初始化配方——槽位内容由服务端 broadcastChanges 同步。
     */
    public CraftingMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory);
    }

    /**
     * 服务端 / 通用构造器——构建完整槽位布局。
     * 配方初始化和库存检查需在构造后调用 {@link #initRecipes()}（见 CwcNetwork 处理器）。
     */
    public CraftingMenu(int containerId, Inventory playerInventory) {
        super(CwcMenuTypes.CRAFTING.get(), containerId);
        this.playerInventory = playerInventory;

        var layout = Layouts.craftingMenu();
        this.inventoryLayout = new InventoryLayout(
                "textures/gui/crafting.png",
                layout.image_width, layout.image_height,
                layout.tex_width, layout.tex_height,
                layout.inventory_start_x, layout.inventory_start_y);
        inventoryLayout.addSlots(this::addSlot, playerInventory);

        // 输入槽——只读，程序填入幽灵物品展示配方需求
        this.addSlot(new InputSlot(craftContainer, 0, layout.slot_input_0.x, layout.slot_input_0.y));
        this.addSlot(new InputSlot(craftContainer, 1, layout.slot_input_1.x, layout.slot_input_1.y));
        this.addSlot(new InputSlot(craftContainer, 2, layout.slot_input_2.x, layout.slot_input_2.y));

        // 输出槽——取出时扣除材料并刷新
        this.addSlot(new OutputSlot(craftContainer, 3, layout.slot_output.x, layout.slot_output.y,
                p -> {
                    var recipes = PartRecipes.getBladeRecipes();
                    if (currentRecipeIndex < recipes.size()) {
                        PartRecipes.consumeMaterials(p, recipes.get(currentRecipeIndex));
                        updateRecipe();
                    }
                }));
    }

    // ──── 配方管理（均在服务端调用） ────

    /**
     * 加载默认配方（索引 0）并检查库存。
     * 在服务端打开界面后立即调用。
     */
    public void initRecipes() {
        var recipes = PartRecipes.getBladeRecipes();
        if (!recipes.isEmpty()) {
            currentRecipeIndex = 0;
            updateRecipe();
        }
    }

    /** 切换到下一个配方（循环），更新输入槽和输出槽 */
    public void cycleRecipe() {
        var recipes = PartRecipes.getBladeRecipes();
        if (recipes.isEmpty()) return;
        currentRecipeIndex = (currentRecipeIndex + 1) % recipes.size();
        updateRecipe();
    }

    /**
     * 用当前配方刷新制造容器：
     * <ol>
     *   <li>将配方需求写入输入槽（幽灵物品）</li>
     *   <li>检查玩家背包——材料充足则输出槽放成品，否则清空</li>
     * </ol>
     */
    private void updateRecipe() {
        var recipes = PartRecipes.getBladeRecipes();
        if (currentRecipeIndex >= recipes.size()) {
            clearCraftSlots();
            return;
        }
        PartRecipe recipe = recipes.get(currentRecipeIndex);

        craftContainer.setItem(0, recipe.slot0().copy());
        craftContainer.setItem(1, recipe.slot1().copy());
        craftContainer.setItem(2, recipe.slot2().copy());

        if (PartRecipes.canCraft(playerInventory.player, recipe)) {
            craftContainer.setItem(3, recipe.output().copy());
        } else {
            craftContainer.setItem(3, ItemStack.EMPTY);
        }

        broadcastChanges();
    }

    /** 清空所有制造槽位 */
    private void clearCraftSlots() {
        for (int i = 0; i < 4; i++) {
            craftContainer.setItem(i, ItemStack.EMPTY);
        }
        broadcastChanges();
    }

    // ──── 容器方法 ────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** 容器关闭时清理——掉落输出槽中的成品（输入槽的幽灵物品不掉落） */
    @Override
    public void removed(Player player) {
        super.removed(player);
        ItemStack output = craftContainer.getItem(3);
        if (!output.isEmpty()) {
            player.drop(output, false);
            craftContainer.setItem(3, ItemStack.EMPTY);
        }
    }
}
