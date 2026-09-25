package com.funnyb.cwc.combat.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 行为注册表——行为 id（如 {@code cwc:block}）→ 实现。
 * <p>
 * <b>面向第三方模组的扩展点</b>：用自己的命名空间注册一个 {@link WeaponBehavior}，然后在零件/槽位的
 * JSON 里写 {@code "behavior": "yourmod:foo"} 指过来。形状与 {@code ParserRegistry} 一致
 * （那边注册"数据形状"，这边注册"动作"）。
 * <p>
 * 同名覆盖，允许替换默认实现。**数据包永远不直接实例化 Java 对象**：JSON 只写 id，实现必须在代码侧
 * 注册过，否则 {@link BehaviorDecl} 的 codec 会在**加载期**报错（而不是等到运行时静默什么都不做）。
 */
public final class BehaviorRegistry {

    /** 内建行为 id——格挡（双手武器主手右键）。实现在迁移那一步注册（旧的 {@code twoHanded: true} 折算成它） */
    public static final String BLOCK = "cwc:block_use";
    /** 内建行为 id——副手出刀（短刀）。实现在迁移那一步注册（旧的 {@code offhandAttack: true} 折算成它） */
    public static final String SWING = "cwc:swing_use";

    private static final Map<String, WeaponBehavior> BEHAVIORS = new HashMap<>();

    private BehaviorRegistry() {}

    /**
     * 注册一个行为实现。建议 id 带自己的命名空间。
     * <p>
     * ⚠ 加载顺序：注册必须发生在**任何数据包被解析之前**（行为 id 的合法性在解析期校验）。
     * 现有的三个 parser 是在本类的同类 {@code ParserRegistry} 的静态块里注册的，照那个位置放。
     */
    public static void register(WeaponBehavior behavior) {
        BEHAVIORS.put(behavior.id(), behavior);
    }

    /** 按 id 取实现；未注册返回 null */
    public static WeaponBehavior get(String id) {
        return BEHAVIORS.get(id);
    }

    /** 该 id 是否已注册（{@link BehaviorDecl} 的 codec 用它做加载期校验） */
    public static boolean isRegistered(String id) {
        return BEHAVIORS.containsKey(id);
    }

    /** 已注册的全部 id——用于"未知行为"的报错消息 */
    public static Set<String> registeredIds() {
        return Set.copyOf(BEHAVIORS.keySet());
    }
}
