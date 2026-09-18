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
 * 制造界面——CWC 的核心 GUI 界面。
 * <p>
 * 布局：
 * <ul>
 *   <li>背景贴图 256×180（crafting.png）</li>
 *   <li>玩家物品栏（36 格）</li>
 *   <li>3 个输入槽 + 1 个输出槽（配方浏览模式）</li>
 *   <li>切换配方按钮——悬停显示当前配方详情</li>
 *   <li>右侧可滚动零件列表 + 左侧材料列表（IconGrid）</li>
 *   <li>标题文字 "制造零件"</li>
 * </ul>
 */
public class CraftingScreen extends BaseInventoryScreen<CraftingMenu> {

    /** 右侧零件列表 */
    private final IconGrid partGrid;
    /** 左侧材料列表 */
    private final IconGrid materialGrid;
    /** 切换配方按钮 */
    private ImageButton cycleButton;
    /** 帮助按钮 */
    private ImageButton helpButton;

    /** 当前选中的零件类型 id——界面重建后用来恢复两侧列表的高亮 */
    private String selectedTypeId;
    /** 当前选中的材质（零件 id）——同上；换类型时清空，因为旧材质已不在新列表里 */
    private String selectedPartId;

    public CraftingScreen(CraftingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, menu.inventoryLayout);
        var grid = Layouts.craftingScreen().part_grid;
        this.partGrid = new IconGrid("grid.cwc.part_list", 0, 0, grid.width, grid.height);
        this.materialGrid = new IconGrid("grid.cwc.material_list", 0, 0, 53, 79);
    }

    @Override
    protected void init() {
        super.init();

        // 切换配方按钮
        var cb = Layouts.craftingScreen().cycle_button;
        cycleButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + cb.x_offset, this.topPos + cb.y_offset,
                cb.width, cb.height, GuiIcons.CYCLE,
                () -> PacketDistributor.sendToServer(new CycleRecipePacket())));

        // 帮助按钮——暂留空
        helpButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + 212, this.topPos + 13, 16, 16, GuiIcons.HELP, () -> {}));

        // 零件列表——从 PartRegistry 自动填充
        partGrid.setItems(createPartStacks());
        partGrid.onSelectionChanged(sel -> {
            String typeId = sel.get(CwcDataComponents.PART_IDENTITY.get());
            if (typeId != null) {
                selectedTypeId = typeId;
                selectedPartId = null;      // 换了类型，旧材质已不在新列表里
                materialGrid.setItems(getMaterialVariants(typeId));
            }
        });
        materialGrid.onSelectionChanged(sel -> {
            String partId = sel.get(CwcDataComponents.PART_IDENTITY.get());
            if (partId != null) {
                selectedPartId = partId;
                PacketDistributor.sendToServer(new SelectPartPacket(partId));
            }
        });

        restoreSelection();
    }

    /**
     * 界面重建（缩放窗口、切全屏都会重跑 {@code init()}）后恢复选择态。
     * <p>
     * {@link IconGrid#setItems} 是"换一份列表"的语义，会顺手清空选中索引，而重建时服务端的
     * 当前配方并没有变——不恢复就会出现"输出槽里成品还在、两列却高亮全无"的脱节，玩家得重点
     * 一次才对得上。这里按 id 重新定位，**不发包**：服务端本来就是对的。
     */
    private void restoreSelection() {
        if (selectedTypeId == null) return;
        materialGrid.setItems(getMaterialVariants(selectedTypeId));
        partGrid.selectWithoutNotify(
                s -> selectedTypeId.equals(s.get(CwcDataComponents.PART_IDENTITY.get())));
        if (selectedPartId != null) {
            materialGrid.selectWithoutNotify(
                    s -> selectedPartId.equals(s.get(CwcDataComponents.PART_IDENTITY.get())));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var lay = Layouts.craftingScreen();
        // 居中绘制且【关闭阴影】：drawCenteredString 内部强制 dropShadow=true，
        // 会在 (+1,+1) 处用暗色重画一遍，在浅色面板上表现为重影。
        // 原版 AbstractContainerScreen.renderLabels 画标题同样传 false。
        Component title = Component.translatable("screen.coldweaponcraftsmanship.craft_parts");
        guiGraphics.drawString(this.font, title,
                lay.title_x - this.font.width(title) / 2, lay.title_y, lay.title_color, false);
    }

    // ──── 鼠标事件 ────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return partGrid.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                || materialGrid.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return partGrid.mouseClicked(mouseX, mouseY, button)
                || materialGrid.mouseClicked(mouseX, mouseY, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return partGrid.mouseReleased(mouseX, mouseY, button)
                || materialGrid.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return partGrid.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || materialGrid.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 渲染背景——先画 GUI 背景贴图，再画零件/材料列表（在 tooltip 之下） */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, partialTick, mouseX, mouseY);
        materialGrid.setPosition(this.leftPos + 47, this.topPos + 14);
        materialGrid.render(guiGraphics, mouseX, mouseY);

        var gridLayout = Layouts.craftingScreen().part_grid;
        partGrid.setPosition(this.leftPos + gridLayout.x_offset, this.topPos + gridLayout.y_offset);
        partGrid.render(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // super.render() 内顺序：renderBg(背景+grid) → widgets → slots → tooltip
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 自定义 tooltip（优先级：帮助 > 切换按钮 > grid）覆盖在最上层
        if (helpButton.isHovered()) {
            guiGraphics.renderComponentTooltip(this.font,
                    List.of(Component.translatable("tooltip.cwc.help")), mouseX, mouseY);
        } else if (cycleButton.isHovered()) {
            guiGraphics.renderComponentTooltip(this.font, buildRecipeTooltip(), mouseX, mouseY);
        } else {
            ItemStack hovered = partGrid.getHoveredItem(mouseX, mouseY);
            if (hovered.isEmpty()) {
                hovered = materialGrid.getHoveredItem(mouseX, mouseY);
            }
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

    /** 如果原版 hoveredSlot 是输入槽（索引 0~2），返回它，否则返回 null */
    private Slot getHoveredInputSlot() {
        // 输入槽在 menu.slots 的最后 4 个中：索引 size-4, size-3, size-2（size-1 是输出槽）
        int inputStart = this.menu.slots.size() - 4;
        int inputEnd = this.menu.slots.size() - 1;
        if (this.hoveredSlot != null
                && this.hoveredSlot.index >= inputStart
                && this.hoveredSlot.index < inputEnd) {
            return this.hoveredSlot;
        }
        return null;
    }

    /**
     * 构建切换按钮的悬停提示。
     */
    private List<Component> buildRecipeTooltip() {
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

    /** 选中零件类型后，列出该类型下所有材料变体——建图标需要 id，所以遍历 id→定义 */
    private List<ItemStack> getMaterialVariants(String typeId) {
        List<ItemStack> variants = new ArrayList<>();
        PartRegistry.partMap().forEach((id, def) -> {
            if (def.type().toString().equals(typeId)) {
                variants.add(PartStacks.partIcon(id.toString()));
            }
        });
        return variants;
    }
}
