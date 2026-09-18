package com.funnyb.cwc.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金属材料形状——{@code "parser": "cwc:metal"}。
 * <p>
 * 与 {@code cwc:default} 的区别只有一个：{@code data} 的值是 {@link PartFormula}（{@code base} + 两个乘数）
 * 而不是标量。两个乘数**目前完全不参与计算**，原因见 {@link PartFormula} 的类注释——不要以为改了它们会有效果。
 * <p>
 * 本形状不解析零件类型（类型只有一种形状）。
 */
public final class MetalParser {

    /** 分派键 */
    public static final String ID = "cwc:metal";

    /** {@code Map<String, PartFormula>} 与 {@link PartDef#data()} 的 {@code Map<String, Object>} 之间的转换 */
    private static final Codec<Map<String, Object>> DATA =
            Codec.unboundedMap(Codec.STRING, PartFormula.CODEC).flatXmap(
                    MetalParser::widen,
                    MetalParser::narrow);

    static final MapCodec<PartDef> PART_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(PartDef::type),
            DATA.optionalFieldOf("data", Map.of()).forGetter(PartDef::data),
            DefaultParser.RECIPES.optionalFieldOf("recipes", List.of()).forGetter(PartDef::recipes)
    ).apply(instance, (type, data, recipes) -> new PartDef(ID, type, data, recipes)));

    /** {@code Map<String, PartFormula>} → {@code Map<String, Object>}（只读协变，安全） */
    @SuppressWarnings("unchecked")
    private static DataResult<Map<String, Object>> widen(Map<String, PartFormula> formulas) {
        return DataResult.success((Map<String, Object>) (Map<?, ?>) formulas);
    }

    /**
     * 反向转换。**对非 {@link PartFormula} 的值报错而不是丢弃**——只有手搓的 PartDef 才会出现这种情况，
     * 静默丢弃会让写出结果缺字段，而报错能立刻指出问题。
     */
    private static DataResult<Map<String, PartFormula>> narrow(Map<String, Object> data) {
        Map<String, PartFormula> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!(entry.getValue() instanceof PartFormula formula)) {
                return DataResult.error(() -> "cwc:metal 的 data 值必须是公式对象，键 '" + entry.getKey()
                        + "' 却是 " + (entry.getValue() == null ? "null" : entry.getValue().getClass().getSimpleName()));
            }
            out.put(entry.getKey(), formula);
        }
        return DataResult.success(out);
    }
}
