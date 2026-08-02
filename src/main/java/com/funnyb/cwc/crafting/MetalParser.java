package com.funnyb.cwc.crafting;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.List;
import java.util.Map;

/**
 * 金属材料解析器——parser="cwc:metal"。
 */
public class MetalParser extends BaseParser {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Integer.class, (JsonDeserializer<Integer>) (json, type, ctx) -> {
                if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString())
                    return (int) Long.decode(json.getAsString()).longValue();
                return json.getAsInt();
            })
            .registerTypeAdapter(int.class, (JsonDeserializer<Integer>) (json, type, ctx) -> {
                if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString())
                    return (int) Long.decode(json.getAsString()).longValue();
                return json.getAsInt();
            })
            .create();

    @Override public String id() { return "cwc:metal"; }

    @Override
    public PartTypeDef parseType(String id, ResourceLocation location, ResourceManager rm) {
        ColdWeaponCraftsmanship.LOGGER.warn("MetalParser cannot parse types ({}), use DefaultParser", id);
        return null;
    }

    @Override
    public PartDef parsePart(String id, ResourceLocation location, ResourceManager rm) {
        try {
            Resource resource = rm.getResourceOrThrow(location);
            try (Reader reader = resource.openAsReader()) {
                JsonMetalPart jp = GSON.fromJson(reader, JsonMetalPart.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> data = jp.data != null
                        ? (Map<String, Object>) (Map<?, ?>) jp.data : Map.of();
                return PartDef.of(id, "cwc:metal", data, parseRecipes(jp.recipes));
            }
        } catch (Exception e) {
            ColdWeaponCraftsmanship.LOGGER.error("Failed to parse metal part {}", location, e);
            return null;
        }
    }

    public static class Formula { public double base, hardnessMultiplier, toughnessMultiplier; }
    private static class JsonMetalPart {
        Map<String, Formula> data;
        List<JsonRecipe> recipes;
    }
}
