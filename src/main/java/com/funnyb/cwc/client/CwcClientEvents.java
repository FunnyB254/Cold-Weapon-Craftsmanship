package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.client.renderer.AssembledWeaponRenderer;
import com.funnyb.cwc.registry.CwcItems;
import com.funnyb.cwc.screen.CwcScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

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

}
