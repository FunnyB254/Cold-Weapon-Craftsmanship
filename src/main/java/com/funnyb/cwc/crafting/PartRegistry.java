package com.funnyb.cwc.crafting;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 零件注册表，扫描 data 下的 cwc 目录加载所有类型和零件。
 * id 从文件路径推导，不在 JSON 中重复存储。
 */
public final class PartRegistry {

    private static final Map<String, PartTypeDef> types = new LinkedHashMap<>();
    private static final Map<String, PartDef> parts = new LinkedHashMap<>();

    private PartRegistry() {}

    /** 扫描并加载所有类型和零件 JSON */
    public static void reload(ResourceManager rm) {
        types.clear();
        parts.clear();

        // 类型：key = "ns:first_folder/type_name" → id = "first_folder.type_name"
        Map<String, ResourceLocation> typeFiles = scan(rm, "types", ".json");
        for (var entry : typeFiles.entrySet()) {
            String id = entry.getKey().substring(entry.getKey().indexOf(':') + 1)
                    .replace('/', '.');
            PartTypeDef def = parseType(id, entry.getValue(), rm);
            if (def != null) types.put(def.id(), def);
        }
        ColdWeaponCraftsmanship.LOGGER.info("Loaded {} part types", types.size());

        // 零件：key = "ns:first_folder/type_name/part_name" → id = "first_folder.type_name.part_name"
        Map<String, ResourceLocation> partFiles = scan(rm, "parts", ".json");
        for (var entry : partFiles.entrySet()) {
            String id = entry.getKey().substring(entry.getKey().indexOf(':') + 1)
                    .replace('/', '.');
            PartDef def = parsePart(id, entry.getValue(), rm);
            if (def != null) parts.put(def.id(), def);
        }
        ColdWeaponCraftsmanship.LOGGER.info("Loaded {} parts", parts.size());
    }

    // --- 查询 ---

    public static PartTypeDef getTypeDef(String id) { return types.get(id); }
    public static PartDef getPartDef(String id) { return parts.get(id); }
    public static Collection<PartDef> getAllParts() { return parts.values(); }

    public static List<PartDef> getPartsByType(String typeId) {
        return parts.values().stream()
                .filter(p -> p.typeId().equals(typeId))
                .toList();
    }

    // --- 内部 ---

    private static Map<String, ResourceLocation> scan(ResourceManager rm, String basePath, String suffix) {
        Map<String, ResourceLocation> result = new LinkedHashMap<>();
        String prefix = basePath + "/";
        for (var ns : rm.getNamespaces()) {
            rm.listResources(basePath, loc -> loc.getPath().endsWith(suffix)).forEach((loc, res) -> {
                String path = loc.getPath();
                int idx = path.indexOf(prefix);
                if (idx < 0) return;
                String rel = path.substring(idx + prefix.length());
                String key = ns + ":" + rel.substring(0, rel.length() - suffix.length());
                result.put(key, loc);
            });
        }
        return result;
    }

    private static PartTypeDef parseType(String id, ResourceLocation loc, ResourceManager rm) {
        String parserId = readField(loc, rm, "parser");
        BaseParser parser = ParserRegistry.get(parserId != null ? parserId : "cwc:default");
        if (parser == null) {
            ColdWeaponCraftsmanship.LOGGER.error("Unknown parser '{}' for type {}", parserId, loc);
            return null;
        }
        return parser.parseType(id, loc, rm);
    }

    private static PartDef parsePart(String id, ResourceLocation loc, ResourceManager rm) {
        String parserId = readField(loc, rm, "parser");
        BaseParser parser = ParserRegistry.get(parserId != null ? parserId : "cwc:default");
        if (parser == null) {
            ColdWeaponCraftsmanship.LOGGER.error("Unknown parser '{}' for part {}", parserId, loc);
            return null;
        }
        return parser.parsePart(id, loc, rm);
    }

    /** 读取 JSON 顶层某个字符串字段 */
    static String readField(ResourceLocation loc, ResourceManager rm, String field) {
        try {
            var resource = rm.getResourceOrThrow(loc);
            try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                var obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (obj.has(field)) return obj.get(field).getAsString();
            }
        } catch (Exception e) {
            ColdWeaponCraftsmanship.LOGGER.warn("Failed to read field '{}' from {}: {}", field, loc, e.toString());
        }
        return null;
    }
}
