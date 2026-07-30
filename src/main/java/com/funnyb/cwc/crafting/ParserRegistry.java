package com.funnyb.cwc.crafting;

import java.util.HashMap;
import java.util.Map;

/**
 * 解析器注册表——按 parser 名（如 "cwc:default"）查找 BaseParser 实例。
 * 第三方模组通过 {@link #register} 注入自定义解析器。
 */
public final class ParserRegistry {

    private static final Map<String, BaseParser> parsers = new HashMap<>();

    static {
        register(new DefaultParser());
        register(new MetalParser());
        register(new HandleParser());
    }

    /** 注册解析器。同名会覆盖，允许第三方替换默认实现 */
    public static void register(BaseParser parser) {
        parsers.put(parser.id(), parser);
    }

    /** 按名查找解析器，找不到返回 null */
    public static BaseParser get(String id) {
        return parsers.get(id);
    }
}
