package com.funnyb.cwc.client.renderer.hud;

import com.funnyb.cwc.client.renderer.CrosshairBar;
import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.combat.behavior.BehaviorHudIds;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;

/**
 * 攻击指示器（{@code cwc:attack_indicator}）——"就绪但打不到"与"打得到"的那条：
 * 未就绪 → 进度条；就绪 + 存在可攻击目标 → 满格图标；就绪 + 无目标 → 空。
 * <p>
 * 挂在 {@code attack} 字段上，读的是**这件武器自己的攻击方式**（{@link CwcCombat#hasAnyAttackableTarget}）：
 * 横扫 = 攻击范围内有可攻击实体（不要求准星对准）；单体 = 准星目标可命中。旧数据由
 * {@code combat.style} 折算时自动带上本 HUD（见 {@code BehaviorResolver} 的别名）。
 * <p>
 * <b>只在原版攻击指示器设为 CROSSHAIR 时画</b>：HOTBAR 走原版快捷栏那个指示器（hotbar 层不受本模组接管）、
 * OFF 就什么都不显示——这条一直如此，不是本次改动引入的。
 * <p>
 * 位置由**哪只手**决定（主手在下、副手镜像到上），见 {@link CrosshairBar#render}。
 */
public final class AttackIndicatorHud implements BehaviorHud {

    @Override
    public String id() {
        return BehaviorHudIds.ATTACK_INDICATOR;
    }

    @Override
    public void render(GuiGraphics gui, HudContext ctx) {
        if (Minecraft.getInstance().options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) return;
        float scale = ctx.player().getAttackStrengthScale(0.0F);
        boolean hasTarget = CwcCombat.hasAnyAttackableTarget(
                ctx.player(), ctx.stack(), ctx.hand(), ctx.crosshairTarget());
        CrosshairBar.render(gui, scale, hasTarget, ctx.hand() == InteractionHand.OFF_HAND);
    }
}
