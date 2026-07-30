package com.funnyb.cwc.network;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.menu.CraftingMenu;
import com.funnyb.cwc.network.serverbound.CycleRecipePacket;
import com.funnyb.cwc.network.serverbound.OpenCraftingPacket;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * CWC 网络包注册中心。
 * 在 MOD 总线阶段注册所有客户端→服务端的数据包及其处理器。
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CwcNetwork {

    /** 注册所有自定义网络包的类型、编解码器和处理逻辑 */
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ColdWeaponCraftsmanship.MODID).versioned("1");

        // 客户端→服务端：打开制造界面
        registrar.playToServer(
                OpenCraftingPacket.TYPE,
                OpenCraftingPacket.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        player.openMenu(new SimpleMenuProvider(
                                (containerId, inventory, player1) -> new CraftingMenu(containerId, inventory),
                                CraftingMenu.TITLE));
                        // 界面打开后立即初始化配方显示
                        if (player.containerMenu instanceof CraftingMenu menu) {
                            menu.initRecipes();
                        }
                    }
                });

        // 客户端→服务端：轮询下一个配方
        registrar.playToServer(
                CycleRecipePacket.TYPE,
                CycleRecipePacket.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        if (player.containerMenu instanceof CraftingMenu menu) {
                            menu.cycleRecipe();
                        }
                    }
                });
    }
}
