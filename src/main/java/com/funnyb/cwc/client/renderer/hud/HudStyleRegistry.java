package com.funnyb.cwc.client.renderer.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * HUD 样式注册表——样式 id → 实现。**客户端专用**（实现要 {@code GuiGraphics}）。
 * <p>
 * <b>面向第三方模组的扩展点</b>：在客户端初始化阶段注册自己的 {@link HudStyle}，然后在零件类型的
 * JSON 里把 {@code hud} 指向它。形状与行为注册表（{@code BehaviorRegistry}）一致。
 * <p>
 * 与行为注册表的一处不对称：行为 id 在**加载期**校验（写错整条数据加载失败），样式 id 只在**绘制时**
 * 才查得出来——因为数据由服务端加载、样式实现注册在客户端，加载期两边不一定都在场。所以写错的样式 id
 * 会在客户端首次绘制时 WARN 一次（见 {@code CrosshairIndicators.renderHud}），不崩、也不刷屏。
 * <p>
 * 这是"样式 id 留在数据里"这个选择的**已知代价**（作者 2026-09-27 确认接受：换来的是一整套可替换的
 * 绘制方式，而不只是唯一一种画法）。
 */
public final class HudStyleRegistry {

    private static final Map<String, HudStyle> STYLES = new HashMap<>();

    /** 内建样式在客户端初始化时注册（与 {@code BehaviorRegistry} 的静态块同理，只是这边只影响显示） */
    static {
        register(new CrosshairBarHud());
    }

    private HudStyleRegistry() {}

    /** 注册一个样式。同名覆盖（第三方可替换内建实现）。建议 id 带自己的命名空间 */
    public static void register(HudStyle style) {
        STYLES.put(style.id(), style);
    }

    /** 按 id 取实现；未注册返回 null */
    public static HudStyle get(String id) {
        return STYLES.get(id);
    }

    /** 已注册的全部 id——用于"未知样式"的报错消息 */
    public static Set<String> registeredIds() {
        return Set.copyOf(STYLES.keySet());
    }
}
