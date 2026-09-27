package com.funnyb.cwc.client.renderer.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 一个 HUD **样式**——把两个数画出来。注册在 {@link HudStyleRegistry}，由数据里的 {@code hud} 键选中。
 *
 * <h2>它只吃结论，不吃原料</h2>
 * 入参只有**进度**与**高亮**两个数（外加"画在哪一侧"）。这两个数由这只手这个字段胜出的**行为**给出
 * （{@code WeaponBehavior.hudReadout}）——样式不自己去解析物品、不自己判定有没有目标。
 * <p>
 * 这样做是有来历的：早先的接口把"这只手上的物品栈、胜出的声明、胜出的行为、准星目标"一股脑交给 HUD，
 * 结果两个内置实现里有一半参数从来没人读，而它们都绕回去调一遍 {@code CwcCombat} **重新解析**
 * 才知道胜负——外面分派层明明已经解析过一次了。现在样式拿到的就是结论。
 *
 * <h2>扩展点</h2>
 * 想换一种画法（环形进度、准星变色……）：实现本接口、在客户端初始化时注册，
 * 然后把数据里的 {@code hud} 指向它。写法与行为注册表一致。
 * <p>
 * 实现必须是**无状态单例**：一帧可能画两条（主手一条、副手一条），状态留在实例里会互相污染。
 */
public interface HudStyle {

    /** 样式 id（注册键；JSON 里 {@code hud} 字段就是它）。内建的见 {@code HudStyleIds} */
    String id();

    /**
     * 画一条。反色混合由调用方成对开关（见 {@code CrosshairBar}），实现只管自己的几何。
     *
     * @param progress  进度 0~1（1 = 可以出手了）。**0 不等于"不显示"**——那是一条空进度条；
     *                  要整个不画，由行为返回空读数（见 {@code WeaponBehavior#hudReadout}）
     * @param highlight 高亮：此刻打不打得到
     * @param mirrored  true = 画在准星**上方**（副手那条）；false = 准星下方（主手那条）
     */
    void render(GuiGraphics gui, float progress, boolean highlight, boolean mirrored);
}
