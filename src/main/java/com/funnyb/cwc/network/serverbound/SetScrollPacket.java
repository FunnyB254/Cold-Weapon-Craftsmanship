package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端网络包：同步零件列表的滚动行数。
 * 服务端据此把对应可见行的零件填入 3 个固定槽位。
 */
public record SetScrollPacket(int scrollRows) implements CustomPacketPayload {

    public static final Type<SetScrollPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "set_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetScrollPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SetScrollPacket::scrollRows, SetScrollPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
