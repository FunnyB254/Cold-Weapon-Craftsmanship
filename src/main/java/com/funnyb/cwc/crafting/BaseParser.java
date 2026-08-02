package com.funnyb.cwc.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 零件解析器抽象基类。
 * id 由 PartRegistry 从文件路径推导后传入，解析器不负责生成 id。
 */
public abstract class BaseParser {

    public abstract String id();

    /** @param id 零件/类型标识，从文件路径推导 */
    public abstract PartTypeDef parseType(String id, ResourceLocation location, ResourceManager rm);

    /** @param id 零件标识，从文件路径推导 */
    public abstract PartDef parsePart(String id, ResourceLocation location, ResourceManager rm);

    /** recipes 中转 —— 把 JSON 包装层去掉 */
    protected static class JsonRecipe {
        public java.util.List<IngredientDef> ingredients;
    }

    protected static java.util.List<java.util.List<IngredientDef>> parseRecipes(
            java.util.List<JsonRecipe> raw) {
        if (raw == null) return java.util.List.of();
        return raw.stream().map(r -> r.ingredients != null ? r.ingredients : java.util.List.<IngredientDef>of()).toList();
    }
}
