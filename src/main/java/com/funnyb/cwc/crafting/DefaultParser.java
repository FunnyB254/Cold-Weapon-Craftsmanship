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
 * 默认 JSON 解析器——parser="cwc:default"。
 */
public class DefaultParser extends BaseParser {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Integer.class, (JsonDeserializer<Integer>) (json, type, ctx) -> {
                if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString())
                    return Integer.decode(json.getAsString());
                return json.getAsInt();
            })
            .registerTypeAdapter(int.class, (JsonDeserializer<Integer>) (json, type, ctx) -> {
                if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString())
                    return Integer.decode(json.getAsString());
                return json.getAsInt();
            })
            .create();

    @Override public String id() { return "cwc:default"; }

    @Override
    public PartTypeDef parseType(String id, ResourceLocation location, ResourceManager rm) {
        try {
            Resource resource = rm.getResourceOrThrow(location);
            try (Reader reader = resource.openAsReader()) {
                JsonType jt = GSON.fromJson(reader, JsonType.class);
                return new PartTypeDef(id, jt.data != null ? jt.data : Map.of(),
                        jt.slots != null ? jt.slots : List.of(),
                        jt.position != null ? jt.position : null);
            }
        } catch (Exception e) {
            ColdWeaponCraftsmanship.LOGGER.error("Failed to parse type {}", location, e);
            return null;
        }
    }

    @Override
    public PartDef parsePart(String id, ResourceLocation location, ResourceManager rm) {
        try {
            Resource resource = rm.getResourceOrThrow(location);
            try (Reader reader = resource.openAsReader()) {
                JsonPart jp = GSON.fromJson(reader, JsonPart.class);
                String parser = jp.parser != null ? jp.parser : "cwc:default";
                @SuppressWarnings("unchecked")
                Map<String, Object> data = jp.data != null
                        ? (Map<String, Object>) (Map<?, ?>) jp.data : Map.of();
                return PartDef.of(id, parser, data);
            }
        } catch (Exception e) {
            ColdWeaponCraftsmanship.LOGGER.error("Failed to parse part {}", location, e);
            return null;
        }
    }

    private static class JsonType {
        Map<String, String> data;
        List<PartTypeDef.SlotDef> slots;
        PartTypeDef.Position position;
    }
    private static class JsonPart { String parser; Map<String, Double> data; }
}
