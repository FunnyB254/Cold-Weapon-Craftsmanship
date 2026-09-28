package com.funnyb.cwc.screen;

import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.menu.AssemblingMenu;
import com.funnyb.cwc.network.serverbound.RenamePartPacket;
import com.funnyb.cwc.network.serverbound.SetScrollPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    /** 命名框底衬——原版铁砧用的那两张精灵（启用态 / 禁用态），照抄它的外观 */
    private static final ResourceLocation TEXT_FIELD_TEX =
            ResourceLocation.withDefaultNamespace("textures/gui/sprites/container/anvil/text_field.png");
    private static final ResourceLocation TEXT_FIELD_DISABLED_TEX =
            ResourceLocation.withDefaultNamespace("textures/gui/sprites/container/anvil/text_field_disabled.png");

    /** 底衬相对输入框的位置——照原版铁砧的关系：左 3px、上 4px、右 4px，高固定 16（它 103 宽的框配 110 宽的底衬） */
    private static final int FIELD_PAD_LEFT = 3;
    private static final int FIELD_PAD_TOP = 4;
    private static final int FIELD_PAD_RIGHT = 4;
    private static final int FIELD_TEX_WIDTH = 110;
    private static final int FIELD_TEX_HEIGHT = 16;
    /** 底衬两端原样搬的宽度：x0..1 与 x108..109 各是 1px 边框 + 1px 内斜角 */
    private static final int FIELD_EDGE = 2;
    /** 中间可横向拉伸的列数（x2..107）——这 106 列彼此逐像素相同，拉多宽都看不出来 */
    private static final int FIELD_TEX_MIDDLE = FIELD_TEX_WIDTH - FIELD_EDGE * 2;

    private ImageButton helpButton;
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

        // 帮助按钮——位置/尺寸与制造界面共用 crafting_screen.json 的那一份（同标题）
        var hb = Layouts.craftingScreen().help_button;
        helpButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + hb.x_offset, this.topPos + hb.y_offset,
                hb.width, hb.height, GuiIcons.HELP, () -> {}));

        // 改名输入框——位置和尺寸从 JSON 布局读取。各项设置照原版铁砧的来：
        // 不能失去焦点、白字（含不可编辑态）、无边框（框体由贴图/精灵画）、上限取自菜单常量。
        var nf = Layouts.assemblingScreen().name_field;
        nameField = this.addRenderableWidget(new EditBox(this.font,
                this.leftPos + nf.x_offset, this.topPos + nf.y_offset,
                nf.width, nf.height, Component.empty()));
        nameField.setMaxLength(AssemblingMenu.MAX_NAME_LENGTH);
        nameField.setBordered(false);
        nameField.setTextColor(0xFFFFFF);
        nameField.setTextColorUneditable(0xFFFFFF);
        nameField.setCanLoseFocus(false);
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
        nameField.setEditable(!initStack.isEmpty());
        this.setInitialFocus(nameField);

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

    /**
     * 改名框的按键处理——照原版铁砧，逐字对应它的写法：
     * <ol>
     *   <li>Escape 先单独放行。不这么做的话它会被下面那条"吞键"规则连它一起吞掉，人就困在界面里了；</li>
     *   <li>其余按键，只要输入框"能吃下输入"（可见 + 聚焦 + 可编辑）就<b>整个吞掉</b>。要点是：数字键
     *       本身不由 {@code keyPressed} 处理（{@code EditBox.keyPressed} 对它返回 false），而是随后由
     *       {@code charTyped} 送进框里——所以这里必须自己把它截住，放给原版就会变成切快捷栏；</li>
     *   <li>底座空着时输入框不可编辑，"能吃下输入"自然为 false，于是全部交回原版：快捷栏、E 键都照常。</li>
     * </ol>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
        }
        return !nameField.keyPressed(keyCode, scanCode, modifiers) && !nameField.canConsumeInput()
                ? super.keyPressed(keyCode, scanCode, modifiers) : true;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var lay = Layouts.craftingScreen();
        // 靠左上角、左对齐、【关闭阴影】——同原版 AbstractContainerScreen（详见 CraftingScreen 的说明）。
        // 标题配置与制造界面共用一份 crafting_screen.json，改这里两边一起变。
        Component title = Component.translatable("screen.coldweaponcraftsmanship.assemble_parts");
        guiGraphics.drawString(this.font, title, lay.title_x, lay.title_y, lay.title_color, false);
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
        drawNameFieldBacking(guiGraphics);
        slotList.setPosition(this.leftPos + Layouts.assemblingScreen().slot_list.x_offset,
                this.topPos + Layouts.assemblingScreen().slot_list.y_offset);
        slotList.setRows(buildRows(), AssemblingMenu.VISIBLE_ROWS,
                this.menu.getScrollRows(), this.menu.maxScrollRows());
        slotList.render(guiGraphics, mouseX, mouseY);
    }

    /**
     * 左侧浮窗显示**底座槽里的物品**——空槽时 {@code getItem(0)} 就是 {@link ItemStack#EMPTY}，
     * 正好对应"放入零件才显示"。底座是个子装配体时，浮窗给的是整棵子树的合计（见 {@code AssemblyTree}），
     * 也就是这件兵器当前的读数。
     */
    @Override
    protected ItemStack infoPanelPart() {
        return this.menu.baseContainer.getItem(0);
    }

    /**
     * 画命名框底衬——原版铁砧那张 tan 底精灵，位置与尺寸完全照它的关系来；
     * 底座槽有物品用启用态、空着用禁用态（压暗成深棕），和铁砧一样。
     * <p>
     * 这张精灵没有九宫格元数据，整张拉伸会把 1px 的边框和内斜角拉毛，所以分三段 blit：
     * 左右各 {@link #FIELD_EDGE}px 原样搬，中间那 {@link #FIELD_TEX_MIDDLE} 列彼此逐像素相同、拉多宽都看不出来。
     * 用的是"带目的尺寸"的那个 blit 重载（目的 w/h 与源 uWidth/vHeight 分开给）。
     */
    private void drawNameFieldBacking(GuiGraphics guiGraphics) {
        var nf = Layouts.assemblingScreen().name_field;
        int x = this.leftPos + nf.x_offset - FIELD_PAD_LEFT;
        int y = this.topPos + nf.y_offset - FIELD_PAD_TOP;
        int width = nf.width + FIELD_PAD_LEFT + FIELD_PAD_RIGHT;
        int middle = width - FIELD_EDGE * 2;
        ResourceLocation tex = this.menu.baseContainer.getItem(0).isEmpty()
                ? TEXT_FIELD_DISABLED_TEX : TEXT_FIELD_TEX;
        // 左端 / 中间（横向拉伸）/ 右端
        guiGraphics.blit(tex, x, y, FIELD_EDGE, FIELD_TEX_HEIGHT,
                0.0F, 0.0F, FIELD_EDGE, FIELD_TEX_HEIGHT, FIELD_TEX_WIDTH, FIELD_TEX_HEIGHT);
        guiGraphics.blit(tex, x + FIELD_EDGE, y, middle, FIELD_TEX_HEIGHT,
                FIELD_EDGE, 0.0F, FIELD_TEX_MIDDLE, FIELD_TEX_HEIGHT, FIELD_TEX_WIDTH, FIELD_TEX_HEIGHT);
        guiGraphics.blit(tex, x + FIELD_EDGE + middle, y, FIELD_EDGE, FIELD_TEX_HEIGHT,
                FIELD_TEX_WIDTH - FIELD_EDGE, 0.0F, FIELD_EDGE, FIELD_TEX_HEIGHT, FIELD_TEX_WIDTH, FIELD_TEX_HEIGHT);
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
        // 可编辑性跟着底座槽走（铁砧同款）：空底座时输入框是禁用态——点不动、不闪光标、快捷键放行
        nameField.setEditable(hasItem);

        // super.render() 内顺序：renderBg(背景+行背景) → widgets(改名框) → slots → tooltip
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // tooltip：帮助 > 列表详情控件
        if (helpButton.isHovered()) {
            guiGraphics.renderComponentTooltip(this.font,
                    List.of(Component.translatable("tooltip.cwc.help")), mouseX, mouseY);
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
