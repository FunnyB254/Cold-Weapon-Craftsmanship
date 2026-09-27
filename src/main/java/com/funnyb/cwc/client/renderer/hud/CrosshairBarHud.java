package com.funnyb.cwc.client.renderer.hud;

import com.funnyb.cwc.client.renderer.CrosshairBar;
import com.funnyb.cwc.combat.behavior.HudStyleIds;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 准星条（{@code cwc:crosshair_bar}）——复刻原版攻击指示器：**未就绪画进度条、就绪且有目标画满格图标**，
 * 两个状态都没目标时什么都不画。
 * <p>
 * 几何只有一份，在 {@link CrosshairBar} 里；本类只是把两个数递过去。主手那条在准星下方、副手那条是它的
 * 镜像（{@link CrosshairBar#mirroredBarY}），所以一个实现同时服务两条——**"画在哪"由手决定**，
 * 与样式无关。
 * <p>
 * <b>刻意不做的事：不读原版 {@code attackIndicator} 设置。</b>那条设置只管原版攻击指示器（即主手那条），
 * 属于**分派层的策略**，放在这里会把副手那条一并挡掉——那是行为变化。见 {@code CrosshairIndicators.renderHud}。
 * <p>
 * 早先这里是两个类（{@code AttackIndicatorHud} / {@code OffhandSwingHud}），差别只有镜像与一处
 * "就绪才亮"的判断；而后者与 {@link CrosshairBar#render} 的分支**重合**（未就绪时走的是进度条分支，
 * 根本不读高亮），所以合并成一个样式后像素完全一致。
 */
public final class CrosshairBarHud implements HudStyle {

    @Override
    public String id() {
        return HudStyleIds.CROSSHAIR_BAR;
    }

    @Override
    public void render(GuiGraphics gui, float progress, boolean highlight, boolean mirrored) {
        CrosshairBar.render(gui, progress, highlight, mirrored);
    }
}
