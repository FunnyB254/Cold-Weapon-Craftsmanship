package com.funnyb.cwc.layout;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * GUI 布局加载器——从资源包中加载 JSON 布局文件，解析为类型化的布局数据对象。
 * <p>
 * 核心设计：
 * <ul>
 *   <li>首次访问时从 ResourceManager 加载 JSON，之后缓存复用。</li>
 *   <li>F3+T 资源重载时通过 {@link #invalidate()} 清空缓存，下次访问重新加载。</li>
 *   <li>服务端直接返回默认值（槽位坐标在服务端无渲染意义）。</li>
 *   <li>JSON 缺失或格式错误时打印日志并回退到 Java 硬编码默认值。</li>
 *   <li>颜色字段支持 {@code "0xFFFFFFFF"} 十六进制字符串写法，Gson 自动解码。</li>
 * </ul>
 * <p>
 * 资源包作者只需在自己的资源包中放入同名 JSON 文件即可覆盖对应布局。
 * <p>
 * <b>调用方必须遵守的不变式：布局值只能影响客户端的渲染与交互，绝不能进入服务端逻辑。</b>
 * 服务端拿不到资源管理器，{@link #load} 一律回落 Java 默认值，所以**同一个字段在两端可能得到不同的值**。
 * 今天无害——所有字段都是贴图路径、像素坐标、颜色，只在客户端用于绘制；菜单侧读它们只为给 {@code Slot}
 * 定位，而槽位坐标不上网络（两端各自构造菜单，槽位数与顺序一致，所以容器同步没问题，只有客户端那份坐标
 * 用于渲染）。一旦有人把布局值用在服务端会读到的地方（"槽位数量""容量"这类，或写进存档），就会变成
 * 两端真实不一致的 bug。真到那一步，正确做法是把该字段从本类挪出去，**而不是给服务端塞一份硬编码默认值**。
 *
 * @see CwcClientEvents#onRegisterReloadListeners
 */
public final class Layouts {

    /**
     * Gson 实例——注册了 Integer 类型适配器，自动将 JSON 中的十六进制字符串
     *（如 "0xC0101010"）解码为 int。
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Integer.class, (JsonDeserializer<Integer>) (json, type, ctx) -> {
                if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                    return (int) Long.decode(json.getAsString()).longValue();
                }
                return json.getAsInt();
            })
            .registerTypeAdapter(int.class, (JsonDeserializer<Integer>) (json, type, ctx) -> {
                if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                    return (int) Long.decode(json.getAsString()).longValue();
                }
                return json.getAsInt();
            })
            .create();

    /** 已记录过"服务端回落默认值"的路径——纯诊断用途，避免刷屏 */
    private static final Set<String> SERVER_FALLBACK_LOGGED = new HashSet<>();

    // 缓存实例，首次访问时惰性加载
    private static CraftingMenu craftingMenu;
    private static CraftingScreen craftingScreen;
    private static IconGrid iconGrid;
    private static AssemblingScreenLayout assemblingScreen;

    private Layouts() {}

    /** 资源重载时调用，清空所有缓存使其下次访问时重新从资源包加载 */
    public static void invalidate() {
        craftingMenu = null;
        craftingScreen = null;
        iconGrid = null;
        assemblingScreen = null;
    }

    // ═══════════════════════════════════════════
    //  公开访问器（惰性加载 + 缓存）
    // ═══════════════════════════════════════════

    /** @return 制造界面槽位与物品栏布局 */
    public static CraftingMenu craftingMenu() {
        if (craftingMenu == null) craftingMenu = load("gui/crafting_menu.json", CraftingMenu.class);
        return craftingMenu;
    }

    /** @return 制造界面标题与零件列表布局 */
    public static CraftingScreen craftingScreen() {
        if (craftingScreen == null) craftingScreen = load("gui/crafting_screen.json", CraftingScreen.class);
        return craftingScreen;
    }

    /** @return 图标网格尺寸和滚动条参数 */
    public static IconGrid iconGrid() {
        if (iconGrid == null) iconGrid = load("gui/icon_grid.json", IconGrid.class);
        return iconGrid;
    }

    /** @return 装配界面布局 */
    public static AssemblingScreenLayout assemblingScreen() {
        if (assemblingScreen == null) assemblingScreen = load("gui/assembling_screen.json", AssemblingScreenLayout.class);
        return assemblingScreen;
    }

    // ═══════════════════════════════════════════
    //  内部加载逻辑
    // ═══════════════════════════════════════════

    /**
     * 布局 JSON 的资源管理器来源——由客户端在启动时注入（见 {@link #setResourceManagerSupplier}）。
     * 本类位于**通用包**，绝不能直接引用 {@code net.minecraft.client.Minecraft}：menu 类由服务端
     * 构造，一旦常量池里有客户端引用，专用服务器会在类校验阶段被 RuntimeDistCleaner 拦下并崩溃。
     * 服务端保持默认的空供给，{@link #load} 因而一律返回 Java 默认值。
     */
    private static Supplier<ResourceManager> resourceManagerSupplier = () -> null;

    /** 客户端注入资源管理器来源并清空已有缓存（仅客户端调用） */
    public static void setResourceManagerSupplier(Supplier<ResourceManager> supplier) {
        resourceManagerSupplier = supplier;
        invalidate();
    }

    /**
     * 从资源包加载 JSON 并反序列化为类型 T。
     * 未注入资源管理器（服务端）时直接返回默认实例；客户端加载失败时同样回退默认实例。
     */
    private static <T> T load(String path, Class<T> clazz) {
        ResourceManager rm = resourceManagerSupplier.get();
        if (rm == null) {
            // 服务端：回落 Java 默认值（本类的不变式见类文档）。每个路径只记一次，让
            // "某个布局值被服务端逻辑读到"这类问题能被诊断出来；开发环境 logLevel=DEBUG 所以看得见。
            if (SERVER_FALLBACK_LOGGED.add(path)) {
                ColdWeaponCraftsmanship.LOGGER.debug(
                        "布局 '{}' 在服务端不可用（资源管理器只在客户端注入），已回落 Java 默认值", path);
            }
            return newDefault(clazz);   // 服务端不渲染 GUI：槽位坐标无意义，也不该触碰客户端资源
        }
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, path);
        try {
            Resource resource = rm.getResourceOrThrow(loc);
            try (Reader reader = resource.openAsReader()) {
                T parsed = GSON.fromJson(reader, clazz);
                // 空文件 / 字面量 null 的 JSON 会让 GSON 返回 null，必须回退默认实例，
                // 否则 null 会被缓存，调用方链式取值（如 craftingScreen().cycle_button）立刻 NPE
                if (parsed != null) return parsed;
                ColdWeaponCraftsmanship.LOGGER.error(
                        "Layout '{}' parsed to null (empty or literal-null JSON), using Java defaults", path);
            }
        } catch (Exception e) {
            ColdWeaponCraftsmanship.LOGGER.error(
                    "Failed to load layout '{}', using Java defaults. Cause: {}", path, e.toString());
        }
        return newDefault(clazz);
    }

    /** 通过无参构造器创建默认布局实例 */
    private static <T> T newDefault(Class<T> clazz) {
        try {
            // Class<T>.getDeclaredConstructor 已经返回 Constructor<T>，newInstance() 就是 T——
            // 原本这里有个 (T) 强转 + @SuppressWarnings("unchecked")，两者都是多余的（IDE 也会报）
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Layout class missing no-arg constructor: " + clazz, e);
        }
    }

    // ═══════════════════════════════════════════
    //  布局数据类（字段名 = JSON key，无参构造器提供默认值）
    // ═══════════════════════════════════════════

    /** 一个坐标——单个槽位的位置，或物品栏整块（背包 27 + 快捷栏 9）的左上角 */
    public static class SlotDef {
        public int x, y;
        public SlotDef() {}
    }

    /**
     * 屏幕上一个矩形元素的位置与尺寸——按钮、图标网格、改名框都是这个形状。
     * <p>
     * 原先是 ButtonDef / PartGridDef / NameFieldDef 三个字段完全相同的类，
     * 但"按钮 / 网格 / 输入框"在布局上本就是同一件事，合并成一个。
     */
    public static class RectDef {
        public int x_offset, y_offset, width, height;
        public RectDef() {}
    }

    // ──── 制造界面（容器侧）── 槽位坐标与物品栏布局 ────

    public static class CraftingMenu {
        public int image_width = 176;
        public int image_height = 192;
        public int tex_width = 256;
        public int tex_height = 256;
        /**
         * 物品栏整块（背包 3×9 + 快捷栏 9）的左上角——就这一个坐标定它的位置，
         * 内部按原版几何自动排列（18px 间距、快捷栏再空出 hotbar_gap）。
         */
        public SlotDef inventory = new SlotDef();
        /**
         * 3×3 输入格**左上角那一格**的坐标——同 {@link #inventory} 的做法，只留一个坐标，
         * 内部 18px 间距（3 列 3 行）是硬编码的：它必须与制造台贴图上烘焙死的九个槽框逐像素对齐，
         * 不是可调项。贴图上九个框的原点是 x=7/25/43、y=18/36/54，槽位取值 = 框+1，所以首格是 (8,19)。
         */
        public SlotDef input_grid = new SlotDef();
        /** 产出槽——贴图上那个 26×26 的大框，16×16 的物品居中，于是槽位取值 = (35+5, 74+5) */
        public SlotDef slot_output = new SlotDef();

        /** 构造器设定默认槽位坐标，无参以便 Gson 反序列化 */
        public CraftingMenu() {
            inventory.x = 8;     inventory.y = 110;
            input_grid.x = 8;    input_grid.y = 19;
            slot_output.x = 40;  slot_output.y = 79;
        }
    }

    // ──── 制造界面（屏幕侧）── 标题与零件列表 ────

    public static class CraftingScreen {
        public int title_x = 8;
        public int title_y = 6;
        public int title_color = 0x404040;
        public RectDef cycle_button = new RectDef();
        /**
         * 零件**类型**列表（右侧）的位置与尺寸——贴图上那块凹槽的内区：
         * 102×80 = (6 列 × 16px + 6px 滚动条) × (5 行 × 16px)，正好铺满不越边框。
         * 列数见 {@code icon_grid.json} 的 {@code cols}（同一条约束，改一处要改另一处）。
         */
        public RectDef part_grid = new RectDef();
        /**
         * 帮助按钮——两个界面共用这一份（装配界面也读本结构，同标题）。
         * <p>
         * 坐标为相对面板左上角的绝对值：当前 x_offset = 178 = 面板宽度 176 + 2，即贴在面板右缘外 2px。
         * 面板宽度改了（crafting_menu.json 的 image_width）这里要跟着改，不会再自动跟随。
         */
        public RectDef help_button = new RectDef();
        /**
         * 零件数值浮窗——落在主面板**左侧之外**，右端 4px 压在主面板底下（画在主面板之前，
         * 由主面板的左边缘盖住），观感同原版创造模式物品栏那排标签页。两个界面共用这一份。
         * <p>
         * 两处耦合，改这里或改它们的值时都要对一眼：
         * <ul>
         *   <li>{@code height} 必须等于 {@code crafting_menu.json} 的 {@code image_height}——
         *       "高度和主贴图相同"就是这条，上下沿才会连成一条线；</li>
         *   <li>{@code width + x_offset}（= 压在面板底下的那几像素）与
         *       {@code -x_offset}（= 伸到面板左边多远）都要落在 JEI 的左侧避让区内，
         *       否则 JEI 的物品列表会盖到它上面：避让区见 {@code CwcJeiPlugin.KEEP_OUT}
         *       （现为 100，即浮窗左边最多伸出 100px、且 <= 100 才被完全护住；
         *       现值 96 ≤ 100，留 4px 余量）。</li>
         * </ul>
         */
        public RectDef info_panel = new RectDef();

        public CraftingScreen() {
            cycle_button.x_offset = 12;
            cycle_button.y_offset = 79;
            // 16×16：必须等于字形贴图 cycle.png 的尺寸——ImageButton 把字形按按钮尺寸整张 blit
            cycle_button.width = 16;
            cycle_button.height = 16;
            part_grid.x_offset = 66;
            part_grid.y_offset = 19;
            part_grid.width = 102;
            part_grid.height = 80;
            help_button.x_offset = 178;
            help_button.y_offset = 0;
            help_button.width = 16;
            help_button.height = 16;
            info_panel.x_offset = -96;
            info_panel.y_offset = 0;
            info_panel.width = 100;
            info_panel.height = 192;
        }
    }

    // ──── 图标网格 ────

    public static class IconGrid {
        /** 列数——必须与 {@code crafting_screen.json} 的 part_grid 宽度自洽：cols×16 + 滚动条6 ≤ 宽度 */
        public int cols = 6;
        public int icon_size = 16;
        public int row_height = 16;
        public int thumb_width = 6;
        public int thumb_height = 16;
    }

    // ──── 组装界面 ────

    public static class AssemblingScreenLayout {
        public RectDef name_field = new RectDef();
        public SlotListDef slot_list = new SlotListDef();
        /** 底座槽的坐标——对应 assembling.png 上烘焙死的槽框，与零件列表的三行分开配置 */
        public SlotDef base_slot = new SlotDef();
        public AssemblingScreenLayout() {
            base_slot.x = 11;
            base_slot.y = 80;
            name_field.x_offset = 8;
            name_field.y_offset = 22;
            name_field.width = 160;
            name_field.height = 12;
            slot_list.x_offset = 75;
            slot_list.y_offset = 34;
            slot_list.width = 93;
            slot_list.height = 66;
            slot_list.row_height = 22;
            slot_list.scrollbar_width = 6;
            slot_list.scrollbar_height = 16;
            slot_list.frame.x_offset = 3;
            slot_list.frame.y_offset = 3;
            slot_list.frame.size = 18;
            slot_list.info.x_offset = 23;
            slot_list.info.y_offset = 3;
            slot_list.info.size = 16;
            slot_list.text.x_offset = 41;
            slot_list.text.y_offset = 7;
        }
    }

    /** 零件改造列表（SlotList）布局 */
    public static class SlotListDef {
        public int x_offset, y_offset, width, height;
        public int row_height, scrollbar_width, scrollbar_height;
        public FrameDef frame = new FrameDef();
        public InfoDef info = new InfoDef();
        public TextDef text = new TextDef();
        public SlotListDef() {}
    }

    /** 行内物品框——偏移量指到框内 16×16 内容的左上角，size 是含 1px 边框的整框边长（原版 18） */
    public static class FrameDef {
        public int x_offset, y_offset, size;
        public FrameDef() {}
    }

    /** 详情控件——键体由原版九宫格精灵画，悬停态由原版给，所以没有颜色字段 */
    public static class InfoDef {
        public int x_offset, y_offset, size;
        public InfoDef() {}
    }

    /** 槽位名称文本 */
    public static class TextDef {
        public int x_offset, y_offset;
        public TextDef() {}
    }
}
