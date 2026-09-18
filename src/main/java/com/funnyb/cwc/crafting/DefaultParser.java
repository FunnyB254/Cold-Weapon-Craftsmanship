package com.funnyb.cwc.crafting;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 默认数据形状——{@code "parser": "cwc:default"}，也是**零件类型唯一使用的形状**。
 * <p>
 * 2026-09-17 从 GSON 解析器改写为 codec：零件定义搬进 datapack registry 后由原版加载，
 * 形状必须能用 codec 表达，因而解析不再碰 IO（旧实现要 {@code ResourceManager} 是为了自己读文件）。
 * <p>
 * {@code data} 的值是**数字或字符串混合**的：{@code weight} 这类语义键是字符串（供槽位约束匹配），
 * {@code block} 这类数值属性是数字。类型声明里的 {@code data} 则全是字符串。
 */
public final class DefaultParser {

    private DefaultParser() {}

    /**
     * {@code data} 的单个值——数字或字符串。
     * <p>
     * 解码先试数字：非数字的 JSON 值（如 {@code "light"}）在非宽容的 ops 下报错，随后回落到字符串。
     * 编码方向对意外的类型返回错误而不是抛异常——这样"手搓了一个 data 里放对象/列表的 PartDef"
     * 会在写出时得到明确报错，而不是静默写出坏数据。
     */
    private static final Codec<Object> DATA_VALUE = Codec.either(Codec.DOUBLE, Codec.STRING).flatXmap(
            either -> DataResult.success((Object) either.map(d -> d, s -> s)),
            value -> {
                if (value instanceof Double d) return DataResult.success(Either.left(d));
                if (value instanceof String s) return DataResult.success(Either.right(s));
                return DataResult.error(() -> "cwc:default 的 data 值只能是数字或字符串，实际是 "
                        + (value == null ? "null" : value.getClass().getSimpleName()));
            });

    /** 一条配方——JSON 里是 {@code {"ingredients": [...]}}；"无要求"的格写 {@code {}} */
    private static final MapCodec<List<IngredientDef>> RECIPE =
            IngredientDef.CODEC.listOf().fieldOf("ingredients");

    /** 配方列表；缺失按空表（类型定义没有配方，零件一般都有） */
    static final Codec<List<List<IngredientDef>>> RECIPES = RECIPE.codec().listOf();

    /** 零件定义（{@code cwc:default} 形状）——{@code type} 必填，这是从"按路径推导"改成"显式字段"的关键 */
    static final MapCodec<PartDef> PART_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(PartDef::type),
            Codec.unboundedMap(Codec.STRING, DATA_VALUE).optionalFieldOf("data", Map.of())
                    .forGetter(PartDef::data),
            RECIPES.optionalFieldOf("recipes", List.of()).forGetter(PartDef::recipes)
    ).apply(instance, (type, data, recipes) -> new PartDef(PartDef.DEFAULT_PARSER, type, data, recipes)));
}
