package com.funnyb.cwc.client;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.crafting.PartRegistry;
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

    /** 资源重载时刷新 Layouts 缓存和 PartRegistry */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            Layouts.invalidate();
            PartRegistry.reload(manager);
        });
    }
}
