package com.funnyb.cwc.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * 配方中的单个材料——物品 id + 数量。配方在图形上恒为 3 格，允许留空。
 * <p>
 * <b>"该格无材料要求"在 JSON 里写成空对象 {@code {}}</b>（即没有 {@code item} 字段），
 * 解码成 {@code item} 为 {@link Optional#empty()} 的实例，用 {@link #isNone()} 判断。
 * <p>
 * 为什么是 {@code {}} 而不是 {@code null}：DFU 的列表 codec 不接受 {@code null} 元素，
 * 而"用哨兵 + {@code Codec.either} 兜住 null"的做法也不行——{@code Codec.unit} 内部是按 map 取输入的，
 * 遇到 null 同样失败（实测报 {@code Failed to parse either. First/Second: Not a JSON object: null}）。
 * <p>
 * 为什么 {@code item} 是 {@link Optional} 而不是可空的 {@code String}：DFU 组装记录时会对每个组件调
 * {@code Optional.of(值)}，**组件解出 null 就直接 NPE**。所以可空字段只能用 {@code Optional} 表达。
 * <p>
 * 物品 id 在**解码期只校验语法**（必须是合法的 {@code ResourceLocation}），**存在性**仍在使用时由
 * {@link PartRecipes} 检查——两者分开是有意的：语法错误应当在加载数据包时就报出来，
 * 而"引用了未安装模组的物品"只该让那一条配方作废，不该让整个零件加载失败。
 */
public record IngredientDef(Optional<String> item, int count) {

    /** 单条材料。{@code count} 缺失按 1（与旧的 {@code Math.max(1, count)} 口径一致） */
    public static final Codec<IngredientDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // 无默认值的 optionalFieldOf 得到 MapCodec<Optional<String>>，与组件类型天然配对
            Codec.STRING.optionalFieldOf("item").forGetter(IngredientDef::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(IngredientDef::count)
    ).apply(instance, IngredientDef::new));

    /** "该格无材料要求"的实例——JSON 里就是 {@code {}} */
    public static IngredientDef none() {
        return new IngredientDef(Optional.empty(), 1);
    }

    /** 是否"该格无材料要求" */
    public boolean isNone() {
        return item.isEmpty();
    }

    /** 该格的物品 id 字符串；无要求时返回空串——**调用方应先查 {@link #isNone()}** */
    public String itemId() {
        return item.orElse("");
    }
}
