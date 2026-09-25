package com.funnyb.cwc.client.renderer.hud;

import com.funnyb.cwc.client.renderer.CrosshairBar;
import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.combat.behavior.BehaviorHudIds;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 副手出刀指示器（{@code cwc:offhand_attack}）——准星**上方**那条（主手那条的镜像，见 {@link CrosshairBar}）：
 * 就绪度就绪 + 存在可命中目标 → 满格图标；冷却中即使有目标也只显示就绪度进度条。
 * <p>
 * 挂在 {@code offHandUse} 字段上（旧 {@code offhandAttack: true} 折算时自动带上），就绪度读的是
 * <b>出刀自己的独立冷却</b>（{@link CwcCombat#offhandReadiness}），与主手完全隔离。
 * <p>
 * "有没有目标"问的是**副手这把刀自己的 {@code attack} 字段**（{@link CwcCombat#hasAnyAttackableTarget}）——
 * "在哪只手就按那只手读"这条规则在这里也成立，所以副手拿短刃时它是单体判定、拿横扫刃时是范围判定。
 * <p>
 * 副手的 {@code attack} 字段**不单独画一条**：它的结论已经并进本 HUD（满格图标）。两条画在同一位置会
 * 反色两次 = 看着什么都没有，见 {@code CrosshairIndicators} 对"只画哪两对"的说明。
 */
public final class OffhandSwingHud implements BehaviorHud {

    @Override
    public String id() {
        return BehaviorHudIds.OFFHAND_ATTACK;
    }

    @Override
    public void render(GuiGraphics gui, HudContext ctx) {
        // 就绪度 = 1 − 副手独立冷却占比（1 可出手，0 刚出刀）；partial tick 固定 0（与原版攻速条同口径）
        float readiness = CwcCombat.offhandReadiness(ctx.player());
        boolean canHit = readiness >= 1.0F && CwcCombat.hasAnyAttackableTarget(
                ctx.player(), ctx.stack(), ctx.hand(), ctx.crosshairTarget());
        CrosshairBar.render(gui, readiness, canHit, true);
    }
}
