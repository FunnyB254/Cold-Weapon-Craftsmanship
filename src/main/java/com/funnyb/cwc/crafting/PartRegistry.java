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
        warnOnTypeCycles();

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

    /**
     * 类型图环检测——**仅记 ERROR，不阻止加载**。
     * <p>
     * 属性聚合（{@link WeaponStats}）与渲染都沿装配树递归，类型图上出现环意味着"理论上"可以
     * 无限嵌套。但递归遍历的是**物品树**而非类型图——玩家仍须逐级手工装配，深度实际由人力封顶，
     * 不构成崩溃风险。所以这条是**给数据作者的诊断**：发现问题只报，数据照常注册。
     */
    private static void warnOnTypeCycles() {
        // 建图：类型 → 它的槽位能接受的类型集合（按与装配界面相同的约束匹配规则）
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (PartTypeDef from : types.values()) {
            Set<String> accepted = new LinkedHashSet<>();
            for (PartTypeDef candidate : types.values()) {
                for (PartTypeDef.SlotDef slot : from.slots()) {
                    if (slotAccepts(slot, candidate)) {
                        accepted.add(candidate.id());
                        break;
                    }
                }
            }
            edges.put(from.id(), accepted);
        }

        Set<String> done = new HashSet<>();
        Set<String> onStack = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();
        for (String id : edges.keySet()) {
            findCycle(id, edges, done, onStack, path);
        }
    }

    /** 深度优先找环；发现回边就打印完整环路径 */
    private static void findCycle(String node, Map<String, Set<String>> edges, Set<String> done,
                                  Set<String> onStack, Deque<String> path) {
        if (done.contains(node)) return;
        if (onStack.contains(node)) {
            List<String> cycle = new ArrayList<>();
            boolean started = false;
            for (String s : path) {          // ArrayDeque 迭代顺序 = 入栈顺序
                if (s.equals(node)) started = true;
                if (started) cycle.add(s);
            }
            cycle.add(node);
            ColdWeaponCraftsmanship.LOGGER.error(
                    "类型图存在环，装配可无限嵌套：{}（仅提示，不阻止加载）", String.join(" -> ", cycle));
            return;
        }
        onStack.add(node);
        path.addLast(node);
        for (String next : edges.getOrDefault(node, Set.of())) {
            findCycle(next, edges, done, onStack, path);
        }
        path.removeLast();
        onStack.remove(node);
        done.add(node);
    }

    /**
     * 槽位约束能否被该类型满足——与 {@code AssemblingMenu.matchesConstraint} 同规则：
     * 约束为空 = 全收；约束键在候选类型 data 中缺失或不匹配即拒绝。
     * 注意匹配的是类型的**语义值**（如 data.type = "attack"/"guard"），不是类型 id。
     */
    private static boolean slotAccepts(PartTypeDef.SlotDef slot, PartTypeDef candidate) {
        Map<String, List<String>> constraint = slot.constraint();
        if (constraint == null || constraint.isEmpty()) return true;
        for (Map.Entry<String, List<String>> entry : constraint.entrySet()) {
            List<String> allowed = entry.getValue();
            if (allowed == null) continue;
            String value = candidate.data().get(entry.getKey());
            if (value == null || !allowed.contains(value)) return false;
        }
        return true;
    }

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
