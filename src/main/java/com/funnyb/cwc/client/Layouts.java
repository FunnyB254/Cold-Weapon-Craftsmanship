package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.Reader;

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
                    return Integer.decode(json.getAsString());
                }
                return json.getAsInt();
            })
            .registerTypeAdapter(int.class, (JsonDeserializer<Integer>) (json, type, ctx) -> {
                if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                    return Integer.decode(json.getAsString());
                }
                return json.getAsInt();
            })
            .create();

    // 缓存实例，首次访问时惰性加载
    private static CraftingMenu craftingMenu;
    private static CraftingScreen craftingScreen;
    private static CreativePartStar creativePartStar;
    private static IconGrid iconGrid;
    private static InventoryLayout inventoryLayout;
    private static BaseInventoryScreen baseInventoryScreen;
    private static ImageButton imageButton;

    private Layouts() {}

    /** 资源重载时调用，清空所有缓存使其下次访问时重新从资源包加载 */
    public static void invalidate() {
        craftingMenu = null;
        craftingScreen = null;
        creativePartStar = null;
        iconGrid = null;
        inventoryLayout = null;
        baseInventoryScreen = null;
        imageButton = null;
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

    /** @return 选择界面全部控件布局 */
    public static CreativePartStar creativePartStar() {
        if (creativePartStar == null) creativePartStar = load("gui/creative_part_star_screen.json", CreativePartStar.class);
        return creativePartStar;
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

    /** @return 物品渲染缩放与偏移参数 */
    public static BaseInventoryScreen baseInventoryScreen() {
        if (baseInventoryScreen == null) baseInventoryScreen = load("gui/base_inventory_screen.json", BaseInventoryScreen.class);
        return baseInventoryScreen;
    }

    /** @return 透明按钮 hover 高亮颜色 */
    public static ImageButton imageButton() {
        if (imageButton == null) imageButton = load("gui/image_button.json", ImageButton.class);
        return imageButton;
    }

    // ═══════════════════════════════════════════
    //  内部加载逻辑
    // ═══════════════════════════════════════════

    /**
     * 从资源包加载 JSON 并反序列化为类型 T。
     * 服务端直接返回默认实例（不加载资源），客户端加载失败时回退到默认实例。
     */
    private static <T> T load(String path, Class<T> clazz) {
        // 服务端不渲染 GUI，槽位坐标无意义，直接返回默认值避免 Minecraft 类不可用
        if (!FMLEnvironment.dist.isClient()) {
            return newDefault(clazz);
        }
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, path);
        try {
            ResourceManager rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            Resource resource = rm.getResourceOrThrow(loc);
            try (Reader reader = resource.openAsReader()) {
                return GSON.fromJson(reader, clazz);
            }
        } catch (Exception e) {
            ColdWeaponCraftsmanship.LOGGER.error(
                    "Failed to load layout '{}', using Java defaults. Cause: {}", path, e.toString());
            return newDefault(clazz);
        }
    }

    /** 通过无参构造器创建默认布局实例 */
    @SuppressWarnings("unchecked")
    private static <T> T newDefault(Class<T> clazz) {
        try {
            return (T) clazz.getDeclaredConstructor().newInstance();
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

    /** 标签文字的位置和颜色 */
    public static class LabelDef {
        public int x_offset, y_offset, color;
        public LabelDef() {}
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

    // ──── 选择界面 ──── 背景、按钮、标签 ────

    public static class CreativePartStar {
        public int image_width = 256;
        public int image_height = 166;
        public int tex_width = 256;
        public int tex_height = 256;
        public int background_color = 0xC0101010;
        public ButtonDef button_craft = new ButtonDef();
        public ButtonDef button_assemble = new ButtonDef();
        public LabelDef label_craft = new LabelDef();
        public LabelDef label_assemble = new LabelDef();

        public CreativePartStar() {
            button_craft.x_offset = -1;    button_craft.y_offset = 62;
            button_craft.width = 44;        button_craft.height = 44;
            button_assemble.x_offset = 213; button_assemble.y_offset = 62;
            button_assemble.width = 44;     button_assemble.height = 44;
            label_craft.x_offset = 22;      label_craft.y_offset = 94;
            label_craft.color = 0xFFFFFF;
            label_assemble.x_offset = 236;  label_assemble.y_offset = 94;
            label_assemble.color = 0xFFFFFF;
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

    // ──── 物品栏基础界面 ────

    public static class BaseInventoryScreen {
        public double item_scale_numerator = 14.0;
        public double item_scale_denominator = 16.0;
        public double item_offset_x = 1.0;
        public double item_offset_y = 1.0;
    }

    // ──── 透明按钮 ────

    public static class ImageButton {
        public int hover_color = 0x30FFFFFF;
    }
}
