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
    private static InventoryLayout inventoryLayout;
    private static AssemblingScreenLayout assemblingScreen;

    private Layouts() {}

    /** 资源重载时调用，清空所有缓存使其下次访问时重新从资源包加载 */
    public static void invalidate() {
        craftingMenu = null;
        craftingScreen = null;
        iconGrid = null;
        inventoryLayout = null;
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

    /** @return 物品栏快捷栏间隔 */
    public static InventoryLayout inventoryLayout() {
        if (inventoryLayout == null) inventoryLayout = load("gui/inventory_layout.json", InventoryLayout.class);
        return inventoryLayout;
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

    /** 单个槽位的坐标 */
    public static class SlotDef {
        public int x, y;
        public SlotDef() {}
    }

    /** 零件网格在制造界面中的位置和尺寸 */
    public static class PartGridDef {
        public int x_offset, y_offset, width, height;
        public PartGridDef() {}
    }

    /** 按钮的位置和尺寸 */
    public static class ButtonDef {
        public int x_offset, y_offset, width, height;
        public ButtonDef() {}
    }

    // ──── 制造界面（容器侧）── 槽位坐标与物品栏布局 ────

    public static class CraftingMenu {
        public int image_width = 256;
        public int image_height = 180;
        public int tex_width = 256;
        public int tex_height = 256;
        public int inventory_start_x = 48;
        public int inventory_start_y = 102;
        public SlotDef slot_input_0 = new SlotDef();
        public SlotDef slot_input_1 = new SlotDef();
        public SlotDef slot_input_2 = new SlotDef();
        public SlotDef slot_output = new SlotDef();

        /** 构造器设定默认槽位坐标，无参以便 Gson 反序列化 */
        public CraftingMenu() {
            slot_input_0.x = 104; slot_input_0.y = 18;
            slot_input_1.x = 120; slot_input_1.y = 18;
            slot_input_2.x = 136; slot_input_2.y = 18;
            slot_output.x = 120;  slot_output.y = 61;
        }
    }

    // ──── 制造界面（屏幕侧）── 标题与零件列表 ────

    public static class CraftingScreen {
        public int title_x = 129;
        public int title_y = 9;
        public int title_color = 0xFFFFFF;
        public ButtonDef cycle_button = new ButtonDef();
        public PartGridDef part_grid = new PartGridDef();

        public CraftingScreen() {
            cycle_button.x_offset = 155;
            cycle_button.y_offset = -12;
            cycle_button.width = 54;
            cycle_button.height = 14;
            part_grid.x_offset = 155;
            part_grid.y_offset = 8;
            part_grid.width = 54;
            part_grid.height = 80;
        }
    }

    // ──── 图标网格 ────

    public static class IconGrid {
        public int cols = 3;
        public int icon_size = 16;
        public int row_height = 16;
        public int thumb_width = 6;
        public int thumb_height = 16;
    }

    // ──── 物品栏布局 ────

    public static class InventoryLayout {
        public int hotbar_gap = 4;
    }

    // ──── 组装界面 ────

    public static class AssemblingScreenLayout {
        public NameFieldDef name_field = new NameFieldDef();
        public SlotListDef slot_list = new SlotListDef();
        public AssemblingScreenLayout() {
            name_field.x_offset = 48;
            name_field.y_offset = 10;
            name_field.width = 160;
            name_field.height = 12;
            slot_list.x_offset = 115;
            slot_list.y_offset = 22;
            slot_list.width = 93;
            slot_list.height = 66;
            slot_list.row_height = 22;
            slot_list.scrollbar_width = 6;
            slot_list.frame.x_offset = 2;
            slot_list.frame.y_offset = 2;
            slot_list.info.x_offset = 23;
            slot_list.info.y_offset = 4;
            slot_list.info.size = 16;
            slot_list.text.x_offset = 41;
            slot_list.text.y_offset = 7;
        }
    }

    public static class NameFieldDef {
        public int x_offset, y_offset, width, height;
        public NameFieldDef() {}
    }

    /** 零件改造列表（SlotList）布局 */
    public static class SlotListDef {
        public int x_offset, y_offset, width, height;
        public int row_height, scrollbar_width;
        public FrameDef frame = new FrameDef();
        public InfoDef info = new InfoDef();
        public TextDef text = new TextDef();
        public SlotListDef() {}
    }

    /** 物品框——只用到两个偏移（框体尺寸由贴图与槽位原生 18px 决定） */
    public static class FrameDef {
        public int x_offset, y_offset;
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
