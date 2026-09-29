package com.funnyb.cwc.screen;

import com.funnyb.cwc.layout.Layouts;
import com.funnyb.cwc.crafting.PartTypeDef;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ItemAttributeModifiers;

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
 * 行数据不足可见行数时（武器的槽位比列表行数少），多出来的行是"不存在的槽位"：
 * 那一行的框会被 {@link #ROW_BG} 盖掉，名称、详情热区也都不画，整行看上去就是一块空凹底。
 * <p>
 * 布局参数从 assets/cwc/gui/assembling_screen.json 读取，资源包可覆盖。
 */
public class SlotList {

    /** 滚动条滑块贴图 */
    private static final ResourceLocation SLIDER_TEX =
            ResourceLocation.fromNamespaceAndPath("coldweaponcraftsmanship", "textures/gui/slider.png");

    /**
     * 空行遮盖色——必须与面板贴图上列表凹底的内部同色（{@code #8B8B8B}，即原版槽位底色的灰）。
     * <p>
     * 凹底内部除那几个槽位框之外是纯一色，所以拿它盖掉空行的框之后逐像素看不出来。
     */
    private static final int ROW_BG = 0xFF8B8B8B;

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

    /**
     * 槽位名那批滚动的相位原点——"上次画的行内容"与"上次的滚动位置"。
     * 二者之一变了就说明屏幕上换了名字，滚动从头开始（见 {@link ScrollingText}）。存内容而不是存时间：
     * 每帧都会传一份**等值的新 list** 进来，只能按值判，不能按引用判。
     */
    private List<Row> shownRows = List.of();
    private int shownScrollRows = 0;
    private long namesShownSince;

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
        int clamped = Math.max(0, Math.min(scrollRows, maxScroll));
        // 行内容或滚动位置变了 = 屏幕上换了一批槽位名，滚动相位归零。
        // Row / SlotDef 都是 record，按值比较——每帧传进来的是一份等值的新 list，不会每帧误判成"变了"
        if (!rows.equals(shownRows) || clamped != shownScrollRows) {
            shownRows = List.copyOf(rows);
            shownScrollRows = clamped;
            namesShownSince = Util.getMillis();
        }
        this.rows = rows;
        this.visibleRows = visibleRows;
        this.scrollRows = clamped;
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
    /** 滑块高（= 滑条贴图高）。夹到 ≥1：JSON 里缺这个字段时是 0，滑块会整条消失 */
    private int scrollbarHeight() { return Math.max(1, cfg().scrollbar_height); }
    private int textOffsetX() { return cfg().text.x_offset; }
    private int textOffsetY() { return cfg().text.y_offset; }
    private int infoOffsetX() { return cfg().info.x_offset; }
    private int infoOffsetY() { return cfg().info.y_offset; }
    private int infoSize() { return cfg().info.size; }
    /** 物品框含边框的边长。夹到 ≥1：JSON 里缺这个字段时是 0，遮盖会画不出东西 */
    private int frameSize() { return Math.max(1, cfg().frame.size); }

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
                int trackHeight = height - scrollbarHeight();
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
            return y + (int) ((float) scrollRows / maxRows * (height - scrollbarHeight()));
        }
        return y;
    }

    private boolean isOnThumb(int mx, int my) {
        if (!scrollable) return false;
        int thumbY = currentThumbY();
        return mx >= x && mx < x + scrollbarWidth() && my >= thumbY && my < thumbY + scrollbarHeight();
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
            if (row == null) {
                // 空行——行数据只按实际槽位数建，所以超出的行是"这条槽位在这个武器上不存在"。
                // 把烘焙在面板贴图上的物品框盖掉：不盖就会留一个空框，既没有标签也放不进东西。
                // 遮盖色与凹底内部同色（见 ROW_BG），所以看不出接缝；凹底本身不动——它是列表本体的
                // 框，也是滚动视口的边界，收短它要连底边框一起搬，那是另一回事。
                int fs = frameSize();
                int fx = bodyX() + cfg().frame.x_offset - 1;
                int fy = ry + cfg().frame.y_offset - 1;
                guiGraphics.fill(fx, fy, fx + fs, fy + fs, ROW_BG);
                continue;
            }

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

            // 槽位名称——放不下就循环滚动，与数值浮窗同一套（见 ScrollingText）
            ScrollingText.draw(guiGraphics, Minecraft.getInstance().font,
                    Component.translatable(row.slotDef().name()),
                    bodyX() + textOffsetX(), ry + textOffsetY(),
                    bodyWidth() - textOffsetX(), Util.getMillis() - namesShownSince, 0xFFFFFF, true);
        }

        guiGraphics.disableScissor();

        // 滚动条滑块——贴区域左边界
        if (scrollable) {
            int thumbY = currentThumbY();
            int sw = scrollbarWidth();
            int sh = scrollbarHeight();
            guiGraphics.blit(SLIDER_TEX, x, thumbY, 0, 0, sw, sh, sw, sh);
        }
    }

    /**
     * 会被槽位 {@code scale} 加权的属性——**与 {@link com.funnyb.cwc.crafting.AssemblyTree} 的聚合逐条对应**：
     * 那里只对 damage / speed / durability 乘槽位权重，block 明写"不走槽位权重（裸加）"。
     * 所以这里也不列 block —— 显示了却不生效，比不显示更坏。
     */
    private static final List<String> WEIGHTED_ATTRIBUTES = List.of("damage", "speed", "durability");

    /**
     * 构建详情控件的悬停 tooltip——两段，各自有标题、各自可能不出现：
     * <pre>
     * 特征需求：
     *  - 安装方式：铤装
     * 属性倍率：
     *  - 攻速：50%
     * </pre>
     * 上段是 constraint（槽位收什么），下段是 scale（收进来的零件打几折）。
     * 返回空表 = 这个槽位没什么可说的，调用方据此不弹 tooltip。
     */
    public List<Component> infoTooltip(Row row) {
        var slotDef = row.slotDef();
        java.util.List<Component> lines = new java.util.ArrayList<>();

        String bullet = Component.translatable("tooltip.cwc.bullet").getString();
        String colon = Component.translatable("tooltip.cwc.colon").getString();
        String separator = Component.translatable("tooltip.cwc.separator").getString();

        var constraint = slotDef.constraint();
        if (constraint != null && !constraint.isEmpty()) {
            lines.add(Component.translatable("tooltip.cwc.slot_requires"));
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
        }

        java.util.List<Component> weights = weightedLines(slotDef.scale(), bullet, colon);
        if (!weights.isEmpty()) {
            lines.add(Component.translatable("tooltip.cwc.slot_scale"));
            lines.addAll(weights);
        }
        return lines;
    }

    /**
     * 槽位倍率那几行——只列**不为 1** 的项：1 是"全量计入"，也就是不声明时的默认值，
     * 列出来只是噪音（数据里写 {@code "damage": 1.0} 与不写完全等价）。
     * <p>
     * 顺序固定为 damage → speed → durability（不跟 JSON 里的键序走），与零件数值浮窗的行序一致。
     * 百分比复用原版属性 tooltip 的 {@code "#.##"} 格式器，所以 0.5 显示成"50%"、0.3 是"30%"。
     */
    private static java.util.List<Component> weightedLines(Map<String, Double> scale,
                                                           String bullet, String colon) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (scale == null) return lines;
        for (String attribute : WEIGHTED_ATTRIBUTES) {
            Double value = scale.get(attribute);
            if (value == null || value == 1.0) continue;
            lines.add(Component.literal(bullet
                    + Component.translatable("tooltip.cwc.stat." + attribute).getString()
                    + colon
                    + ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value * 100.0) + "%"));
        }
        return lines;
    }
}
