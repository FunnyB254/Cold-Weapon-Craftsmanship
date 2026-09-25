package com.funnyb.cwc.client.renderer.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * HUD 注册表——hud id → 实现。**客户端专用**（实现要 {@code GuiGraphics}）。
 * <p>
 * <b>面向第三方模组的扩展点</b>：在客户端初始化阶段注册自己的 {@link BehaviorHud}，然后在零件类型的
 * JSON 里把 {@code hud} 指向它。形状与行为注册表（{@code BehaviorRegistry}）一致。
 * <p>
 * 与行为注册表的一处不对称：行为 id 在**加载期**校验（写错整条数据加载失败），HUD id 只在**绘制时**
 * 才查得出来——因为数据由服务端加载、HUD 注册在客户端，加载期两边不一定都在场。所以写错的 hud id
 * 会在客户端首次绘制时 WARN 一次（见 {@code CrosshairIndicators.renderHud}），不崩、也不刷屏。
 */
public final class BehaviorHudRegistry {

    private static final Map<String, BehaviorHud> HUDS = new HashMap<>();

    /** 内建 HUD 在客户端初始化时注册（与 {@code BehaviorRegistry} 的静态块同理，只是这边只影响显示） */
    static {
        register(new AttackIndicatorHud());
        register(new OffhandSwingHud());
    }

    private BehaviorHudRegistry() {}

    /** 注册一个 HUD。同名覆盖（第三方可替换内建实现）。建议 id 带自己的命名空间 */
    public static void register(BehaviorHud hud) {
        HUDS.put(hud.id(), hud);
    }

    /** 按 id 取实现；未注册返回 null */
    public static BehaviorHud get(String id) {
        return HUDS.get(id);
    }

    /** 已注册的全部 id——用于"未知 hud"的报错消息 */
    public static Set<String> registeredIds() {
        return Set.copyOf(HUDS.keySet());
    }
}
