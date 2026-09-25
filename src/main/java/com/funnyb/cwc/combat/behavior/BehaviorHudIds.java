package com.funnyb.cwc.combat.behavior;

/**
 * 内建 HUD id。
 * <p>
 * <b>为什么 id 在通用侧、实现在客户端侧</b>：hud id 是**数据契约的一部分**（JSON 里 {@code "hud"} 写的就是它，
 * 旧字段折算时也要用），所以它必须被通用侧的解析器引用；而"怎么画"要用 {@code GuiGraphics}，
 * 只能在客户端（实现见客户端的 {@code BehaviorHudRegistry}）。两边共享的只有这个字符串。
 * <p>
 * 与行为 id 的关系：行为 id 在 {@link BehaviorRegistry}，HUD id 在这里——**两笔独立注册**，
 * 唯一的联系是 JSON 里同一字段对象下并排写的两个键（见 {@code PartTypeDef.BehaviorDecl}）。
 */
public final class BehaviorHudIds {

    /**
     * 主手攻击指示器（准星下方那条）：冷却进度条 / 满格图标。
     * 挂在 {@code attack} 字段上——所有 CWC 武器都画（旧 {@code combat.style} 折算时带上）。
     */
    public static final String ATTACK_INDICATOR = "cwc:attack_indicator";

    /**
     * 副手出刀指示器（准星上方那条，主手那条的镜像）。挂在 {@code offHandUse} 字段上
     * ——旧 {@code offhandAttack: true} 折算时带上，所以旧数据不用改也照画。
     */
    public static final String OFFHAND_ATTACK = "cwc:offhand_attack";

    private BehaviorHudIds() {}
}
