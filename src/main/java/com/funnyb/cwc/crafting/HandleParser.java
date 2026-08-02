package com.funnyb.cwc.crafting;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.google.gson.Gson;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.List;
import java.util.Map;

/**
 * 手柄零件解析器——parser="cwc:handle"。
 */
public class HandleParser extends BaseParser {

    private static final Gson GSON = new Gson();

    @Override public String id() { return "cwc:handle"; }

    @Override
    public PartTypeDef parseType(String id, ResourceLocation location, ResourceManager rm) {
        ColdWeaponCraftsmanship.LOGGER.warn("HandleParser cannot parse types ({}), use DefaultParser", id);
        return null;
    }

    @Override
    public PartDef parsePart(String id, ResourceLocation location, ResourceManager rm) {
        try {
            Resource resource = rm.getResourceOrThrow(location);
            try (Reader reader = resource.openAsReader()) {
                JsonHandlePart jp = GSON.fromJson(reader, JsonHandlePart.class);
                return PartDef.of(id, "cwc:handle", Map.of(), parseRecipes(jp.recipes));
            }
        } catch (Exception e) {
            ColdWeaponCraftsmanship.LOGGER.error("Failed to parse handle part {}", location, e);
            return null;
        }
    }

    private static class JsonHandlePart {
        List<JsonRecipe> recipes;
    }
}
