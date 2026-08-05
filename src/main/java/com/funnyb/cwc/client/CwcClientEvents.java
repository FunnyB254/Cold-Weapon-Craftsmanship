package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.client.renderer.AssembledWeaponRenderer;
import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.item.CwcWeapon;
import com.funnyb.cwc.network.serverbound.CwcOffhandAttackPacket;
import com.funnyb.cwc.registry.CwcItems;
import com.funnyb.cwc.screen.CwcScreen;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
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
     * 强制攻击冷却——输入层拦截（客户端主拦截）。
     * 主手持本模组武器（HANDLE_PART）且攻击冷却未满时，取消攻击键触发：
     * {@code startAttack()} 不执行 → 不挥剑、不重置冷却条、不发攻击包 → 服务器不命中，
     * 实现"强制等冷却完才能攻击"。冷却满放行；非本模组武器不受影响。
     * 服务器端 AttackEntityEvent 兜底见 {@link com.funnyb.cwc.combat.CwcCombatEvents}。
     */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getKeyMapping() != Minecraft.getInstance().options.keyAttack) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != CwcItems.HANDLE_PART.get()) return;  // 只限制本模组武器
        if (player.getAttackStrengthScale(0f) >= 1.0f) return;      // 冷却满，放行
        event.setCanceled(true);                                    // 冷却未满，阻止攻击动作
        event.setSwingHand(false);                                  // 手也不挥
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

    // —— 副手短刀攻击指示器（复刻原版 crosshair 攻击指示器样式，显示在准星上方）——

    private static final ResourceLocation OFFHAND_INDICATOR_BACKGROUND =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final ResourceLocation OFFHAND_INDICATOR_PROGRESS =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
    private static final ResourceLocation OFFHAND_INDICATOR_FULL =
            ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_full");

    /**
     * 副手短刀攻击指示器：准星上方显示短刀就绪度进度条（复刻原版 crosshair 攻击指示器样式）。
     * 数据 = 1 - 短刀物品冷却占比：冷却中显示进度条（0→100%），就绪且准星对准活体时显示满格图标。
     * 只显示短刀就绪状态，原版主手攻击指示器（准星下方）不受影响；副手不是短刀/主手双手武器/旁观者时不显示。
     */
    @SubscribeEvent
    public static void onCrosshairPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;    // 只第一人称显示（与准星一致）
        if (player.isSpectator()) return;                            // 旁观者不攻击
        ItemStack off = player.getOffhandItem();
        if (!CwcWeapon.isOffhandKnife(off)) return;                  // 副手不是短刀
        if (CwcWeapon.isTwoHandedStack(player.getMainHandItem())) return; // 双手武器占用右键

        // 就绪度 = 1 - 副手独立冷却占比（1 可攻击，0 刚出刀）；partial tick 固定 0（与原版 getAttackStrengthScale(0.0F) 一致）
        float ready = CwcCombat.offhandReadiness(player);
        // 满格图标只在准星实体处于短刀自身攻击距离内时显示，避免"瞄着够不着的目标却提示可攻击"
        double reach = CwcCombat.resolveReach(player, InteractionHand.OFF_HAND, off);
        GuiGraphics gui = event.getGuiGraphics();
        int x = gui.guiWidth() / 2 - 8;      // 16 宽居中于准星
        // 顶部对齐锚点：冷却条(16×4)与满格图标(16×16)同顶，就绪时往下长；
        // 锚点取准星上方刚好不碰准星的最低位置（图标 16 高、底边距准星顶 h/2-7 留 1px → y = h/2-24）
        int y = gui.guiHeight() / 2 - 24;
        // 反色混合：任意背景下都可见（与原版准星指示器一致）
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        if (ready >= 1.0F) {
            if (mc.crosshairPickEntity instanceof LivingEntity
                    && mc.crosshairPickEntity.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach) {
                gui.blitSprite(OFFHAND_INDICATOR_FULL, x, y, 16, 16);  // 满格 16×16，与冷却条顶部对齐（往下长）
            }
        } else {
            gui.blitSprite(OFFHAND_INDICATOR_BACKGROUND, x, y, 16, 4);
            gui.blitSprite(OFFHAND_INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, (int) (ready * 17.0F), 4);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

}
