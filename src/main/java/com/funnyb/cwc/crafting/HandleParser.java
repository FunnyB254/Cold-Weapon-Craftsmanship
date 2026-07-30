package com.funnyb.cwc.crafting;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Map;

/**
 * 手柄零件解析器——parser="cwc:handle"。
 */
public class HandleParser extends BaseParser {

    @Override public String id() { return "cwc:handle"; }

    @Override
    public PartTypeDef parseType(String id, ResourceLocation location, ResourceManager rm) {
        ColdWeaponCraftsmanship.LOGGER.warn("HandleParser cannot parse types ({}), use DefaultParser", id);
        return null;
    }

    @Override
    public PartDef parsePart(String id, ResourceLocation location, ResourceManager rm) {
        return PartDef.of(id, "cwc:handle", Map.of());
    }
}
