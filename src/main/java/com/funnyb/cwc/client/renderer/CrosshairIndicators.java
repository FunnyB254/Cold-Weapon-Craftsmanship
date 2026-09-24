package com.funnyb.cwc.client.renderer;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.registry.CwcItems;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 准星层接管与 CWC 攻击指示器。
 * <p>
 * <b>{@link #onCrosshairPre} 与 {@link #onCrosshairPost} 是一对互补物，必须同处一个类</b>：
 * Pre 在主手**是** CWC 武器时取消原版 crosshair 层（原版准星 + 攻击指示器）并自画；
 * Post 只在主手**非** CWC 时补画副手短刀指示器。两者各自判一次主手是不是 CWC，所以只搬走一个的后果是
 * 副手指示器**画两次**（同一位置两次反色 = 看着不对）或**完全消失**。
 * <p>
 * 本类**不读**任何跨 tick 状态（模式锁 / 主手武器身份 / 副手挂起旗子），只读 {@code mc.hitResult}、
 * {@code mc.options} 与 {@link CwcCombat} 的查询——它与 {@code CwcClientEvents} 的 tick 相位耦合为零。
 * <p>
 * （自 {@code CwcClientEvents} 抽出时是纯搬运；那边的 tick 相位说明见 {@code CwcClientEvents} 的类文档。）
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
public class CrosshairIndicators {

    /** 准星本体精灵（复刻原版 renderCrosshair 居中 15×15） */
    private static final ResourceLocation CROSSHAIR_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/crosshair");
    private static final ResourceLocation OFFHAND_INDICATOR_BACKGROUND =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final ResourceLocation OFFHAND_INDICATOR_PROGRESS =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
    private static final ResourceLocation OFFHAND_INDICATOR_FULL =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_full");

    /**
     * crosshair 层接管——主手 CWC 武器时取消原版层（原版准星 + 攻击指示器），自画准星 + CWC 指示器。
     * <p>消除两次反色：CWC 武器 delay>5，原版攻击指示器在准星对准活体时也会画满格（反色），与 CWC 补画
     * 同一区域两次反色 = 还原消失。接管后原版不再画，CWC 全权绘制（一次反色）。
     * F3 debug 模式放行原版：原版 debug 分支只画 3D 准星、不画攻击指示器，无两次反色。</p>
     */
    @SubscribeEvent
    public static void onCrosshairPre(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;    // 只第一人称（与准星一致）
        if (player.isSpectator()) return;                            // 旁观者走原版逻辑
        ItemStack main = player.getMainHandItem();
        if (main.getItem() != CwcItems.HANDLE_PART.get()) return;    // 只接管 CWC 主手

        // F3 debug 3D 准星放行原版（debug 分支只画 3D 准星、不画攻击指示器，无两次反色）
        if (mc.getDebugOverlay().showDebugScreen()
                && !player.isReducedDebugInfo() && !mc.options.reducedDebugInfo().get()) {
            return;
        }

        event.setCanceled(true);
        GuiGraphics gui = event.getGuiGraphics();
        // 反色混合：准星本体 + 主手攻击指示器（复刻原版 renderCrosshair 样式）
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        // 准星本体：居中 15×15
        gui.blitSprite(CROSSHAIR_SPRITE, (gui.guiWidth() - 15) / 2, (gui.guiHeight() - 15) / 2, 15, 15);
        // 主手 CWC 攻击指示器：仅 CROSSHAIR 模式画准星下方；HOTBAR 走原版快捷栏指示器（hotbar 层不受影响）、OFF 不显示
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
            renderMainHandIndicator(gui, player, main);
        }
        RenderSystem.defaultBlendFunc();
        // 副手短刀指示器（内部自管反色混合）
        renderOffhandIndicator(gui, player);
        RenderSystem.disableBlend();
    }

    /**
     * 主手 CWC 攻击指示器（准星下方，反色混合由调用方保证）：
     * 攻速未满 → 进度条；攻速满 + 存在可攻击目标 → 满格图标；攻速满 + 无目标 → 空。
     * "存在可攻击目标"：横扫 = 攻击范围内有可攻击实体（不要求准星对准）；单体（短刀/斧）= 准星目标可命中。
     */
    private static void renderMainHandIndicator(GuiGraphics gui, Player player, ItemStack main) {
        Minecraft mc = Minecraft.getInstance();
        float scale = player.getAttackStrengthScale(0.0F);
        int x = gui.guiWidth() / 2 - 8;
        int y = gui.guiHeight() / 2 + 9;
        if (scale >= 1.0F) {
            if (CwcCombat.hasAnyAttackableTarget(player, main, InteractionHand.MAIN_HAND,
                    mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null)) {
                gui.blitSprite(OFFHAND_INDICATOR_FULL, x, y, 16, 16);
            }
        } else {
            gui.blitSprite(OFFHAND_INDICATOR_BACKGROUND, x, y, 16, 4);
            gui.blitSprite(OFFHAND_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, (int) (scale * 17.0F), 4);
        }
    }

    /**
     * 副手短刀攻击指示器（准星上方，内部自管反色混合）：短刀冷却就绪 + 存在可命中目标 → 满格图标；
     * 冷却中即使有目标也只显示就绪度进度条。主手非 CWC 时由 {@link #onCrosshairPost} 调用，
     * 主手 CWC 时由 {@link #onCrosshairPre} 接管调用。
     */
    private static void renderOffhandIndicator(GuiGraphics gui, Player player) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack off = player.getOffhandItem();
        if (!CwcWeapon.isOffhandKnife(off)) return;
        if (CwcWeapon.isTwoHandedStack(player.getMainHandItem())) return; // 双手武器占用右键
        // 就绪度 = 1 - 副手独立冷却占比（1 可攻击，0 刚出刀）；partial tick 固定 0（与原版 getAttackStrengthScale(0.0F) 一致）
        float ready = CwcCombat.offhandReadiness(player);
        int x = gui.guiWidth() / 2 - 8;      // 16 宽居中于准星
        // 位置与主手那条成**镜像**：主手取"准星下方 9px 为顶点、就绪时往下长"，
        // 副手取"到准星距离相同、就绪时往上长"——于是两者离准星一样近。
        // 镜面用准星精灵**自身**的中心行（精灵 15 高、顶行 (h-15)/2 → 中心行 (h-15)/2 + 7；
        // 不用 h/2，因为精灵并未以 h/2 为对称中心，差 1px）。
        // 故副手满格图标的顶 = 2×中心行 − (主手图标顶 h/2+9) − 15（镜像一个 16 高的区间）。
        final int crosshairCenterRow = (gui.guiHeight() - 15) / 2 + 7;
        int y = 2 * crosshairCenterRow - (gui.guiHeight() / 2 + 9) - 15;
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        // 满格图标（可攻击提示）：冷却就绪 && 存在可攻击目标（不要求准星对准横扫，单体看准星）
        boolean canHit = ready >= 1.0F && CwcCombat.hasAnyAttackableTarget(player, off, InteractionHand.OFF_HAND,
                mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null);
        if (canHit) {
            gui.blitSprite(OFFHAND_INDICATOR_FULL, x, y, 16, 16);
        } else if (ready < 1.0F) {
            // 冷却条(16×4)与满格图标**底边对齐**（图标 16 高、条 4 高 → y+12）：
            // 就绪时图标是从这条条**往上**长，与主手"往下长"成镜像
            gui.blitSprite(OFFHAND_INDICATOR_BACKGROUND, x, y + 12, 16, 4);
            gui.blitSprite(OFFHAND_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y + 12, (int) (ready * 17.0F), 4);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * 攻击指示器 Post 兜底——主手**非 CWC** 时的副手短刀指示器（主手 CWC 时由 {@link #onCrosshairPre}
     * 接管，Post 不触发）。**冷却就绪且存在可攻击目标 → 提示可攻击**。
     */
    @SubscribeEvent
    public static void onCrosshairPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;    // 只第一人称显示（与准星一致）
        if (player.isSpectator()) return;                            // 旁观者不攻击
        if (player.getMainHandItem().getItem() == CwcItems.HANDLE_PART.get()) return; // 主手 CWC 由 Pre 接管
        renderOffhandIndicator(event.getGuiGraphics(), player);
    }

}
