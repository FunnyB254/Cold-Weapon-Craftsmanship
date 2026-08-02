package com.funnyb.cwc.screen;

import com.funnyb.cwc.client.Layouts;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.menu.AssemblingMenu;
import com.funnyb.cwc.network.serverbound.RenamePartPacket;
import com.funnyb.cwc.network.serverbound.SetScrollPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 装配界面——组装零件为成品武器。
 * 包含：底座槽、改名框、帮助/返回按钮、零件改造列表（SlotList）。
 * 列表采用创造模式式滚动：固定 3 个 InputSlot 显示物品框，滚动整数行改变内容。
 * 所有装/取/滚动变更走网络包，服务端权威。
 */
public class AssemblingScreen extends BaseInventoryScreen<AssemblingMenu> {

    private ImageButton helpButton;
    private ImageButton backButton;
    private EditBox nameField;
    private String lastCustomName = null;

    /** 零件改造列表——位置/尺寸从 JSON 布局读取 */
    private final SlotList slotList = new SlotList(
            Layouts.assemblingScreen().slot_list.x_offset,
            Layouts.assemblingScreen().slot_list.y_offset,
            Layouts.assemblingScreen().slot_list.width,
            Layouts.assemblingScreen().slot_list.height);

    public AssemblingScreen(AssemblingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, menu.inventoryLayout);
    }

    @Override
    protected void init() {
        super.init();

        helpButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + 212, this.topPos + 7, 16, 16, () -> {}));

        backButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + 230, this.topPos + 7, 16, 16,
                () -> Minecraft.getInstance().setScreen(new CreativePartStarScreen())));

        // 改名输入框——位置和尺寸从 JSON 布局读取
        var nf = Layouts.assemblingScreen().name_field;
        var box = new EditBox(this.font,
                this.leftPos + nf.x_offset, this.topPos + nf.y_offset,
                nf.width, nf.height, Component.empty()) {
            @Override
            public boolean charTyped(char code, int modifiers) {
                if (AssemblingScreen.this.menu.baseContainer.getItem(0).isEmpty()) return false;
                return super.charTyped(code, modifiers);
            }
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (AssemblingScreen.this.menu.baseContainer.getItem(0).isEmpty()) return false;
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        };
        nameField = this.addRenderableWidget(box);
        nameField.setMaxLength(50);
        nameField.setBordered(false);
        nameField.setTextColor(0xFFFFFF);
        nameField.setResponder(text -> {
            if (!this.menu.baseContainer.getItem(0).isEmpty()) {
                PacketDistributor.sendToServer(new RenamePartPacket(text));
            }
        });
        // 全屏切换等重建场景：如有自定义名则填充
        ItemStack initStack = this.menu.baseContainer.getItem(0);
        var initCustom = !initStack.isEmpty()
                ? initStack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                : null;
        if (initCustom != null) {
            nameField.setValue(initCustom.getString());
            lastCustomName = initCustom.getString();
        }
        nameField.setFocused(true);
        this.setFocused(nameField);

        // 列表滚动变化 → 发 SetScrollPacket 让服务端刷新
        slotList.onScroll(rows ->
                PacketDistributor.sendToServer(new SetScrollPacket(rows)));
    }

    /** 根据底座武器 + ASSEMBLED_SLOTS 构建 SlotList 全量行数据 */
    private List<SlotList.Row> buildRows() {
        List<SlotList.Row> result = new ArrayList<>();
        int count = this.menu.activePartSlotCount();
        for (int i = 0; i < count; i++) {
            PartTypeDef.SlotDef slotDef = this.menu.getSlotDefAt(i);
            if (slotDef == null) continue;
            boolean hasPart = !this.menu.getAssembledPartStack(slotDef.name()).isEmpty();
            result.add(new SlotList.Row(slotDef, hasPart));
        }
        return result;
    }

    /** 改名框聚焦时拦截快捷栏快捷键——和铁砧一致，按键进输入框不移动物品 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField != null && nameField.isFocused()
                && isHotbarKey(keyCode, scanCode)) {
            return nameField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 判断是否匹配任一快捷栏槽位绑定（keyPressed 只收键盘事件，天然不含鼠标） */
    private boolean isHotbarKey(int keyCode, int scanCode) {
        var options = net.minecraft.client.Minecraft.getInstance().options;
        for (var mapping : options.keyHotbarSlots) {
            if (mapping.matches(keyCode, scanCode)) return true;
        }
        return false;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var lay = Layouts.craftingScreen();
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.coldweaponcraftsmanship.assemble_parts"),
                lay.title_x, lay.title_y, lay.title_color);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return slotList.mouseScrolled(mx, my, sx, sy)
                || super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        return slotList.mouseClicked(mx, my, btn) || super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        return slotList.mouseReleased(mx, my, btn) || super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return slotList.mouseDragged(mx, my, btn, dx, dy) || super.mouseDragged(mx, my, btn, dx, dy);
    }

    /** 渲染背景——先画 GUI 背景贴图，再画零件列表行背景（在 slots/tooltip 之下） */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, partialTick, mouseX, mouseY);
        slotList.setPosition(this.leftPos + Layouts.assemblingScreen().slot_list.x_offset,
                this.topPos + Layouts.assemblingScreen().slot_list.y_offset);
        slotList.setRows(buildRows(), AssemblingMenu.VISIBLE_ROWS,
                this.menu.getScrollRows(), this.menu.maxScrollRows());
        slotList.render(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 底座槽：无物品清空，物品变化时读取 CUSTOM_NAME 填充（没有则留空）
        ItemStack current = this.menu.baseContainer.getItem(0);
        boolean hasItem = !current.isEmpty();
        var customName = hasItem
                ? current.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                : null;
        String currentName = customName != null ? customName.getString() : "";
        if (!Objects.equals(lastCustomName, hasItem ? currentName : null)) {
            lastCustomName = hasItem ? currentName : null;
            nameField.setValue(currentName);
        }

        // super.render() 内顺序：renderBg(背景+行背景) → widgets(改名框) → slots → tooltip
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // tooltip：帮助/返回 > 列表详情控件
        if (helpButton.isHovered()) {
            guiGraphics.renderComponentTooltip(this.font,
                    List.of(Component.translatable("tooltip.cwc.help")), mouseX, mouseY);
        } else if (backButton.isHovered()) {
            guiGraphics.renderComponentTooltip(this.font,
                    List.of(Component.translatable("tooltip.cwc.back")), mouseX, mouseY);
        } else {
            int visibleIdx = slotList.visibleRowAt(mouseX, mouseY);
            if (visibleIdx >= 0 && slotList.isOnInfo(mouseX, mouseY, visibleIdx)) {
                SlotList.Row row = slotList.visibleRow(visibleIdx);
                if (row != null) {
                    List<Component> info = slotList.infoTooltip(row);
                    if (!info.isEmpty()) {
                        guiGraphics.renderComponentTooltip(this.font, info, mouseX, mouseY);
                    }
                }
            }
        }
    }
}
