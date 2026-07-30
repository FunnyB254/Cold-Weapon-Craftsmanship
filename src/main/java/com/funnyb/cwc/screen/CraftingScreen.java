package com.funnyb.cwc.screen;

import com.funnyb.cwc.client.Layouts;
import com.funnyb.cwc.menu.CraftingMenu;
import com.funnyb.cwc.network.serverbound.CycleRecipePacket;
import com.funnyb.cwc.registry.CwcItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

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
    /** 切换配方按钮——悬停时显示配方需求详情 */
    private ImageButton cycleButton;

    public CraftingScreen(CraftingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, menu.inventoryLayout);
        var grid = Layouts.craftingScreen().part_grid;
        this.partGrid = new IconGrid("grid.cwc.part_list", 0, 0, grid.width, grid.height);
        this.materialGrid = new IconGrid("grid.cwc.material_list", 0, 0, 53, 79);
    }

    @Override
    protected void init() {
        super.init();

        // 切换配方按钮——位置和尺寸从 JSON 布局读取
        var cb = Layouts.craftingScreen().cycle_button;
        cycleButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + cb.x_offset, this.topPos + cb.y_offset,
                cb.width, cb.height,
                () -> PacketDistributor.sendToServer(new CycleRecipePacket())));

        // 零件列表——使用铁标准刃的贴图，悬停框显示标准刃的语言文件
        ItemStack bladeStack = new ItemStack(CwcItems.PART.get());
        bladeStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.translatable("type.cwc.standard_blade"));
        partGrid.setItems(List.of(bladeStack));
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var lay = Layouts.craftingScreen();
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.coldweaponcraftsmanship.craft_parts"),
                lay.title_x, lay.title_y, lay.title_color);
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 左侧材料列表
        materialGrid.setPosition(this.leftPos + 47, this.topPos + 8);
        materialGrid.render(guiGraphics, mouseX, mouseY);

        // 右侧零件列表
        var gridLayout = Layouts.craftingScreen().part_grid;
        partGrid.setPosition(this.leftPos + gridLayout.x_offset, this.topPos + gridLayout.y_offset);
        partGrid.render(guiGraphics, mouseX, mouseY);

        // tooltip（优先级：按钮 > 零件列表 > 材料列表 > 输入槽幽灵物品）
        if (cycleButton.isHovered()) {
            guiGraphics.renderComponentTooltip(this.font, buildRecipeTooltip(), mouseX, mouseY);
        } else {
            ItemStack hovered = partGrid.getHoveredItem(mouseX, mouseY);
            if (hovered.isEmpty()) {
                hovered = materialGrid.getHoveredItem(mouseX, mouseY);
            }
            if (!hovered.isEmpty()) {
                guiGraphics.renderTooltip(this.font, hovered, mouseX, mouseY);
            } else {
                // 复用原版 hoveredSlot——不再手动遍历 hitbox
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
     * 构建切换按钮的悬停提示——仅显示"切换配方"。
     * 材料需求已通过输入槽的幽灵物品展示，无需在 tooltip 中重复。
     */
    private List<Component> buildRecipeTooltip() {
        return List.of(Component.translatable("tooltip.coldweaponcraftsmanship.cycle_recipe"));
    }
}
