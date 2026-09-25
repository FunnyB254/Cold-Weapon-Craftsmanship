package com.funnyb.cwc.combat.behavior;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 行为注册表——行为 id（如 {@code cwc:block_use}）→ 实现。
 * <p>
 * <b>面向第三方模组的扩展点</b>：用自己的命名空间注册一个 {@link WeaponBehavior}，然后在零件/槽位的
 * JSON 里写 {@code "behavior": "yourmod:foo"} 指过来。形状与 {@code ParserRegistry} 一致
 * （那边注册"数据形状"，这边注册"动作"）。
 * <p>
 * 同名覆盖，允许替换默认实现。**数据包永远不直接实例化 Java 对象**：JSON 只写 id，实现必须在代码侧
 * 注册过，否则 {@link com.funnyb.cwc.crafting.PartTypeDef.BehaviorDecl} 的 codec 会在**加载期**报错
 * （而不是等到运行时静默什么都不做）。
 */
public final class BehaviorRegistry {

    /** 内建行为 id——格挡（主手/副手右键保持）。旧字段 {@code twoHanded: true} 折算成它 */
    public static final String BLOCK = "cwc:block_use";
    /** 内建行为 id——出刀（副手右键瞬发）。旧字段 {@code offhandAttack: true} 折算成它 */
    public static final String SWING = "cwc:swing_use";
    /** 内建攻击方式 id——单体直击（{@code combat.style: "normal"} 折算成它） */
    public static final String STRIKE_ATTACK = "cwc:strike_attack";
    /** 内建攻击方式 id——横扫（{@code combat.style: "sweep"} 折算成它） */
    public static final String SWEEP_ATTACK = "cwc:sweep_attack";
    /** 内建攻击方式 id——跳劈暴击（{@code combat.style: "critical"} 折算成它） */
    public static final String CRITICAL_ATTACK = "cwc:critical_attack";

    private static final Map<String, WeaponBehavior> BEHAVIORS = new HashMap<>();

    /**
     * 内建行为在**本类初始化时**注册——顺序上早于任何数据包解析（行为 id 的合法性在解析期校验），
     * 位置与 {@code ParserRegistry} 的静态块一致。
     */
    static {
        register(new BlockBehavior());
        register(new SwingBehavior());
        register(new StrikeAttack());
        register(new SweepAttack());
        register(new CriticalAttack());
    }

    private BehaviorRegistry() {}

    /**
     * 注册一个行为实现。建议 id 带自己的命名空间。同名覆盖（第三方可替换内建实现）。
     * <p>
     * ⚠ 第三方注册必须发生在**任何数据包被解析之前**（模组构造器 / 模组总线事件里）。
     */
    public static void register(WeaponBehavior behavior) {
        BEHAVIORS.put(behavior.id(), behavior);
    }

    /** 按 id 取实现；未注册返回 null */
    public static WeaponBehavior get(String id) {
        return BEHAVIORS.get(id);
    }

    /** 该 id 是否已注册（{@code BehaviorDecl} 的 codec 用它做加载期校验） */
    public static boolean isRegistered(String id) {
        return BEHAVIORS.containsKey(id);
    }

    /** 已注册的全部 id——用于"未知行为"的报错消息 */
    public static Set<String> registeredIds() {
        return Set.copyOf(BEHAVIORS.keySet());
    }

    /** 已注册的、且能挂在某个字段上的全部 id——用于"这个行为不能用在 X 字段上"的报错消息 */
    public static Set<String> registeredIds(BehaviorField field) {
        Set<String> ids = new HashSet<>();
        for (WeaponBehavior behavior : BEHAVIORS.values()) {
            if (behavior.fields().contains(field)) ids.add(behavior.id());
        }
        return ids;
    }

    /**
     * 行为的**显示名**语言键——{@code behavior.<命名空间>.<路径以 . 连接>}，与零件/类型的显示名同一条约定
     * （见 {@code PartDef.langKey}）。
     * <p>
     * 没写语言的 id 由调用方回落成显示 id 本身（见 {@code CwcWeapon.behaviorName}），所以第三方只注册行为、
     * 不提供语言也能用，只是 tooltip 上显示的是那个 id。
     */
    public static String langKey(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return "behavior." + id;      // id 不合法时给个可读的兜底，别让 tooltip 炸
        return "behavior." + key.getNamespace() + "." + key.getPath().replace('/', '.');
    }
}
