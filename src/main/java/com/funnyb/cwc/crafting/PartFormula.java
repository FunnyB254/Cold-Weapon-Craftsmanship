package com.funnyb.cwc.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 金属零件的数值公式——{@code cwc:metal} 解析器的 {@code data} 值类型。
 *
 * @param base                 基线值。**目前唯一参与计算的字段**
 * @param hardnessMultiplier   随材料硬度缩放的系数
 * @param toughnessMultiplier  随材料韧性缩放的系数
 */
public record PartFormula(double base, double hardnessMultiplier, double toughnessMultiplier) {

    public static final Codec<PartFormula> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("base", 0.0).forGetter(PartFormula::base),
            Codec.DOUBLE.optionalFieldOf("hardnessMultiplier", 0.0).forGetter(PartFormula::hardnessMultiplier),
            Codec.DOUBLE.optionalFieldOf("toughnessMultiplier", 0.0).forGetter(PartFormula::toughnessMultiplier)
    ).apply(instance, PartFormula::new));

    // ─────────────────────────────────────────────────────────────────────
    //  ⚠ 两个乘数目前完全不参与计算——不要以为改了它们会有效果！
    //
    //  它们属于**尚未实现的「锻造」系统**（作者确认）：所有材料的**平均值与范围相同**，材料之间的差异
    //  由锻造的系数与常数来表达。所以数据里"38 份零件乘数完全一致"（damage 恒 h=0.5、durability 恒 h=5/t=2、
    //  speed 恒 0/0）是**刻意的**，不是复制粘贴痕迹——speed 与材料无关（取决于武器几何），按设计就填 0。
    //
    //  目前唯一生效的是 base，材料差异全部由各零件 JSON 手填的 base 承载
    //  （标准刃：铜 2.0 / 铁 2 / 金 2.5 / 钻 3.0 / 下界合金 3.5）。锻造系统上线后这层手填应由材料表取代。
    //
    //  看到一个属性数值不对时，先去查 base，不要去调乘数。详见 docs/bugs.md 的「数据设计缺口」一节。
    // ─────────────────────────────────────────────────────────────────────
}
