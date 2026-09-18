package com.funnyb.cwc.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 手柄材料形状——{@code "parser": "cwc:handle"}。
 * <p>
 * 手柄零件自身不贡献任何属性（武器的数值全部来自装配上去的刃/镡/配重），所以 {@code data} 恒为空。
 * 这个形状存在的意义只是把"没有 data"这件事与 {@code cwc:default} 区分开——{@code data} 为空是
 * {@link PartTypeDef#role()} 判定"这是手柄底座"的依据，让手柄的 JSON 显式写成这种形状更不容易写错。
 * <p>
 * 本形状不解析零件类型。
 */
public final class HandleParser {

    /** 分派键 */
    public static final String ID = "cwc:handle";

    static final MapCodec<PartDef> PART_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(PartDef::type),
            DefaultParser.RECIPES.optionalFieldOf("recipes", List.of()).forGetter(PartDef::recipes)
    ).apply(instance, (type, recipes) -> new PartDef(ID, type, Map.of(), recipes)));
}
