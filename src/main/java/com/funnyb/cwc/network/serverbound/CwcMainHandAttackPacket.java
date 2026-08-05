package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端网络包：主手 CWC 武器普攻。
 * 与副手出刀同构——目标选择在客户端用原版 hitResult 点选完成，这里只带目标实体 id；
 * 服务端按刃型普攻方式（AttackStyle）权威执行（伤害/冷却），不再走 vanilla Player.attack。
 *
 * @param targetId 瞄准的实体 id；-1 表示无目标（空挥。normal 空挥仅挥动+进冷却；sweep 空挥照样结算范围伤害）
 */
public record CwcMainHandAttackPacket(int targetId) implements CustomPacketPayload {

    public static final Type<CwcMainHandAttackPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "mainhand_attack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CwcMainHandAttackPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CwcMainHandAttackPacket::targetId, CwcMainHandAttackPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
