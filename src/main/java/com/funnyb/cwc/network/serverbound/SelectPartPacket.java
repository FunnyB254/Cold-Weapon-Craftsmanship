package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端选中材料变体时发给服务端，更新当前配方。
 */
public record SelectPartPacket(String partId) implements CustomPacketPayload {

    public static final Type<SelectPartPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "select_part"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectPartPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SelectPartPacket::partId, SelectPartPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
