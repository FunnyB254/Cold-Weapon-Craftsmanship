package com.funnyb.cwc.combat.behavior;

/**
 * 内建 HUD **样式** id。
 * <p>
 * <b>为什么 id 在通用侧、实现在客户端侧</b>：hud id 是**数据契约的一部分**（JSON 里 {@code "hud"} 写的就是它），
 * 所以它必须被通用侧的解析器引用；而"怎么画"要用 {@code GuiGraphics}，只能在客户端
 * （实现见客户端的 {@code HudStyleRegistry}）。两边共享的只有这个字符串。
 *
 * <h2>样式 ≠ 读数</h2>
 * 一个 HUD 由两半拼成（作者 2026-09-27 定）：
 * <ul>
 *   <li><b>读数</b>（进度 + 高亮）由**行为**给——它是这只手这个字段的逻辑半自己的结论，
 *       见 {@link WeaponBehavior#hudReadout}；</li>
 *   <li><b>样式</b>（把这两个数画成什么样）由数据里的 {@code hud} 键选，就是本类的 id。</li>
 * </ul>
 * 所以样式**不含来源**：同一个样式挂在哪个字段上都说得通，真正决定"显示什么"的是行为。
 * 这也意味着"挂错字段"不再是需要校验的语义错误——没有可校验错的东西。
 */
public final class HudStyleIds {

    /**
     * 准星条——原版攻击指示器那种：未就绪画进度条、就绪且有目标画满格图标。
     * <p>
     * 主手那条画在准星下方、副手那条是它的镜像（画在准星上方）——**"画在哪"由手决定，
     * 不由样式决定**，所以两条共用一个 id。今天这是唯一的样式。
     */
    public static final String CROSSHAIR_BAR = "cwc:crosshair_bar";

    private HudStyleIds() {}
}
