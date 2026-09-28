package com.funnyb.cwc.menu;

import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.crafting.IngredientDef;
import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartStacks;
import com.funnyb.cwc.crafting.ShapelessCrafting;
import com.funnyb.cwc.registry.CwcMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 零件制造台容器——工作台式：左上是一个**真正可放取**的 3×3 输入格，左下产出，右侧选零件**类型**。
 * <p>
 * 合成规则是**无序**的（材料放哪格都行），数量按**物品总数**算——见 {@link ShapelessCrafting}。
 * 出哪一件由**格子里的材料**决定：在选中类型的所有材质变体里，哪一条配方被格子满足了就出哪个；
 * 同时被满足多条就是**配方冲突**，客户端据此点亮轮换按钮（候选数经 ContainerData 同步过去）。
 * <p>
 * 配方数据仍是 {@code PartDef.recipes()}（JSON 内嵌），**没有数据需要迁移**：三条 ingredient 的旧格式
 * 按无序读就是"这些材料各要几个"，`count` 本来就在里面。
 */
public class CraftingMenu extends AbstractContainerMenu implements ContainerListener {

    public static final Component TITLE = Component.empty();

    /** 输入格是 3×3 */
    public static final int GRID_WIDTH = 3;
    /** 输入格总数 */
    public static final int INPUT_COUNT = GRID_WIDTH * GRID_WIDTH;
    /** 产出在 {@link #craftContainer} 里的索引（0..8 是输入格） */
    public static final int OUTPUT_INDEX = INPUT_COUNT;
    /**
     * 输入格的间距——**硬编码**，与 {@link InventoryLayout} 对玩家物品栏的做法同理：
     * 它必须与制造台贴图上烘焙死的九个槽框逐像素对齐，不是可调项；可调的只有整块的左上角。
     */
    private static final int SLOT_PITCH = 18;

    public final InventoryLayout inventoryLayout;
    /** 0..8 = 3×3 输入格，9 = 产出 */
    public final SimpleContainer craftContainer = new SimpleContainer(INPUT_COUNT + 1);
    private final Inventory playerInventory;

    /** 选中的零件**类型**——不是具体零件：出哪个材质变体由格子里的材料决定 */
    private String currentTypeId;
    /** 格子当前能满足的候选；多于一条就是配方冲突 */
    private List<Match> matches = List.of();
    /** 当前展示第几个候选 */
    private int matchIndex = 0;
    /** 重算期间抑制监听：写产出槽本身也是一次容器变化，不挡就会自触发 */
    private boolean refreshing = false;
    /** 同步给客户端：候选数。客户端只判 "> 1" 来决定轮换按钮能不能点 */
    private final SimpleContainerData candidateData = new SimpleContainerData(1);

    /** 一条候选：某个变体的一条配方被格子满足了 */
    private record Match(String partId, List<IngredientDef> recipe) {}

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
                layout.inventory.x, layout.inventory.y);
        inventoryLayout.addSlots(this::addSlot, playerInventory);

        // 3×3 输入格——**普通 Slot**（可放可取，不做 mayPlace 过滤：原版工作台也不过滤）。
        // 坐标由 input_grid 那一个原点 + 硬编码间距推出来，只写一个坐标是刻意的（同物品栏）
        var grid = layout.input_grid;
        for (int i = 0; i < INPUT_COUNT; i++) {
            this.addSlot(new Slot(craftContainer, i,
                    grid.x + (i % GRID_WIDTH) * SLOT_PITCH,
                    grid.y + (i / GRID_WIDTH) * SLOT_PITCH));
        }

        this.addSlot(new OutputSlot(craftContainer, OUTPUT_INDEX,
                layout.slot_output.x, layout.slot_output.y, p -> takeOne()));

        addDataSlots(candidateData);
        // 格子一变就重新求值（放/取/被 shift 搬动都会走到这里）
        craftContainer.addListener(this);
    }

    // ──── 候选（配方）管理 ────

    /** 客户端在右侧列表选中一个**类型**后调用 */
    public void selectType(String typeId) {
        this.currentTypeId = typeId;
        this.matchIndex = 0;
        refresh();
    }

    /**
     * 切到下一条候选——同一条变体的多条配方、以及"多个变体同时被满足"都走这一个入口。
     * 客户端只在候选 >1（冲突）时才会点亮按钮，所以这里对无冲突的情况直接返回。
     */
    public void cycleRecipe() {
        if (matches.size() <= 1) return;
        matchIndex = (matchIndex + 1) % matches.size();
        refresh();
    }

    /** 格子当前能满足的候选数（>1 = 配方冲突）——客户端据此决定轮换按钮是否可用 */
    public int candidateCount() {
        return candidateData.get(0);
    }

    /** 重新求值：拿格子的当前内容在**选中类型的所有变体**里找能满足的配方，再把产出摆上 */
    private void refresh() {
        refreshing = true;
        try {
            matches = findMatches();
            if (matchIndex >= matches.size()) matchIndex = 0;
            Match match = currentMatch();
            craftContainer.setItem(OUTPUT_INDEX,
                    match == null ? ItemStack.EMPTY : PartStacks.partIcon(match.partId()));
            candidateData.set(0, matches.size());
        } finally {
            refreshing = false;
        }
        broadcastChanges();
    }

    /**
     * 在选中类型的所有材质变体里找出格子能满足的配方。
     * <p>
     * 遍历 {@link PartRegistry#partMap()} 而不是另建索引：变体最多几十个、只在格子变化时跑一次。
     * 顺序即 partMap 的 id 序，稳定可预期（轮换按钮切来切去不会乱跳）。
     */
    private List<Match> findMatches() {
        if (currentTypeId == null) return List.of();
        List<ItemStack> grid = liveGrid();
        List<Match> found = new ArrayList<>();
        for (var entry : PartRegistry.partMap().entrySet()) {
            PartDef def = entry.getValue();
            if (def == null || !currentTypeId.equals(def.type().toString())) continue;
            for (List<IngredientDef> recipe : def.recipes()) {
                if (ShapelessCrafting.matches(recipe, grid)) {
                    // partMap 的键是 ResourceLocation，而 PartStacks.partIcon/id 一路都用字符串，这里就转好
                    found.add(new Match(entry.getKey().toString(), recipe));
                }
            }
        }
        return found;
    }

    private Match currentMatch() {
        return matchIndex >= 0 && matchIndex < matches.size() ? matches.get(matchIndex) : null;
    }

    /**
     * 3×3 九个格子的当前内容——**故意不复制**：{@link ShapelessCrafting#consume} 需要拿到容器里那一份
     * 才能就地扣减（复制出来的副本扣了也白扣）。
     */
    private List<ItemStack> liveGrid() {
        List<ItemStack> grid = new ArrayList<>(INPUT_COUNT);
        for (int i = 0; i < INPUT_COUNT; i++) grid.add(craftContainer.getItem(i));
        return grid;
    }

    /**
     * 取走一件产出：扣掉当前这一炉的材料（**从格子里**扣，不再从背包），再重新求值。
     * 由 {@link OutputSlot#onTake} 触发——鼠标拿和 shift 连取都走这条路。
     */
    private void takeOne() {
        Match match = currentMatch();
        if (match != null) ShapelessCrafting.consume(match.recipe(), liveGrid());
        refresh();
    }

    /**
     * 格子变化 → 重新求值。
     * <p>
     * 两道闸：
     * <ul>
     *   <li>{@code refreshing}——重算时自己会写产出槽，那也是一次容器变化，不挡就会递归；</li>
     *   <li><b>只认服务端</b>——客户端的格子内容是从网络包同步下来的，落下来同样会走这个监听。
     *       但客户端没有 {@code currentTypeId}（选择只往服务端发，不回传），一求值就会算出"无候选"，
     *       把同步来的产出槽和候选数一起抹掉。求值本就只该由服务端做。</li>
     * </ul>
     */
    @Override
    public void containerChanged(Container container) {
        if (container != craftContainer || refreshing) return;
        if (playerInventory.player.level().isClientSide) return;
        refresh();
    }

    /**
     * shift 点击的快速移动——工作台式：背包 ↔ 3×3，产出 → 背包。
     * <p>
     * **不做"放不进就在背包内部换段"那套兜底**（原版工作台会做）：与装配台同一个理由——
     * 那次点击的本意是"放进格子"，物品没进去却被挪到另一个区段纯属副作用。这里干脆什么都不做。
     * <p>
     * 产出的扣材料**不在这里**做：走尾部那句 {@code slot.onTake(...)} → {@link OutputSlot} 的回调，
     * 与鼠标取走同一条路（在这里再做一次就会双扣）。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        int outputIndex = this.slots.size() - 1;
        int gridStart = outputIndex - INPUT_COUNT;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == outputIndex) {
            if (!this.moveItemStackTo(stack, 0, gridStart, true)) return ItemStack.EMPTY;
        } else if (index >= gridStart) {
            // 格子 → 背包
            if (!this.moveItemStackTo(stack, 0, gridStart, false)) return ItemStack.EMPTY;
        } else {
            // 背包 → 先塞 3×3（原版工作台的顺序）
            if (!this.moveItemStackTo(stack, gridStart, outputIndex, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        // 返回"搬动前那份的副本"：原版 doClick 靠它决定要不要再搬一次（shift 连取就靠这条循环）
        return original;
    }

    /**
     * 关界面时把 3×3 里的东西还给玩家——这几格以前是只读的配方展示，现在放的是**真物品**，
     * 不还就是吞物品。装不下就掉在脚下（{@code placeItemBackInInventory} 内部会处理）。
     * <p>
     * 产出槽**必须先丢掉**：它是按格子推出来的展示品，玩家没取走就不该被当成物品发出去
     * （{@code clearContainer} 会连它一起塞给玩家——那等于白送一件成品）。
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        craftContainer.removeItemNoUpdate(OUTPUT_INDEX);
        for (int i = 0; i < INPUT_COUNT; i++) {
            ItemStack stack = craftContainer.removeItemNoUpdate(i);
            if (!stack.isEmpty()) playerInventory.placeItemBackInInventory(stack);
        }
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
}
