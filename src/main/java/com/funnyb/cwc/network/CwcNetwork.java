package com.funnyb.cwc.network;

import com.funnyb.cwc.ColdWeaponCraftsmanship;
import com.funnyb.cwc.menu.AssemblingMenu;
import com.funnyb.cwc.menu.CraftingMenu;
import com.funnyb.cwc.network.serverbound.CwcMainHandAttackPacket;
import com.funnyb.cwc.network.serverbound.CwcOffhandAttackPacket;
import com.funnyb.cwc.network.serverbound.CycleRecipePacket;
import com.funnyb.cwc.network.serverbound.RenamePartPacket;
import com.funnyb.cwc.network.serverbound.SelectPartPacket;
import com.funnyb.cwc.network.serverbound.SetScrollPacket;

import com.funnyb.cwc.combat.CwcCombat;
import com.funnyb.cwc.combat.behavior.BehaviorDispatch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * CWC 网络包注册中心。
 * 在 MOD 总线阶段注册所有客户端→服务端的数据包及其处理器。
 */
@EventBusSubscriber(modid = ColdWeaponCraftsmanship.MODID)
public class CwcNetwork {

    /** 注册所有自定义网络包的类型、编解码器和处理逻辑 */
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ColdWeaponCraftsmanship.MODID).versioned("1");

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

        // 客户端→服务端：选中零件类型
        registrar.playToServer(
                SelectPartPacket.TYPE,
                SelectPartPacket.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        if (player.containerMenu instanceof CraftingMenu menu) {
                            menu.selectType(payload.typeId());
                        }
                    }
                });

        // 客户端→服务端：改名
        registrar.playToServer(
                RenamePartPacket.TYPE,
                RenamePartPacket.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        if (player.containerMenu instanceof AssemblingMenu menu) {
                            menu.renameItem(payload.newName());
                        }
                    }
                });

        // 客户端→服务端：零件列表滚动行数同步
        registrar.playToServer(
                SetScrollPacket.TYPE,
                SetScrollPacket.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        if (player.containerMenu instanceof AssemblingMenu menu) {
                            menu.setScrollRows(payload.scrollRows());
                        }
                    }
                });

        // 客户端→服务端：主手 CWC 武器普攻（按刃型普攻方式，服务端权威执行）
        registrar.playToServer(
                CwcMainHandAttackPacket.TYPE,
                CwcMainHandAttackPacket.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        CwcCombat.performMainHandAttack(player, payload.targetId());
                    }
                });

        // 客户端→服务端：副手右键出手（目标由客户端 hitResult 点选，服务端权威执行）。
        // 具体动作由"这只手右键胜出的行为"决定（短刀 = cwc:swing_use），见 BehaviorDispatch。
        registrar.playToServer(
                CwcOffhandAttackPacket.TYPE,
                CwcOffhandAttackPacket.STREAM_CODEC,
                (payload, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        BehaviorDispatch.serverUse(player, InteractionHand.OFF_HAND, payload.targetId());
                    }
                });

    }
}
