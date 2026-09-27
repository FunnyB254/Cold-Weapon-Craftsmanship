package com.funnyb.cwc.client.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 攻击指示器那条（进度条 / 满格图标）的**几何与画法——唯一一份**。
 * <p>
 * 主手与副手两条的差别只有一个字："镜像"——而且是**两层**镜像（作者 2026-09-27 定的第二层）：
 * <ul>
 *   <li><b>位置</b>：主手那条在准星<b>下方</b>（照原版 {@code renderCrosshair} 的口径），副手那条取它关于
 *       <b>准星精灵中心行</b>的镜像（{@link #mirroredBarY}），于是两边在"未满"和"满"两个状态下到准星的
 *       距离都一致；</li>
 *   <li><b>画面</b>：副手那条的 sprite 自身**上下 + 左右都翻**（见 {@link #blit}）——上下是为了让可见内容
 *       贴着准星（理由见下），左右是为了让它成为主手那条真正的镜像：这三张 sprite 画的是一条**横躺的小剑**，
 *       列 0–3 是剑柄、列 4–15 是充能填的剑身，所以左右一翻，剑柄就到右边、充能从右往左长。</li>
 * </ul>
 * <p>
 * <b>"sprite 要翻转再画"的来历</b>（这段是调了四轮才对上的，别简化）：
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
 * 逐像素倒序只动 UV：几何与绕序都不变，也不需要任何 GL 状态（代价是采样次数 = 宽 × 高，见 {@link #blit}）。
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
     * @param mirrored  true = 画在准星上方且**整条左右镜像**（副手那条）：sprite 上下 + 左右都翻、
     *                  图标与条底对齐。见 {@link #blit} 对"镜像"的定义
     */
    public static void render(GuiGraphics gui, float readiness, boolean hasTarget, boolean mirrored) {
        int x = gui.guiWidth() / 2 - 8;      // 16 宽居中于准星
        if (readiness >= 1.0F) {
            if (hasTarget) {
                blit(gui, FULL, x, mirrored ? mirroredIconY(gui.guiHeight()) : belowY(gui.guiHeight()),
                        ICON_SIZE, ICON_SIZE, ICON_SIZE, mirrored);
            }
        } else {
            int barY = mirrored ? mirroredBarY(gui.guiHeight()) : belowY(gui.guiHeight());
            blit(gui, BACKGROUND, x, barY, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, mirrored);
            blit(gui, PROGRESS, x, barY, BAR_WIDTH, BAR_HEIGHT,
                    (int) (readiness * PROGRESS_WIDTH_BASE), mirrored);
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
     * 1:1 画一张 sprite——可能只画它左端的一段（进度条按就绪度截取）。
     *
     * @param width  盒子宽（也是 sprite 自身的宽，三张都是 16）
     * @param shown  实际画多宽：背景/图标是整张，进度条是就绪度那一截
     * @param flip   true = 副手那条：**整条关于盒子的竖直中线镜像**（上下 + 左右都翻）。
     *               <p>
     *               镜像是**按盒子算**的，不是"把 sprite 自己的 UV 翻一遍"：盒子第 c 列显示源列
     *               {@code width - 1 - c}。于是源列 {@code [0, shown)} 会落到盒子的**右端**
     *               {@code [width - shown, width)} —— 这正是"左右翻转"该有的样子（剑柄换到右边、
     *               充能从右往左长）；若只在原位置翻 UV，充能会从**剑尖**那一端长出来，那是错的。
     *               <p>
     *               代价是逐像素采样：满格图标 16×16 = 256 次 quad、条 16×4 = 64 次。
     *               仅在副手那条可见时发生，且这里没有更便宜的做法（见类注释里被否掉的三条路）。
     */
    private static void blit(GuiGraphics gui, ResourceLocation sprite, int x, int y,
                             int width, int height, int shown, boolean flip) {
        if (!flip) {
            // ⚠ 必须用**九参**那个重载。`GuiGraphics` 两个重载语义不同：
            // 五参 `(sprite, x, y, width, height)` 把**整张** sprite 的 UV 铺满目标矩形，也就是
            // **拉伸**——`shown` 不足 16 时整条被压进 `shown` 像素里（"像在拉伸"就是这么来的）；
            // 九参 `(sprite, tw, th, u, v, x, y, uw, vh)` 取的是 `[u, u+uw)` 那一段 UV，**裁切**。
            // 原版那条进度条用的就是九参（`Gui.java:475`），所以这里必须跟着。
            // 夹取是防御：这一支今天算不出 17（调用方只在 `readiness < 1.0` 时算 `shown`），
            // 但 UV 一旦超过 1 会采到图集里隔壁的 sprite；镜像那支的 `Math.max(0, width - shown)` 同理。
            gui.blitSprite(sprite, width, height, 0, 0, x, y, Math.min(shown, width), height);
            return;
        }
        // 逐像素倒序采样：UV 同时按 u→width-1-u、v→height-1-v 映射，几何与绕序都不变。
        // 下界做了夹取：shown 可能是 17（PROGRESS_WIDTH_BASE 那个故意的溢出），此时整条画满即可。
        for (int col = Math.max(0, width - shown); col < width; col++) {
            int u = width - 1 - col;
            for (int row = 0; row < height; row++) {
                gui.blitSprite(sprite, width, height, u, height - 1 - row, x + col, y + row, 1, 1);
            }
        }
    }
}
