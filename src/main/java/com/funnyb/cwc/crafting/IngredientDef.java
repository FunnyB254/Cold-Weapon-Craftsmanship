package com.funnyb.cwc.crafting;

/**
 * 配方中的单个材料——item 或 tag + 数量。
 */
public record IngredientDef(String item, String tag, int count) {}
