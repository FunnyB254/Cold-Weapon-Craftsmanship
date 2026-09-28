package com.funnyb.cwc.client.sprite;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * 「精灵图 + 调色板」两种 JSON 的数据类与解析。**只做解析与校验，不碰资源管理器、不碰贴图**——
 * 加载与缓存见 {@link SpriteTextures}。
 * <p>
 * 两种文件的分工：
 * <ul>
 *   <li><b>精灵图</b>（{@code cwc/sprite/<类型>/<组>.json}）——形状。{@code pixels} 是二维数组，
 *       每个值是调色板下标；<b>每行长度必须一致</b>（不一致直接报错，见 {@link #parseSprite}）。</li>
 *   <li><b>调色板</b>（{@code cwc/palette/<类型>/<材质>.json}）——配色。{@code sprite} 指向用哪条精灵图，
 *       {@code colors} 是 RGBA 十六进制串列表。<b>下标 0 保留为透明项</b>。</li>
 * </ul>
 * <p>
 * 同一组材质共用一条精灵图（分组只写在调色板的 {@code sprite} 字段里，精灵图自己不知道有谁在用它），
 * 所以"哪些材质共用一条"改数据即可，不必改代码。
 * <p>
 * <b>颜色格式</b>：{@code #RRGGBBAA} / {@code RRGGBBAA} / {@code #RRGGBB}（六位 = 不透明）。
 * 写入 {@code NativeImage} 前要转成它内部的 <b>ABGR 打包</b>（{@link com.mojang.blaze3d.platform.NativeImage}
 * 的 {@code getPixelRGBA}/{@code setPixelRGBA} 实际按 ABGR 打包，见 {@link #decodeColors}）。
 */
public final class SpritePalette {

    private SpritePalette() {}

    /** 精灵图：{@code pixels[y][x]} = 调色板下标。构造时已保证非空、每行等长。 */
    public record Sprite(int[][] pixels) {
        /** 列数（= 贴图宽度） */
        public int width() { return pixels[0].length; }
        /** 行数（= 贴图高度） */
        public int height() { return pixels.length; }
    }

    /**
     * 调色板：用哪条精灵图 + 颜色表。
     *
     * @param sprite 精灵图引用，形如 {@code 命名空间:类型/组}（对应 {@code cwc/sprite/<类型>/<组>.json}）
     * @param colors RGBA 十六进制串，下标 0 必须是透明项
     */
    public record Palette(String sprite, String[] colors) {}

    /** 格式错误——缺字段、行不等长、下标越界、颜色串非法，全走这一个异常，由调用方统一记 ERROR */
    public static class SpriteFormatException extends RuntimeException {
        public SpriteFormatException(String message) { super(message); }
    }

    /**
     * 解析精灵图。
     * <p>
     * 校验（任一不过即抛）：{@code pixels} 存在且是非空数组；每行都是数组；<b>每行长度与首行一致</b>；
     * 每个值是非负整数。越界下标查不出（那要看调色板有多长），留给 {@link #decodeColors} 之后的逐像素写入。
     */
    public static Sprite parseSprite(JsonObject json) {
        JsonElement raw = json.get("pixels");
        if (raw == null) {
            throw new SpriteFormatException("缺少 'pixels' 字段");
        }
        if (!raw.isJsonArray()) {
            throw new SpriteFormatException("'pixels' 不是数组");
        }
        JsonArray rows = raw.getAsJsonArray();
        if (rows.isEmpty()) {
            throw new SpriteFormatException("'pixels' 为空数组（至少要有 1 行）");
        }
        int h = rows.size();
        int[][] pixels = new int[h][];
        int w = -1;
        for (int y = 0; y < h; y++) {
            JsonElement rowEl = rows.get(y);
            if (!rowEl.isJsonArray()) {
                throw new SpriteFormatException("'pixels' 第 " + y + " 行不是数组");
            }
            JsonArray row = rowEl.getAsJsonArray();
            if (w < 0) {
                w = row.size();
                if (w == 0) {
                    throw new SpriteFormatException("'pixels' 第 0 行为空（至少要有 1 列）");
                }
            } else if (row.size() != w) {
                // 作者要求：每行长度必须一致，否则直接报错
                throw new SpriteFormatException(
                        "'pixels' 每行长度必须一致：第 " + y + " 行有 " + row.size() + " 列，首行是 " + w + " 列");
            }
            int[] line = new int[w];
            for (int x = 0; x < w; x++) {
                JsonElement v = row.get(x);
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
                    throw new SpriteFormatException("'pixels' 第 " + y + " 行第 " + x + " 列不是数字");
                }
                int idx = v.getAsInt();
                if (idx < 0) {
                    throw new SpriteFormatException("'pixels' 第 " + y + " 行第 " + x + " 列是负数 " + idx);
                }
                line[x] = idx;
            }
            pixels[y] = line;
        }
        return new Sprite(pixels);
    }

    /**
     * 解析调色板。校验：{@code sprite} 是非空字符串、{@code colors} 是非空字符串数组、
     * 并且<b>第 0 项是透明项</b>（精灵图用 0 表示背景，第 0 项若不透明，背景会被涂成那个颜色）。
     */
    public static Palette parsePalette(JsonObject json) {
        JsonElement spriteEl = json.get("sprite");
        if (spriteEl == null) {
            throw new SpriteFormatException("缺少 'sprite' 字段");
        }
        if (!spriteEl.isJsonPrimitive() || !spriteEl.getAsJsonPrimitive().isString()) {
            throw new SpriteFormatException("'sprite' 不是字符串");
        }
        String sprite = spriteEl.getAsString();
        if (sprite.isEmpty()) {
            throw new SpriteFormatException("'sprite' 为空字符串");
        }

        JsonElement colorsEl = json.get("colors");
        if (colorsEl == null) {
            throw new SpriteFormatException("缺少 'colors' 字段");
        }
        if (!colorsEl.isJsonArray()) {
            throw new SpriteFormatException("'colors' 不是数组");
        }
        JsonArray arr = colorsEl.getAsJsonArray();
        if (arr.isEmpty()) {
            throw new SpriteFormatException("'colors' 为空数组");
        }
        String[] colors = new String[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            JsonElement c = arr.get(i);
            if (!c.isJsonPrimitive() || !c.getAsJsonPrimitive().isString()) {
                throw new SpriteFormatException("'colors' 第 " + i + " 项不是字符串");
            }
            colors[i] = c.getAsString();
        }
        if ((parseColor(colors[0]) >>> 24) != 0) {
            throw new SpriteFormatException("'colors' 第 0 项必须是透明项（alpha = 00），现在是 " + colors[0]);
        }
        return new Palette(sprite, colors);
    }

    /**
     * 颜色字符串表 → {@code NativeImage} 用的打包整数表。
     * <p>
     * 打包格式是 Minecraft 的「ABGR32」——{@code alpha << 24 | blue << 16 | green << 8 | red}。
     * 名字叫 ABGR 容易误会成"内存里也是这个顺序"：它在内存里是 {@code R,G,B,A} 四个字节，
     * 小端读成 int 才是 {@code A<<24|B<<16|G<<8|R}。{@code getPixelRGBA}/{@code setPixelRGBA} 读写的就是这个 int，
     * 所以照着 {@code FastColor.ABGR32.color(alpha, blue, green, red)} 的写法打包即可。
     */
    public static int[] decodeColors(String[] colors) {
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            try {
                out[i] = parseColor(colors[i]);
            } catch (SpriteFormatException e) {
                // 补上"第几项"——parseColor 拿不到下标，而作者改调色板时最需要知道的就是这一项
                throw new SpriteFormatException("'colors' 第 " + i + " 项：" + e.getMessage());
            }
        }
        return out;
    }

    /** 单个颜色串 → 打包整数（见 {@link #decodeColors} 的格式说明） */
    public static int parseColor(String s) {
        String v = s.startsWith("#") ? s.substring(1) : s;
        if (v.length() != 6 && v.length() != 8) {
            throw new SpriteFormatException("颜色 '" + s + "' 长度不对（应为 RRGGBB 或 RRGGBBAA）");
        }
        long n;
        try {
            n = Long.parseLong(v, 16);
        } catch (NumberFormatException e) {
            throw new SpriteFormatException("颜色 '" + s + "' 不是十六进制");
        }
        int r, g, b, a;
        if (v.length() == 6) {
            r = (int) ((n >> 16) & 0xFF);
            g = (int) ((n >> 8) & 0xFF);
            b = (int) (n & 0xFF);
            a = 0xFF;      // 六位写法 = 不透明
        } else {
            r = (int) ((n >> 24) & 0xFF);
            g = (int) ((n >> 16) & 0xFF);
            b = (int) ((n >> 8) & 0xFF);
            a = (int) (n & 0xFF);
        }
        return a << 24 | b << 16 | g << 8 | r;
    }

    /** 打包整数的可读形式（诊断日志用），输出 {@code #RRGGBBAA} */
    public static String describeColor(int packed) {
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X",
                packed & 0xFF, (packed >> 8) & 0xFF, (packed >> 16) & 0xFF, (packed >>> 24) & 0xFF);
    }
}
