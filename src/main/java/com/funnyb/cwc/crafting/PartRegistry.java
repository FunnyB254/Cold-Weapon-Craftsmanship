package com.funnyb.cwc.crafting;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.combat.behavior.BehaviorField;
import com.funnyb.cwc.registry.CwcRegistries;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 零件定义的查询门面——**关卡作用域的注册表缓存**。
 * <p>
 * 零件与类型定义存放在两个数据包注册表里（{@link CwcRegistries}），由原版加载并同步到客户端。
 * 但本模组的查询点分布在拿不到 {@code RegistryAccess} 的地方——最典型的是
 * {@code CwcWeapon.getDefaultAttributeModifiers(ItemStack)}（属性按需推导要在那里查定义，而该方法的签名里
 * 没有 {@code HolderLookup.Provider}）。所以这里在关卡加载时缓存一次注册表引用，之后全部查询走静态入口。
 * <p>
 * 这是一个**关卡作用域**的缓存，不是"自己维护的数据表"：内容始终是原版注册表本身（不复制、不解析），
 * 关卡加载时绑定、卸载时解绑。这样既保住了"20 处查询点一行不用改"，也没有把数据的所有权从原版那里拿回来。
 * <p>
 * <b>未绑定时（进关卡之前，或关卡卸载后）所有查询返回 null</b>——调用方一律按"查不到定义"降级
 * （属性退化为空、渲染跳过该零件），不会崩。这与旧的"客户端没有数据"是同一条降级路径。
 */
public final class PartRegistry {

    /** 当前关卡的注册表引用；未绑定时为 null */
    private static Registry<PartTypeDef> typeRegistry;
    private static Registry<PartDef> partRegistry;

    private PartRegistry() {}

    // ──── 生命周期 ────

    /**
     * 绑定当前关卡的注册表——由 {@code LevelEvent.Load} 调用，**客户端与服务端都会触发**，
     * 所以一个钩子同时覆盖两端（单人的集成服务器两端共用同一份注册表，绑定两次也是同一个对象）。
     */
    public static void bind(RegistryAccess access) {
        typeRegistry = access.registry(CwcRegistries.PART_TYPE).orElse(null);
        partRegistry = access.registry(CwcRegistries.PART).orElse(null);
        if (partRegistry == null) {
            // 只会在数据包尚未加载时出现；绑定不上不影响运行（查询降级），但要能诊断
            ColdWeaponCraftsmanship.LOGGER.warn("零件注册表尚未就绪，零件查询将暂时返回空");
            return;
        }
        ColdWeaponCraftsmanship.LOGGER.info("Loaded {} part types / {} parts",
                typeRegistry == null ? 0 : typeRegistry.size(), partRegistry.size());
        warnOnTypeCycles();
        warnOnSlotPriorityTies();
    }

    /**
     * 槽位优先级**潜在并列**检测——**仅记 WARN，不阻止加载**。
     * <p>
     * 行为的层内解析规则是"并列 = 该字段无胜者"（见 {@code BehaviorResolver}），而并列只可能来自
     * **同一个类型里两个槽位给同一字段同一个 ≥1 优先级**——这一条在加载期就能算出来，不必等玩家把两个槽
     * 都装满才发现"某个行为不生效"。同优先级 + 两边都声明了该字段才会真的并列，所以只报 WARN。
     * <p>
     * 这是给数据作者的诊断，与 {@link #warnOnTypeCycles} 同类。
     */
    private static void warnOnSlotPriorityTies() {
        for (Map.Entry<ResourceLocation, PartTypeDef> entry : typeMap().entrySet()) {
            List<PartTypeDef.SlotDef> slots = entry.getValue().slots();
            for (int i = 0; i < slots.size(); i++) {
                for (int j = i + 1; j < slots.size(); j++) {
                    for (BehaviorField field : BehaviorField.values()) {
                        int a = slots.get(i).priorityFor(field);
                        int b = slots.get(j).priorityFor(field);
                        if (a < 1 || a != b) continue;      // 没写（0）不参与竞争，不会并列
                        ColdWeaponCraftsmanship.LOGGER.warn(
                                "类型 {} 的槽位 {} 与 {} 在字段 {} 上给了同一个优先级 {}"
                                        + "——两者都装满且都声明该字段时，这个字段会**并列成无胜者**（仅提示，不阻止加载）",
                                entry.getKey(), slots.get(i).name(), slots.get(j).name(),
                                field.jsonName(), a);
                    }
                }
            }
        }
    }

    /**
     * 类型图环检测——**仅记 ERROR，不阻止加载**。
     * <p>
     * 属性聚合（{@link AssemblyTree}）与渲染都沿装配树递归，类型图上出现环意味着"理论上"可以无限嵌套。
     * 但递归遍历的是**物品树**而非类型图——玩家仍须逐级手工装配，深度实际由人力封顶，
     * 而且 {@link PartNode#MAX_DEPTH} 另有兜底，不构成崩溃风险。
     * 所以这是**给数据作者的诊断**：发现问题只报，数据照常注册。
     */
    private static void warnOnTypeCycles() {
        Map<ResourceLocation, PartTypeDef> types = typeMap();
        if (types.isEmpty()) return;

        // 建图：类型 → 它的槽位能接受的类型集合（判据与装配台的放入校验同源，都走 SlotDef.accepts）
        Map<ResourceLocation, Set<ResourceLocation>> edges = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, PartTypeDef> from : types.entrySet()) {
            Set<ResourceLocation> accepted = new LinkedHashSet<>();
            for (Map.Entry<ResourceLocation, PartTypeDef> candidate : types.entrySet()) {
                for (PartTypeDef.SlotDef slot : from.getValue().slots()) {
                    if (slot.accepts(candidate.getValue())) {
                        accepted.add(candidate.getKey());
                        break;
                    }
                }
            }
            edges.put(from.getKey(), accepted);
        }

        Set<ResourceLocation> done = new HashSet<>();
        Set<ResourceLocation> onStack = new HashSet<>();
        Deque<ResourceLocation> path = new ArrayDeque<>();
        for (ResourceLocation id : edges.keySet()) {
            findCycle(id, edges, done, onStack, path);
        }
    }

    /** 深度优先找环；发现回边就打印完整环路径 */
    private static void findCycle(ResourceLocation node, Map<ResourceLocation, Set<ResourceLocation>> edges,
                                  Set<ResourceLocation> done, Set<ResourceLocation> onStack,
                                  Deque<ResourceLocation> path) {
        if (done.contains(node)) return;
        if (onStack.contains(node)) {
            List<String> cycle = new ArrayList<>();
            boolean started = false;
            for (ResourceLocation s : path) {          // ArrayDeque 迭代顺序 = 入栈顺序
                if (s.equals(node)) started = true;
                if (started) cycle.add(s.toString());
            }
            cycle.add(node.toString());
            ColdWeaponCraftsmanship.LOGGER.error(
                    "类型图存在环，装配可无限嵌套：{}（仅提示，不阻止加载）", String.join(" -> ", cycle));
            return;
        }
        onStack.add(node);
        path.addLast(node);
        for (ResourceLocation next : edges.getOrDefault(node, Set.of())) {
            findCycle(next, edges, done, onStack, path);
        }
        path.removeLast();
        onStack.remove(node);
        done.add(node);
    }

    /** 解绑——由 {@code LevelEvent.Unload} 调用，避免关卡切换后残留上一个世界的引用 */
    public static void unbind() {
        typeRegistry = null;
        partRegistry = null;
    }

    // ──── 查询 ────

    /**
     * 按 id 取类型定义，查不到返回 null。
     * <p>
     * 接受 id 字符串而不是 {@code ResourceLocation}：调用方大多是从物品组件的
     * {@code PART_IDENTITY} 里拿到的字符串。非法 id（例如旧存档里的点号格式）返回 null 而不是抛异常。
     */
    public static PartTypeDef getTypeDef(String id) {
        return lookup(typeRegistry, id);
    }

    /** 按 id 取零件定义，查不到返回 null */
    public static PartDef getPartDef(String id) {
        return lookup(partRegistry, id);
    }

    /** 按 id 取类型定义——已持有 {@link ResourceLocation} 时用这个，免去字符串往返（零件的 {@code type} 字段就是） */
    public static PartTypeDef getTypeDef(ResourceLocation id) {
        return typeRegistry == null || id == null ? null : typeRegistry.get(id);
    }

    /** 按 id 取零件定义——已持有 {@link ResourceLocation} 时用这个 */
    public static PartDef getPartDef(ResourceLocation id) {
        return partRegistry == null || id == null ? null : partRegistry.get(id);
    }

    /** 全部零件定义（按 id 排序，确定性顺序） */
    public static Collection<PartDef> getAllParts() {
        return partMap().values();
    }

    /** 某类型下的全部零件（按 id 排序） */
    public static List<PartDef> getPartsByType(String typeId) {
        ResourceLocation type = parse(typeId);
        if (type == null) return List.of();
        return partMap().entrySet().stream()
                .filter(e -> type.equals(e.getValue().type()))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * 全部零件——**id 与定义都要的场合用这个**（零件没有 id 字段，id 是注册表键）。
     * 返回按 id 排序的不可变快照，顺序确定。
     */
    public static Map<ResourceLocation, PartDef> partMap() {
        if (partRegistry == null) return Map.of();
        // TreeMap 按 id 字符串排序 → 遍历顺序确定（注册表自身的顺序不保证跨版本一致）
        Map<ResourceLocation, PartDef> out = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        partRegistry.entrySet().forEach(e -> out.put(e.getKey().location(), e.getValue()));
        return Collections.unmodifiableMap(out);
    }

    /** 全部类型——id 与定义都要的场合用这个 */
    public static Map<ResourceLocation, PartTypeDef> typeMap() {
        if (typeRegistry == null) return Map.of();
        Map<ResourceLocation, PartTypeDef> out = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        typeRegistry.entrySet().forEach(e -> out.put(e.getKey().location(), e.getValue()));
        return Collections.unmodifiableMap(out);
    }

    /** 注册表是否已就绪（数据包加载完成）。供诊断与"能不能装配"这类判断用 */
    public static boolean isReady() {
        return partRegistry != null;
    }

    // ──── 内部 ────

    private static <T> T lookup(Registry<T> registry, String id) {
        if (registry == null || id == null || id.isEmpty()) return null;
        ResourceLocation key = parse(id);
        return key == null ? null : registry.get(key);
    }

    /** 字符串 → ResourceLocation，非法返回 null（旧存档的点号 id 会走到这里，按"查不到"处理） */
    private static ResourceLocation parse(String id) {
        if (id == null) return null;
        try {
            return ResourceLocation.parse(id);
        } catch (Exception e) {
            return null;
        }
    }
}
