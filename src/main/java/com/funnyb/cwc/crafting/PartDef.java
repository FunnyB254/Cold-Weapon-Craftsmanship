package com.funnyb.cwc.crafting;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 零件定义——datapack registry {@code coldweaponcraftsmanship:cwc/part} 的条目类型。
 * <p>
 * <b>没有 {@code id} 字段</b>：id 由注册表键提供（文件名决定），codec 不该也无法自己写它。
 * 需要"id + 定义"两者时用 {@code PartRegistry.entries()}。
 * <p>
 * <b>{@code type} 是显式字段</b>，取代旧的"去掉 id 最后一个点号推父类型"——那等于把目录结构变成语义，
 * 第三方无法自由布局。显式字段还有一个好处：某个零件属于哪个类型一眼可见。
 * <p>
 * 数据形状由 {@code "parser"} 字段分派（{@code Codec.dispatch} → {@link ParserRegistry}）：
 * {@code data} 的值类型随 parser 而不同（default 是数字/字符串混合、metal 是 {@link PartFormula}），
 * 由各自的 codec 声明。
 *
 * @param parser  数据形状分派键，同时是 JSON 里的 {@code "parser"} 字段。自定义形状用 {@code ParserRegistry.register}
 * @param type    所属零件类型的注册表 id
 * @param data    解析器特定的键值对；{@link AssemblyTree} 读其中的数值属性，槽位约束匹配读其中的语义键
 * @param recipes 配方列表，每条固定 3 格；"该格无材料要求"写成空对象 {@code {}}（见 {@link IngredientDef}）
 */
public record PartDef(String parser, ResourceLocation type,
                      Map<String, Object> data,
                      List<List<IngredientDef>> recipes) {

    /** 默认解析器 id——数据形状按 {@code DefaultParser}，也是类型唯一使用的形状 */
    public static final String DEFAULT_PARSER = "cwc:default";

    /**
     * 零件定义的编解码器——按 {@code "parser"} 字段分派到各解析器声明的数据形状。
     * <p>
     * 分派键是 {@code Codec.STRING}，因此 **JSON 里必须写 {@code parser} 字段**（现有 38 份零件都有）。
     */
    public static final Codec<PartDef> CODEC =
            Codec.STRING.<PartDef>dispatch("parser", PartDef::parser, ParserRegistry::partCodecFor);

    /**
     * 语言文件 key——{@code part.<命名空间>.<路径以 . 连接>}，例
     * {@code part.coldweaponcraftsmanship.standard_blade.iron}。
     * <p>
     * 带命名空间是为了第三方扩展包不会和本模组的 key 撞车——与"开放集成"的目标一致，代价是 key 变长。
     */
    public static String langKey(ResourceLocation id) {
        return "part." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }
}
