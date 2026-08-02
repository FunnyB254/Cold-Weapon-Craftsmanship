package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.screen.CwcScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
     * 资源重载时只刷新 Layouts 缓存。
     * PartRegistry 数据由服务端 datapack 加载（{@link ColdWeaponCraftsmanship} 服务端事件），
     * 客户端资源管理器不含 data/，重载会把共享注册表清空（F3+T 后制造界面列表变空），故不在此重载。
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> Layouts.invalidate());
    }

}
