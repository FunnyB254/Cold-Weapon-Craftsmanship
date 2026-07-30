package com.funnyb.cwc.crafting;

import java.util.Map;

/**
 * 零件定义——由材料解析器解析。
 *
 * @param id          零件完整标识，如 "cwc.standard_blade.iron"
 * @param parser      材料解析器名
 * @param data        解析器特定数据
 * @param displayName 语言文件 key，如 "part.cwc.standard_blade.iron"
 */
public record PartDef(String id, String parser,
                      Map<String, Object> data, String displayName) {

    /** 从 id 推导类型标识。id 格式: <ns>.<type>.<name> → type = <ns>.<type> */
    public String typeId() {
        int lastDot = id.lastIndexOf('.');
        return lastDot > 0 ? id.substring(0, lastDot) : id;
    }

    public static PartDef of(String id, String parser, Map<String, Object> data) {
        return new PartDef(id, parser, data, "part." + id);
    }
}
