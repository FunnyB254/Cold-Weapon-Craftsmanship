package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端网络包：请求打开装配界面。
 */
public record OpenAssemblingPacket() implements CustomPacketPayload {

    public static final Type<OpenAssemblingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "open_assembling"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAssemblingPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenAssemblingPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
