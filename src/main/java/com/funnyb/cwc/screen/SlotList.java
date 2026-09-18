package com.funnyb.cwc.screen;

import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 行级滚动列表——装配界面零件改造列表的视觉层。
 * <p>
 * 创造模式式滚动：槽位位置固定（由真实 InputSlot 渲染物品框），行背景（凹底 + 槽位框）
 * 已永久烘焙在 assembling.png 上，本组件只负责槽位名称文本、详情热区（原版键体 + 字形）
 * 与悬停 tooltip 的渲染，以及滚动交互。
 * 滚动行数由服务端 ContainerData 权威持有，本组件仅向服务端发送滚动意图。
 * <p>
 * 布局参数从 assets/cwc/gui/assembling_screen.json 读取，资源包可覆盖。
 */
public class SlotList {

    /** 滚动条滑块贴图 */
    private static final ResourceLocation SLIDER_TEX =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "textures/gui/slider.png");

    /**
     * 详情热区的键体精灵——就是原版按钮用的那三张，悬停时和原版按钮一样换成高亮态。
     * <p>
     * 只借外观：本组件不接收点击、不注册控件，所以详情热区既不会触发事件，
     * 也不会发出按钮音效——它压根没有可点的地方。
     */
    private static final WidgetSprites INFO_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/button"),
            ResourceLocation.withDefaultNamespace("widget/button_disabled"),
            ResourceLocation.withDefaultNamespace("widget/button_highlighted"));

    /** 单行视觉数据：槽位定义 + 是否已装零件 */
    public record Row(PartTypeDef.SlotDef slotDef, boolean hasPart) {}

    private int x;
    private int y;
    private final int width;
    private final int height;

    /** 全量行数据 */
    private List<Row> rows = List.of();
    /** 当前滚动行数（服务端 ContainerData 同步值） */
    private int scrollRows = 0;
    /** 可见行数 */
    private int visibleRows = 3;
    /** 是否有滚动空间（决定滑块显示） */
    private boolean scrollable = false;

    private boolean dragging = false;
    private int dragOffsetFromThumbTop = 0;

    /** 滚动变化回调——通知上层发 SetScrollPacket */
    private java.util.function.Consumer<Integer> onScroll;

    public SlotList(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** 每帧渲染前更新列表位置 */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * 设置行数据和滚动状态（每帧调用）。
     *
     * @param rows        全量行数据
     * @param visibleRows 可见行数
     * @param scrollRows  服务端当前滚动行数
     * @param maxScroll   最大滚动行数
     */
    public void setRows(List<Row> rows, int visibleRows, int scrollRows, int maxScroll) {
        this.rows = rows;
        this.visibleRows = visibleRows;
        this.scrollRows = Math.max(0, Math.min(scrollRows, maxScroll));
        this.scrollable = maxScroll > 0;
    }

    /** 设置滚动变化回调 */
    public void onScroll(java.util.function.Consumer<Integer> callback) {
        this.onScroll = callback;
    }

    /** 当前滚动行数 */
    public int scrollRows() {
        return scrollRows;
    }

    /** 最大滚动行数 */
    public int maxScrollRows() {
        return Math.max(0, rows.size() - visibleRows);
    }

    // ──── 布局参数 ────

    private Layouts.SlotListDef cfg() { return Layouts.assemblingScreen().slot_list; }
    private int rowHeight() { return cfg().row_height; }
    private int scrollbarWidth() { return cfg().scrollbar_width; }
    private int textOffsetX() { return cfg().text.x_offset; }
    private int textOffsetY() { return cfg().text.y_offset; }
    private int infoOffsetX() { return cfg().info.x_offset; }
    private int infoOffsetY() { return cfg().info.y_offset; }
    private int infoSize() { return cfg().info.size; }

    /** 列表主体宽度（不含滑条） */
    private int bodyWidth() {
        return width - scrollbarWidth();
    }

    /** 主体内容起点 x（滑条占最左 scrollbarWidth 像素） */
    private int bodyX() {
        return x + scrollbarWidth();
    }

    /** 某可见行在屏幕上的 Y 坐标 */
    private int visibleRowY(int visibleIndex) {
        return y + visibleIndex * rowHeight();
    }

    // ──── 鼠标事件 ────

    /** 滚轮滚动——每次 ±1 行，仅在鼠标位于列表主体内时处理 */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < bodyX() || mouseX >= bodyX() + bodyWidth() || mouseY < y || mouseY >= y + height) {
            return false;
        }
        int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
        requestScroll(scrollRows + delta);
        return true;
    }

    /** 鼠标按下——仅处理滑块拖拽；物品框点击交给原版 Slot 命中 */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOnThumb((int) mouseX, (int) mouseY)) {
            dragging = true;
            dragOffsetFromThumbTop = (int) mouseY - currentThumbY();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    /** 拖拽滑块——按整行步进，换算鼠标 Y 比例到目标行数并取整 */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            int maxRows = maxScrollRows();
            if (maxRows > 0) {
                int trackHeight = height - 16;
                float ratio = (float) (mouseY - dragOffsetFromThumbTop - y) / trackHeight;
                requestScroll(Math.round(ratio * maxRows));
            }
            return true;
        }
        return false;
    }

    /**
     * 发送滚动请求到服务端——**值没变就不发**。
     * <p>
     * 滚轮每格都会走到这里，而当前数据下列表其实滚不动（可见 3 行、总行数不足），
     * 不比较就每格发一次 {@code SetScrollPacket}，服务端收到要跑整菜单重同步。
     * 拖滑块同理：同一行内来回移动不该重复发包。
     */
    private void requestScroll(int rows) {
        int clamped = Math.max(0, Math.min(rows, maxScrollRows()));
        if (clamped == scrollRows) return;
        if (onScroll != null) onScroll.accept(clamped);
    }

    private int currentThumbY() {
        int maxRows = maxScrollRows();
        if (maxRows > 0) {
            return y + (int) ((float) scrollRows / maxRows * (height - 16));
        }
        return y;
    }

    private boolean isOnThumb(int mx, int my) {
        if (!scrollable) return false;
        int thumbY = currentThumbY();
        return mx >= x && mx < x + scrollbarWidth() && my >= thumbY && my < thumbY + 16;
    }

    // ──── 悬停查询 ────

    /** 获取鼠标悬停的可见行索引，-1 表示无 */
    public int visibleRowAt(double mouseX, double mouseY) {
        if (mouseX < bodyX() || mouseX >= bodyX() + bodyWidth() || mouseY < y || mouseY >= y + height) {
            return -1;
        }
        int idx = (int) ((mouseY - y) / rowHeight());
        return (idx >= 0 && idx < visibleRows) ? idx : -1;
    }

    /** 判断鼠标是否命中某可见行的详情控件区域 */
    public boolean isOnInfo(double mouseX, double mouseY, int visibleIndex) {
        int ry = visibleRowY(visibleIndex);
        return mouseX >= bodyX() + infoOffsetX() && mouseX < bodyX() + infoOffsetX() + infoSize()
                && mouseY >= ry + infoOffsetY() && mouseY < ry + infoOffsetY() + infoSize();
    }

    /** 获取某可见行对应的全量行索引 */
    public int fullRowIndex(int visibleIndex) {
        return scrollRows + visibleIndex;
    }

    /** 获取某可见行的行数据，越界返回 null */
    public Row visibleRow(int visibleIndex) {
        int full = scrollRows + visibleIndex;
        return (full >= 0 && full < rows.size()) ? rows.get(full) : null;
    }

    // ──── 渲染 ────

    /**
     * 渲染列表：scissor 裁剪 → 各行的详情热区（键体 + 字形）+ 槽位名称文本 → 滑块。
     * 行背景与物品框都不由本组件画（前者烘焙在面板上，后者由真实 InputSlot 渲染）。
     */
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.enableScissor(x, y, x + width, y + height);

        for (int i = 0; i < visibleRows; i++) {
            Row row = visibleRow(i);
            int ry = visibleRowY(i);
            if (row == null) break;

            // 行背景【不在这里画】——凹底和 3 个槽位框已永久烘焙进 assembling.png，
            // 像原版容器那样固定不动，滚动只换内容。这里只画随行变化的东西。

            // 详情热区——键体和帮助/循环按钮同源，都是原版 widget/button 九宫格精灵，
            // 悬停时原版会自动换成 button_highlighted，就是那种「亮起来」的按下感。
            // 尺寸必须容得下原画的符号：扣掉九宫格 3px 边框后的内区要装得下
            // 那三个方点加 1px 投影（共 9x3），16x16 的内区是 10x10，放得下且有余量。
            guiGraphics.blitSprite(INFO_SPRITES.get(true, isOnInfo(mouseX, mouseY, i)),
                    bodyX() + infoOffsetX(), ry + infoOffsetY(), infoSize(), infoSize());
            guiGraphics.blit(GuiIcons.INFO, bodyX() + infoOffsetX(), ry + infoOffsetY(),
                    0, 0, infoSize(), infoSize(), infoSize(), infoSize());

            // 槽位名称——按像素宽度截断 + 省略号
            String name = Component.translatable(row.slotDef().name()).getString();
            guiGraphics.drawString(Minecraft.getInstance().font,
                    truncateToWidth(name, bodyWidth() - textOffsetX()),
                    bodyX() + textOffsetX(), ry + textOffsetY(), 0xFFFFFF);
        }

        guiGraphics.disableScissor();

        // 滚动条滑块——贴区域左边界
        if (scrollable) {
            int thumbY = currentThumbY();
            guiGraphics.blit(SLIDER_TEX, x, thumbY, 0, 0, scrollbarWidth(), 16, scrollbarWidth(), 16);
        }
    }

    /** 按像素宽度截断字符串，超出加省略号 */
    private String truncateToWidth(String text, int maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        String truncated = text;
        while (font.width(truncated) + ellipsisWidth > maxWidth && !truncated.isEmpty()) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + ellipsis;
    }

    /**
     * 构建详情控件的悬停 tooltip——把 constraint 转为多行可读文本。
     * 第一行标题，之后每行一条约束：" - 安装方式：铤装"。
     */
    public List<Component> infoTooltip(Row row) {
        var constraint = row.slotDef().constraint();
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (constraint == null || constraint.isEmpty()) return lines;

        lines.add(Component.translatable("tooltip.cwc.slot_requires"));

        String bullet = Component.translatable("tooltip.cwc.bullet").getString();
        String colon = Component.translatable("tooltip.cwc.colon").getString();
        String separator = Component.translatable("tooltip.cwc.separator").getString();
        for (Map.Entry<String, List<String>> entry : constraint.entrySet()) {
            String key = entry.getKey();
            StringBuilder sb = new StringBuilder();
            sb.append(bullet);
            sb.append(Component.translatable(key + ".cwc").getString());
            sb.append(colon);
            var values = entry.getValue();
            if (values == null) continue;      // JSON 写 "constraint": { "weight": null } 时值为 null，跳过
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) sb.append(separator);
                sb.append(Component.translatable(key + ".cwc." + values.get(i)).getString());
            }
            lines.add(Component.literal(sb.toString()));
        }
        return lines;
    }
}
