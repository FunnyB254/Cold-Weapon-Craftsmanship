package com.funnyb.cwc.screen;

import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.crafting.PartDef;
import com.funnyb.cwc.crafting.PartRegistry;
import com.funnyb.cwc.crafting.PartStacks;
import com.funnyb.cwc.menu.CraftingMenu;
import com.funnyb.cwc.network.serverbound.CycleRecipePacket;
import com.funnyb.cwc.network.serverbound.SelectPartPacket;
import com.funnyb.cwc.registry.CwcDataComponents;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 零件制造界面——工作台式。
 * <p>
 * 布局（crafting.png 176×192，尺寸由 crafting_menu.json 的 image_width/height 决定）：
 * <ul>
 *   <li>左上 **3×3 输入格** + 左下产出槽（都画在贴图上，本类只管列表与按钮）</li>
 *   <li>右侧一整个**零件类型**列表（IconGrid，6 列 × 5 行）</li>
 *   <li>轮换按钮——**只在配方冲突时可用**（见 {@link #render}）</li>
 *   <li>玩家物品栏（36 格）、靠左上的标题</li>
 * </ul>
 * 出哪个材质变体不在这里决定：客户端只说"我在做哪一类"，服务端按格子里的材料算。
 */
public class CraftingScreen extends BaseInventoryScreen<CraftingMenu> {

    /** 右侧零件**类型**列表 */
    private final IconGrid partGrid;
    /** 切换配方（候选）按钮 */
    private ImageButton cycleButton;
    /** 帮助按钮 */
    private ImageButton helpButton;

    /** 当前选中的零件类型 id——界面重建后用来恢复列表高亮 */
    private String selectedTypeId;

    public CraftingScreen(CraftingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, menu.inventoryLayout);
        var grid = Layouts.craftingScreen().part_grid;
        this.partGrid = new IconGrid("grid.cwc.part_list", 0, 0, grid.width, grid.height);
    }

    @Override
    protected void init() {
        super.init();

        // 切换（轮换）按钮
        var cb = Layouts.craftingScreen().cycle_button;
        cycleButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + cb.x_offset, this.topPos + cb.y_offset,
                cb.width, cb.height, GuiIcons.CYCLE,
                () -> PacketDistributor.sendToServer(new CycleRecipePacket())));

        // 帮助按钮——暂留空。位置/尺寸从 JSON 读；两个界面共用 crafting_screen.json 里的那一份，
        // 所以装配界面也读这里（同标题）。x_offset=178 = 面板宽 176 + 2，即贴在面板右缘外 2px。
        var hb = Layouts.craftingScreen().help_button;
        helpButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + hb.x_offset, this.topPos + hb.y_offset,
                hb.width, hb.height, GuiIcons.HELP, () -> {}));

        // 零件类型列表——从 PartRegistry 自动填充；选中即告诉服务端"我在做这一类"
        partGrid.setItems(createPartStacks());
        partGrid.onSelectionChanged(sel -> {
            String typeId = sel.get(CwcDataComponents.PART_IDENTITY.get());
            if (typeId != null) {
                selectedTypeId = typeId;
                PacketDistributor.sendToServer(new SelectPartPacket(typeId));
            }
        });

        restoreSelection();
    }

    /**
     * 界面重建（缩放窗口、切全屏都会重跑 {@code init()}）后恢复选择态。
     * <p>
     * {@link IconGrid#setItems} 是"换一份列表"的语义，会顺手清空选中索引；而重建时服务端的
     * 选择并没有变——不恢复就会出现"产出槽里成品还在、列表却高亮全无"的脱节。
     * 这里按 id 重新定位，**不发包**：服务端本来就是对的，而重发一次会把服务端的候选索引
     * 打回第 0 个（{@code selectType} 会重置轮换位置），玩家刚轮换到的配方就白轮了。
     */
    private void restoreSelection() {
        if (selectedTypeId == null) return;
        partGrid.selectWithoutNotify(
                s -> selectedTypeId.equals(s.get(CwcDataComponents.PART_IDENTITY.get())));
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var lay = Layouts.craftingScreen();
        // 靠左上角、左对齐、【关闭阴影】——与原版逐字一致：原版 AbstractContainerScreen.init()
        // 把 titleLabelX/Y 定为 (8,6)，renderLabels 就是这么画的（drawString 末尾传 false）。
        // 别再改回 drawCenteredString：它没有不带阴影的重载，阴影会在 (+1,+1) 用暗色重画一遍，
        // 在浅色面板上表现为重影。
        Component title = Component.translatable("screen.coldweaponcraftsmanship.craft_parts");
        guiGraphics.drawString(this.font, title, lay.title_x, lay.title_y, lay.title_color, false);
    }

    // ──── 鼠标事件 ────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return partGrid.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return partGrid.mouseClicked(mouseX, mouseY, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return partGrid.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return partGrid.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 渲染背景——先画 GUI 背景贴图，再画类型列表（在 tooltip 之下） */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, partialTick, mouseX, mouseY);
        var gridLayout = Layouts.craftingScreen().part_grid;
        partGrid.setPosition(this.leftPos + gridLayout.x_offset, this.topPos + gridLayout.y_offset);
        partGrid.render(guiGraphics, mouseX, mouseY);
    }

    /**
     * 左侧浮窗显示**产出槽里那件**——也就是"你正要做出来的成品"，它随着格子里的材料变
     * （放铁是铁版本、放铜是铜版本）。产出为空时 {@code getItem} 本就是 EMPTY，浮窗自动隐藏。
     * <p>
     * 与装配台那边"显示底座上那件"是同一个口径：都显示**当前这件东西的数值**。
     */
    @Override
    protected ItemStack infoPanelPart() {
        return this.menu.craftContainer.getItem(CraftingMenu.OUTPUT_INDEX);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 轮换按钮**只在配方冲突时出现**：不冲突就整个藏起来（那块位置露出的就是面板底色）。
        // 用 visible 而不是 active：visible 一关，原版那边连"不画、不响应点击、不报悬停"三件事
        // 一起停掉（AbstractWidget.render/mouseClicked/isMouseOver 都查它），所以用不着再写禁用态，
        // 也不会冒出"禁用按钮的 tooltip"。必须在 super.render 之前设——按钮是在那一轮画的
        cycleButton.visible = this.menu.candidateCount() > 1;

        // super.render() 内顺序：renderBg(背景+grid) → widgets → slots → tooltip
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 自定义 tooltip（优先级：帮助 > 切换按钮 > grid > 输入格）覆盖在最上层
        if (helpButton.isHovered()) {
            guiGraphics.renderComponentTooltip(this.font,
                    List.of(Component.translatable("tooltip.cwc.help")), mouseX, mouseY);
        } else if (cycleButton.isHovered()) {
            guiGraphics.renderComponentTooltip(this.font, buildRecipeTooltip(), mouseX, mouseY);
        } else {
            ItemStack hovered = partGrid.getHoveredItem(mouseX, mouseY);
            if (!hovered.isEmpty()) {
                guiGraphics.renderTooltip(this.font, hovered, mouseX, mouseY);
            } else {
                Slot inputSlot = getHoveredInputSlot();
                if (inputSlot != null && inputSlot.hasItem()) {
                    guiGraphics.renderTooltip(this.font, inputSlot.getItem(), mouseX, mouseY);
                }
            }
        }
    }

    /** 如果原版 hoveredSlot 落在 3×3 输入格里，返回它，否则返回 null */
    private Slot getHoveredInputSlot() {
        // 输入格是 menu.slots 的最后 10 个里的前 9 个（最后一个 size-1 是产出）
        int inputStart = this.menu.slots.size() - 1 - CraftingMenu.INPUT_COUNT;
        int inputEnd = this.menu.slots.size() - 1;
        if (this.hoveredSlot != null
                && this.hoveredSlot.index >= inputStart
                && this.hoveredSlot.index < inputEnd) {
            return this.hoveredSlot;
        }
        return null;
    }

    /** 切换按钮的悬停提示——顺带把"现在有几个候选"说清楚（按钮禁用时这是唯一的解释） */
    private List<Component> buildRecipeTooltip() {
        int candidates = this.menu.candidateCount();
        if (candidates > 1) {
            return List.of(Component.translatable(
                    "tooltip.coldweaponcraftsmanship.cycle_recipe_conflict", candidates));
        }
        return List.of(Component.translatable("tooltip.coldweaponcraftsmanship.cycle_recipe"));
    }

    /**
     * 零件类型列表——按零件的 **{@code type} 字段**去重（零件定义里没有 id，类型是它自己的字段）。
     * 顺序由 {@code partMap()} 保证（按 id 排序），所以列表不会每次刷新都换序。
     */
    private List<ItemStack> createPartStacks() {
        var seen = new HashSet<String>();
        List<ItemStack> stacks = new ArrayList<>();
        for (PartDef def : PartRegistry.partMap().values()) {
            String typeId = def.type().toString();
            if (seen.add(typeId)) {
                stacks.add(PartStacks.typeIcon(typeId));
            }
        }
        return stacks;
    }
}
