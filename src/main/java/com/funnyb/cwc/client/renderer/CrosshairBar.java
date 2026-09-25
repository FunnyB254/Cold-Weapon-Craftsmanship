package com.funnyb.cwc.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 攻击指示器那条（进度条 / 满格图标）的**几何与画法——唯一一份**。
 * <p>
 * 主手与副手两条的差别只有一个字："镜像"。主手那条在准星**下方**（照原版 {@code renderCrosshair} 的口径），
 * 副手那条取它关于**准星精灵中心行**的镜像（{@link #mirroredBarY}），于是两边在"未满"和"满"两个状态下
 * 到准星的距离都一致。
 * <p>
 * <b>"三张 sprite 要垂直翻转再画"的来历</b>（这段是调了四轮才对上的，别简化）：
 * 原版这三张 sprite 是给"准星<b>下方</b>"设计的，而且**可见内容在自己的贴图框里并不居中**——
 * 16×4 的条内容在第 1–2 行（第 0/3 行只有 x=3 一个像素，近似居中），16×16 的满格图标内容只占第 0–6 行、
 * 下面 9 行**全透明**（顶对齐）。副手那条要画在准星<b>上方</b>：不翻的话条的位置近似没问题，但满格图标的
 * 可见内容会浮在离准星 11 行处——按框算出的"2 行"对不上看得见的图，表现为"图标高出一截"。
 * 翻过来之后内容落到底部，两个状态的**可见部分**才都和主手一样离准星 2 行。
 * <p>
 * <b>为什么不换别的办法</b>（三条都核过源码）：
 * <ul>
 *   <li>{@code pose().scale(1,-1,1)} 会反转绕序 → 背面剔除，而 HUD 渲染期间 cull 是**开着**的
 *       （{@code Minecraft} 每帧 {@code RenderSystem.enableCull()}；{@code RenderType.GUI} 不动剔除状态，
 *       且 sprite 路径不经 {@code RenderType}，是 {@code BufferUploader.drawWithShader} 直接用环境 GL 状态）
 *       → 得额外 bracket 全局 GL 状态；</li>
 *   <li>负 {@code vHeight}：{@code blitSprite} 内部让 vHeight **同时进几何**（{@code y + vHeight}）
 *       → 图会被画到框上方，绕序同样反转；</li>
 *   <li>自备翻转贴图：要往 jar 里塞派生自原版的 png。</li>
 * </ul>
 * 逐行倒序只动 UV：几何与绕序都不变，也不需要任何 GL 状态。
 * <p>
 * <b>反色混合由调用方成对开关</b>（{@link #enableInvertBlend} / {@link #disableInvertBlend}），
 * {@link #render} 自己不碰 GL 状态——准星本体与两条指示器用的是同一档混合，一次 bracket 就够。
 */
public final class CrosshairBar {

    /** 原版三张指示器 sprite——注意：名字里的 "crosshair_attack_indicator" 是原版给**主手**那条起的 */
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final ResourceLocation PROGRESS =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
    private static final ResourceLocation FULL =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_full");

    /** 条的尺寸（sprite 自身也是这个尺寸，1:1 画） */
    private static final int BAR_WIDTH = 16;
    private static final int BAR_HEIGHT = 4;
    /** 满格图标的边长 */
    private static final int ICON_SIZE = 16;
    /** 进度条宽度基准：原版按 {@code scale×17} 画（17 而非 16——满格时正好盖满并溢出 1 像素，与主手一致） */
    private static final float PROGRESS_WIDTH_BASE = 17.0F;
    /** 满格图标 16 高、条 4 高 → 副手那条要让图标与条**底边**对齐，故图标顶 = 条顶 − 12 */
    private static final int ICON_ABOVE_BAR = ICON_SIZE - BAR_HEIGHT;

    private CrosshairBar() {}

    /**
     * 画一条指示器——**"满格就看有没有目标、未满就画进度条"这条规则只有这一份**。
     *
     * @param readiness 就绪度 0~1（1 = 可以出手）；主手用攻速条，副手用出刀自己的独立冷却
     * @param hasTarget 是否存在可命中的目标（横扫 = 范围内有东西，单体 = 准星目标可命中）
     * @param mirrored  true = 画在准星上方（副手），sprite 垂直翻转、图标与条底对齐
     */
    public static void render(GuiGraphics gui, float readiness, boolean hasTarget, boolean mirrored) {
        int x = gui.guiWidth() / 2 - 8;      // 16 宽居中于准星
        if (readiness >= 1.0F) {
            if (hasTarget) {
                blit(gui, FULL, x, mirrored ? mirroredIconY(gui.guiHeight()) : belowY(gui.guiHeight()),
                        ICON_SIZE, ICON_SIZE, mirrored);
            }
        } else {
            int barY = mirrored ? mirroredBarY(gui.guiHeight()) : belowY(gui.guiHeight());
            blit(gui, BACKGROUND, x, barY, BAR_WIDTH, BAR_HEIGHT, mirrored);
            blit(gui, PROGRESS, x, barY, (int) (readiness * PROGRESS_WIDTH_BASE), BAR_HEIGHT, mirrored);
        }
    }

    // —— 几何 ——

    /**
     * 主手那条的顶边 y——照原版 {@code renderCrosshair}（{@code guiHeight / 2 + 9}）：条与满格图标**同顶**。
     */
    public static int belowY(int guiHeight) {
        return guiHeight / 2 + 9;
    }

    /**
     * 副手那条的顶边 y = 主手条关于**准星精灵中心行**的镜像。
     * <p>
     * 镜像一个 4 行的区段：新区段的**顶**行对应原区段的**底**行，所以
     * {@code 新顶 = 2×中心 − 原底 = 2×中心 − (belowY + 3)}。
     * <p>
     * 镜子要用精灵**自身**的中心行（精灵 15 高、顶行 {@code (h-15)/2} → 中心行 {@code (h-15)/2 + 7}）：
     * 精灵并不以 {@code h/2} 为对称中心，用 {@code h/2} 会差一行。
     */
    public static int mirroredBarY(int guiHeight) {
        int crosshairCenterRow = (guiHeight - 15) / 2 + 7;
        return 2 * crosshairCenterRow - (belowY(guiHeight) + BAR_HEIGHT - 1);
    }

    /** 副手满格图标的顶边：与条**底边**对齐（见 {@link #ICON_ABOVE_BAR} 的说明） */
    public static int mirroredIconY(int guiHeight) {
        return mirroredBarY(guiHeight) - ICON_ABOVE_BAR;
    }

    // —— 混合 ——

    /** 反色混合——准星本体与两条指示器共用这一档，由调用方成对开关 */
    public static void enableInvertBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
    }

    public static void disableInvertBlend() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // —— 画 ——

    /**
     * 1:1 画一张 sprite。
     *
     * @param flipped true = 垂直翻转（逐行倒序采样贴图，见类注释）
     */
    private static void blit(GuiGraphics gui, ResourceLocation sprite, int x, int y,
                             int width, int height, boolean flipped) {
        if (!flipped) {
            gui.blitSprite(sprite, x, y, width, height);
            return;
        }
        for (int row = 0; row < height; row++) {
            gui.blitSprite(sprite, width, height, 0, height - 1 - row, x, y + row, width, 1);
        }
    }
}
