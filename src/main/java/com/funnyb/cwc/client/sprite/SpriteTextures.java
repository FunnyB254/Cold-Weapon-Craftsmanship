package com.funnyb.cwc.client.sprite;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.crafting.PartRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.io.Reader;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 零件贴图的「精灵图 + 调色板」运行时——把两份 JSON 现画成一张 {@link NativeImage}。
 * <p>
 * 原来每个（类型 × 材质）一张 PNG（56 张），同一类型七个材质的形状完全相同、只差几个颜色；
 * 现在形状与配色分开：形状只画 16 条精灵图，换色只改几行十六进制。
 * <p>
 * <b>路径推导</b>（与零件 id 一一对应，不需要零件定义里多写任何字段）：
 * <ul>
 *   <li>零件 {@code <命名空间>:<类型>/<材质>} → 调色板 {@code <命名空间>:cwc/palette/<类型>/<材质>.json}</li>
 *   <li>调色板里 {@code sprite} 字段 {@code <命名空间>:<类型>/<组>}
 *       → 精灵图 {@code <命名空间>:cwc/sprite/<类型>/<组>.json}</li>
 * </ul>
 * 命名空间一路跟着 id 走：第三方扩展包的零件走它自己的资源命名空间，与本模组互不干扰。
 * 类型 id（没有自己的贴图）取该类型下 id 最小的零件作代表——与改造前 {@code AssembledWeaponRenderer.textureOf}
 * 的口径完全一致。
 * <p>
 * <b>缓存</b>：每个零件一张画好的图（作者要求"每个零件留一份缓存"），外加解析好的调色板与共用的精灵图
 * （一条精灵图被同组多个材质共用，没必要解析多遍）。F3+T 时 {@link #invalidate()} 把画好的图**逐个 close
 * 再清空**——{@code NativeImage} 是堆外内存，只 clear 不 close 就是泄漏——下次用到时惰性重画。
 * <p>
 * <b>坏文件绝不崩游戏</b>：格式错误走 {@link SpritePalette.SpriteFormatException}，一律记 ERROR、
 * 该零件回落到原版缺失贴图，并把 id 记进 {@link #FAILED} 以免每帧刷屏。
 */
public final class SpriteTextures {

    private SpriteTextures() {}

    /** 零件 id → 已画好的图。值归本类所有，{@link #invalidate()} 负责 close */
    private static final Map<String, NativeImage> IMAGES = new HashMap<>();
    /** 零件 id → 解析好的调色板（调色板与零件一一对应，路径由零件 id 推导） */
    private static final Map<String, SpritePalette.Palette> PALETTES = new HashMap<>();
    /** 精灵图引用 → 精灵图。一份精灵图被同组多个材质共用，解析一次即可 */
    private static final Map<String, SpritePalette.Sprite> SPRITES = new HashMap<>();
    /** 已经报过错、且已回落缺失贴图的 id——不再重试、不再刷屏，重载时随缓存一起清掉 */
    private static final Set<String> FAILED = new HashSet<>();

    /** 缺失贴图——原版约定，与改造前 {@code AssembledWeaponRenderer} 用的同一张 */
    private static final ResourceLocation MISSING_TEXTURE = ResourceLocation.withDefaultNamespace("missingno");

    /**
     * 取某个零件（或类型）的贴图。同一 id 第二次起直接命中缓存。
     *
     * @param id 零件 id（{@code 命名空间:类型/材质}）或类型 id（{@code 命名空间:类型}）
     * @return 一张只读的 {@link NativeImage}；id 非法或资源坏掉时返回原版缺失贴图，**绝不返回 null**
     */
    public static NativeImage sourceOf(String id) {
        // null 不记日志：那是存档里的空组件，每帧都会来问一次，不是资源问题
        if (id == null) return missingTexture();
        if (FAILED.contains(id)) return missingTexture();

        String partId = resolvePartId(id);
        if (partId == null) {
            // 两条都要记：类型 id 解析出来的零件 id 可能与它不同，只记一条会让另一条每帧重试
            FAILED.add(id);
            report(id, "无法从 id 推出零件（既不是合法零件 id，也找不到对应类型）");
            return missingTexture();
        }
        NativeImage cached = IMAGES.get(partId);
        if (cached != null) return cached;
        if (FAILED.contains(partId)) {
            FAILED.add(id);
            return missingTexture();
        }

        NativeImage built;
        try {
            built = build(partId);
        } catch (SpritePalette.SpriteFormatException e) {
            report(partId, e.getMessage());
            built = null;
        } catch (Exception e) {
            report(partId, e.toString());
            built = null;
        }
        if (built == null) {
            FAILED.add(partId);
            FAILED.add(id);
            return missingTexture();
        }
        IMAGES.put(partId, built);
        return built;
    }

    /**
     * 资源重载（F3+T）时清空全部缓存并**释放**已画好的图，下次访问重新加载。
     * <p>
     * 与 {@code Layouts.invalidate()} 同一套语义（清空 → 惰性重载），区别只在于这里还持有堆外内存，
     * 必须先逐个 {@code close()}。调色板/精灵图的解析结果只是一些 int 数组与字符串，清掉即可。
     */
    public static void invalidate() {
        for (NativeImage img : IMAGES.values()) {
            img.close();
        }
        IMAGES.clear();
        PALETTES.clear();
        SPRITES.clear();
        FAILED.clear();
    }

    // ──── 画图 ────

    /**
     * 由零件 id 现画一张图：读调色板 → 读它指定的精灵图 → 逐像素查表。
     *
     * @return 画好的图；任何一步失败都返回 null（调用方负责记 ERROR 与回落）
     * @throws SpritePalette.SpriteFormatException 两份 JSON 的格式问题（缺字段、行不等长、下标越界、颜色串非法）
     */
    private static NativeImage build(String partId) {
        SpritePalette.Palette palette = loadPalette(partId);
        SpritePalette.Sprite sprite = loadSprite(palette.sprite());
        int[] colors = SpritePalette.decodeColors(palette.colors());

        int w = sprite.width();
        int h = sprite.height();
        // useCalloc = true：内存清零，正好等于"全透明"，下标 0 的背景像素不用再写一遍
        NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            int[] row = sprite.pixels()[y];
            for (int x = 0; x < w; x++) {
                int idx = row[x];
                if (idx >= colors.length) {
                    image.close();
                    throw new SpritePalette.SpriteFormatException(String.format(
                            "精灵图下标越界：像素 (%d,%d) 指向第 %d 项，调色板只有 %d 项（%s）",
                            x, y, idx, colors.length, palette.sprite()));
                }
                int argb = colors[idx];
                if ((argb >>> 24) == 0) continue;   // 透明项：跳过，留作清零后的透明
                image.setPixelRGBA(x, y, argb);
            }
        }
        return image;
    }

    /** 读零件 id 对应的调色板 JSON */
    private static SpritePalette.Palette loadPalette(String partId) {
        ResourceLocation key = ResourceLocation.parse(partId);
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                key.getNamespace(), "cwc/palette/" + key.getPath() + ".json");
        SpritePalette.Palette cached = PALETTES.get(partId);
        if (cached != null) return cached;
        SpritePalette.Palette parsed = SpritePalette.parsePalette(readJson(loc));
        PALETTES.put(partId, parsed);
        return parsed;
    }

    /** 读精灵图引用（{@code 命名空间:类型/组}）对应的精灵图 JSON */
    private static SpritePalette.Sprite loadSprite(String ref) {
        SpritePalette.Sprite cached = SPRITES.get(ref);
        if (cached != null) return cached;
        ResourceLocation key = parseRef(ref);
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                key.getNamespace(), "cwc/sprite/" + key.getPath() + ".json");
        SpritePalette.Sprite parsed = SpritePalette.parseSprite(readJson(loc));
        SPRITES.put(ref, parsed);
        return parsed;
    }

    /** 从资源包读一个 JSON 对象。读不到 / 不是对象 / 语法错误都抛格式异常，由调用方统一记 ERROR */
    private static JsonObject readJson(ResourceLocation loc) {
        try (Reader reader = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(loc).openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            throw new SpritePalette.SpriteFormatException("不是 JSON 对象（空文件或字面量）");
        } catch (SpritePalette.SpriteFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new SpritePalette.SpriteFormatException("读取 " + loc + " 失败：" + e);
        }
    }

    // ──── id 推导 ────

    /**
     * 把传入的 id 归一成**具体零件**的 id。
     * <p>
     * 零件 id 原样返回；类型 id（零件定义的 {@code type} 字段，注册表里查得到类型定义）取该类型下
     * id 最小的零件作代表；非法 id 返回 null。
     */
    private static String resolvePartId(String id) {
        ResourceLocation key = parseRefOrNull(id);
        if (key == null) return null;
        if (PartRegistry.getTypeDef(key) == null) return key.toString();
        return PartRegistry.partMap().entrySet().stream()
                .filter(e -> key.equals(e.getValue().type()))
                .map(Map.Entry::getKey)
                .min(Comparator.comparing(ResourceLocation::toString))
                .map(ResourceLocation::toString)
                .orElse(null);
    }

    /** 解析精灵图引用；非法直接抛（这是数据里的错，不是存档里的旧 id） */
    private static ResourceLocation parseRef(String ref) {
        ResourceLocation key = parseRefOrNull(ref);
        if (key == null) {
            throw new SpritePalette.SpriteFormatException("'sprite' 不是合法的资源位置：" + ref);
        }
        return key;
    }

    /** 字符串 → ResourceLocation，非法返回 null（旧存档的点号 id、null 都走这里） */
    private static ResourceLocation parseRefOrNull(String id) {
        if (id == null) return null;
        try {
            return ResourceLocation.parse(id);
        } catch (Exception e) {
            return null;
        }
    }

    // ──── 诊断 ────

    /** 缺失贴图的原图——取原版方块图集里的 missingno，与改造前的回落完全一致 */
    private static NativeImage missingTexture() {
        return Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(MISSING_TEXTURE)
                .contents().getOriginalImage();
    }

    /** 记一条 ERROR（同一个 id 只会到这里一次，{@link #sourceOf} 命中 {@link #FAILED} 后就不再进来） */
    private static void report(String id, String reason) {
        ColdWeaponCraftsmanship.LOGGER.error(
                "零件贴图 '{}' 无法生成，已回落缺失贴图。原因：{}", id, reason);
    }
}
