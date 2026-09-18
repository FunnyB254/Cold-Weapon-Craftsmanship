package com.funnyb.cwc.crafting;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 零件解析器注册表——{@code "parser"} 字段 → 该数据形状的 codec。
 * <p>
 * 2026-09-17 从"注册 {@code BaseParser} 实例"改为"**注册 {@code MapCodec}**"：零件定义搬进 datapack registry
 * 后由原版加载，形状必须能用 codec 表达（也因此解析不再碰 IO）。**可插拔这件事本身保留了**——
 * 第三方模组仍可用自己的 parser id 注册自定义数据形状，只是接口从"解析器"变成"codec"。
 * <p>
 * 同名会覆盖，允许第三方替换默认实现。
 */
public final class ParserRegistry {

    private static final Map<String, MapCodec<? extends PartDef>> PART_CODECS = new HashMap<>();

    static {
        register(PartDef.DEFAULT_PARSER, DefaultParser.PART_CODEC);
        register(MetalParser.ID, MetalParser.PART_CODEC);
        register(HandleParser.ID, HandleParser.PART_CODEC);
    }

    private ParserRegistry() {}

    /**
     * 注册一个数据形状。**面向第三方模组的扩展点**：用自己的 parser id 让零件 JSON 里的
     * {@code "parser"} 指向你的 codec。同名覆盖。
     *
     * @param parserId 分派键，建议带自己的命名空间（如 {@code yourmod:foo}）
     */
    public static void register(String parserId, MapCodec<? extends PartDef> partCodec) {
        PART_CODECS.put(parserId, partCodec);
    }

    /** 已注册的全部 parser id */
    public static Set<String> registeredIds() {
        return Set.copyOf(PART_CODECS.keySet());
    }

    /** 被 {@link PartDef#CODEC} 的分派调用。未知 id 返回一个**必定报错**的 codec，而不是抛异常 */
    static MapCodec<? extends PartDef> partCodecFor(String parserId) {
        MapCodec<? extends PartDef> codec = PART_CODECS.get(parserId);
        return codec != null ? codec : unknownParserCodec(parserId);
    }

    /**
     * 未知 parser 时用的 codec——把失败做进 {@link DataResult}，**不抛异常**。
     * <p>
     * 抛异常会中断整个数据包加载；而"某个零件的 parser 拼错了"只该让那一条零件加载失败、
     * 在日志里报出是哪个 id，其余的照常。所以这里返回一个解码必定失败的 codec，消息里带上那个 id。
     * <p>
     * {@code MapCodec.unit} 需要一个非 null 的占位值（它不读输入、直接返回该值，随后的 flatXmap 必定报错）。
     */
    private static MapCodec<? extends PartDef> unknownParserCodec(String parserId) {
        PartDef placeholder = new PartDef(parserId, null, Map.of(), java.util.List.of());
        return MapCodec.unit(placeholder).flatXmap(
                ignored -> DataResult.error(() -> "未知的零件解析器 parser=\"" + parserId
                        + "\"；已注册的有 " + PART_CODECS.keySet()),
                ignored -> DataResult.error(() -> "未知的零件解析器 parser=\"" + parserId + "\"，无法写出"));
    }

    /** 该 id 是否已注册（供诊断/校验用） */
    public static boolean isRegistered(String parserId) {
        return PART_CODECS.containsKey(parserId);
    }

    /** 按 id 取 codec，未注册返回空——与 {@link #partCodecFor} 的区别是不造占位 codec */
    public static Optional<MapCodec<? extends PartDef>> find(String parserId) {
        return Optional.ofNullable(PART_CODECS.get(parserId));
    }
}
