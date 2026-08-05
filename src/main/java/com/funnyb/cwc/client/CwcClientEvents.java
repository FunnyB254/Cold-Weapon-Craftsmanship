package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.client.renderer.AssembledWeaponRenderer;
import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.crafting.PartTypeDef;
import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.network.serverbound.CwcMainHandAttackPacket;
import com.funnyb.cwc.network.serverbound.CwcOffhandAttackPacket;
import com.funnyb.cwc.registry.CwcItems;
import com.funnyb.cwc.screen.CwcScreen;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * CWC 客户端事件处理器。
 * 负责三件事：
 * 1. CWC 界面打开时隐藏原版 HUD 和手持物品
 * 2. 资源重载时刷新 Layouts 缓存和 PartRegistry
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, value = Dist.CLIENT)
public class CwcClientEvents {

    /** 若当前屏幕是 CWC 界面，取消手持物品的渲染 */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (Minecraft.getInstance().screen instanceof CwcScreen) {
            event.setCanceled(true);
        }
    }

    /** 若当前屏幕是 CWC 界面，取消 HUD（血量、饥饿度、快捷栏等）的渲染 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof CwcScreen) {
            event.setCanceled(true);
        }
    }

    /**
     * 资源重载（F3+T）后刷新 Layouts 缓存、清空武器合成缓存（含顶点网格与动态纹理）。
     * 注意：此事件在 game bus（NeoForge.EVENT_BUS）触发，订阅类不能用 Bus.MOD。
     * PartRegistry 数据由服务端 datapack 加载（{@link ColdWeaponCraftsmanship} 服务端事件），
     * 客户端资源管理器不含 data/，重载会把共享注册表清空（F3+T 后制造界面列表变空），故不在此重载。
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            Layouts.invalidate();
            AssembledWeaponRenderer.clearCache();
        });
    }

    /**
     * 主手 CWC 武器普攻——输入层拦截（客户端主拦截）。
     * 主手持本模组武器（HANDLE_PART）时一律取消 vanilla 攻击，改走自定义包
     * {@link CwcMainHandAttackPacket}：服务端按刃型普攻方式（AttackStyle）权威结算
     * （normal/critical 单目标、sweep 范围全额，空挥也结算范围）。
     * 冷却未满只取消不发包（强制等冷却）；冷却满点选目标发包。非本模组武器放行 vanilla。
     * 服务器端 AttackEntityEvent 兜底见 {@link com.funnyb.cwc.combat.CwcCombatEvents}。
     */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getKeyMapping() != Minecraft.getInstance().options.keyAttack) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return;  // 非 CWC 主手放行 vanilla

        // 主手 CWC 武器一律拦截 vanilla 攻击（无论冷却）
        event.setCanceled(true);
        // 不让 startAttack 走默认挥动（LocalPlayer.swing 发 ServerboundSwingPacket →
        // 服务端 handleAnimate → player.swing → ServerPlayer.swing 会 resetAttackStrengthTicker
        // 隐式重置攻速条），改为纯本地挥动（swing(hand,false) 不发包）
        event.setSwingHand(false);

        if (!CwcCombat.isCooldownReady(player, InteractionHand.MAIN_HAND, stack)) return; // 冷却未满：只取消不发包

        // 冷却满：客户端点选目标（眼睛到碰撞箱距离 ≤ reach，与主/副目标判定同款量法），发主手攻击包（-1 空挥）。
        // 横扫武器额外做双区域前置校验（客户端先验一遍能不能打中，与服务端同一几何）；normal/critical 维持准星点选即可
        int targetId = -1;
        double reach = CwcCombat.resolveReach(player, InteractionHand.MAIN_HAND, stack);
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() != player
                && ehr.getEntity().getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach
                && (CwcWeapon.attackStyle(stack) != PartTypeDef.AttackStyle.SWEEP
                    || CwcCombat.isInSweepDualRange(player, ehr.getEntity(), reach))) {
            targetId = ehr.getEntity().getId();
        }
        PacketDistributor.sendToServer(new CwcMainHandAttackPacket(targetId));
        mc.player.swing(InteractionHand.MAIN_HAND, false);  // 纯本地挥动（不发包）
        mc.player.resetAttackStrengthTicker();              // 本地攻速条/准星指示器同步归零
    }

    /**
     * 副手短刀右键攻击——目标判定在客户端（手感与瞄准一致）。
     * 右键（keyUse）在副手触发点、且副手是短刀武器时：取消原版右键动作（不发 use 包、不触发方块/实体交互），
     * 用客户端 {@link Minecraft#hitResult} 点选目标实体，发 {@link CwcOffhandAttackPacket}（带目标实体 id）
     * 给服务端权威执行伤害/冷却。空挥（无实体目标）发 -1，服务端仅进冷却 + 广播挥动。
     * 冷却期间放行原版流程——原版 useItem 也会因 isOnCooldown 直接 pass，不会出刀。
     * 取消后默认 setSwingHand(true)，由 startUseItem 触发本地副手挥动动画。
     */
    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getKeyMapping() != Minecraft.getInstance().options.keyUse) return;
        if (event.getHand() != InteractionHand.OFF_HAND) return;   // 只在副手触发点处理
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        ItemStack off = player.getOffhandItem();
        if (!CwcWeapon.isOffhandKnife(off)) return;                 // 副手不是短刀，放行原版
        if (CwcWeapon.isTwoHandedStack(player.getMainHandItem())) return; // 双手武器占用右键
        if (!CwcCombat.isCooldownReady(player, InteractionHand.OFF_HAND, off)) return; // 副手独立冷却中不出刀

        // 客户端点选目标：hitResult 命中实体、且在短刀自身攻击距离内才取其 id，否则空挥（-1）。
        // 距离用与主手原版一致的眼睛到碰撞箱量法，避免不同高度下副手距离偏短。
        int targetId = -1;
        double reach = CwcCombat.resolveReach(player, InteractionHand.OFF_HAND, off);
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() != player
                && ehr.getEntity().getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach) {
            targetId = ehr.getEntity().getId();
        }
        PacketDistributor.sendToServer(new CwcOffhandAttackPacket(targetId));

        event.setCanceled(true);
        // 不让 startUseItem 默认挥动（LocalPlayer.swing 会发 ServerboundSwingPacket，
        // 服务端 handleAnimate → player.swing → ServerPlayer.swing 会 resetAttackStrengthTicker
        // 把主手攻击强度清零）。改为纯本地挥动动画（swing(hand,false) 不发包），主副手完全解耦。
        event.setSwingHand(false);
        mc.player.swing(InteractionHand.OFF_HAND, false);
    }

    // —— 准星 / 攻击指示器（主手 CWC 武器时接管 crosshair 层自画）——

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
        // 顶部对齐锚点：冷却条(16×4)与满格图标(16×16)同顶，就绪时往下长；
        // 锚点取准星上方刚好不碰准星的最低位置（图标 16 高、底边距准星顶 h/2-7 留 1px → y = h/2-24）
        int y = gui.guiHeight() / 2 - 24;
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
            gui.blitSprite(OFFHAND_INDICATOR_FULL, x, y, 16, 16);  // 满格 16×16，与冷却条顶部对齐（往下长）
        } else if (ready < 1.0F) {
            gui.blitSprite(OFFHAND_INDICATOR_BACKGROUND, x, y, 16, 4);
            gui.blitSprite(OFFHAND_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, (int) (ready * 17.0F), 4);
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
