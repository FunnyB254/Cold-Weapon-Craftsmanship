package com.funnyb.cwc.crafting;

import java.util.List;
import java.util.Map;

/**
 * 零件类型定义——由 BaseParser.parseType 解析。
 *
 * @param id       类型标识，如 "cwc.standard_blade"
 * @param data     键值对。非空时为 part，空时为 handle_part
 * @param slots    槽位列表。空数组表示不能接收其他零件
 * @param position 组装渲染偏移。null 表示无偏移（默认 (0,0)）
 */
public record PartTypeDef(String id, Map<String, String> data, List<SlotDef> slots,
                          Position position) {

    public String role() {
        return data.isEmpty() ? "handle_part" : "part";
    }

    /**
     * 单个装配槽位。
     * @param name       槽位的语言文件 key
     * @param constraint 匹配约束
     * @param scale      属性加权系数
     */
    public record SlotDef(String name, Map<String, java.util.List<String>> constraint,
                          Map<String, Double> scale) {}

    /** 组装渲染偏移，零件 data 中的 "position" 字段 */
    public record Position(int x, int y) {}
}
