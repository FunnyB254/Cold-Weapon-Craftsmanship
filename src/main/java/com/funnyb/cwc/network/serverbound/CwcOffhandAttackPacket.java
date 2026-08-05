package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端网络包：副手短刀右键攻击。
 * 目标选择（打谁）在客户端用原版 hitResult 点选完成，这里只带目标实体 id；
 * 服务端据此做权威攻击（伤害/冷却），不再自行视线射线。
 *
 * @param targetId 瞄准的实体 id；-1 表示无目标（空挥，仅挥动 + 进冷却）
 */
public record CwcOffhandAttackPacket(int targetId) implements CustomPacketPayload {

    public static final Type<CwcOffhandAttackPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "offhand_attack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CwcOffhandAttackPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CwcOffhandAttackPacket::targetId, CwcOffhandAttackPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
