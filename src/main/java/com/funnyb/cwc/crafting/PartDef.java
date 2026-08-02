package com.funnyb.cwc.crafting;

import java.util.List;
import java.util.Map;

/**
 * 零件定义——由材料解析器解析。
 *
 * @param id          零件完整标识
 * @param parser      材料解析器名
 * @param data        解析器特定数据
 * @param recipes     配方列表——每个配方是 3 个 IngredientDef（含 null）
 * @param displayName 语言文件 key
 */
public record PartDef(String id, String parser,
                      Map<String, Object> data,
                      List<List<IngredientDef>> recipes,
                      String displayName) {

    public String typeId() {
        int lastDot = id.lastIndexOf('.');
        return lastDot > 0 ? id.substring(0, lastDot) : id;
    }

    public static PartDef of(String id, String parser,
                             Map<String, Object> data,
                             List<List<IngredientDef>> recipes) {
        return new PartDef(id, parser, data, recipes, "part." + id);
    }
}
