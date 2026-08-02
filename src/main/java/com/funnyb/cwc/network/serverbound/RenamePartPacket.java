package com.funnyb.cwc.network.serverbound;

import com.funnyb.cwc.ColdWeaponCraftsmanship;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端→服务端网络包：请求修改装配界面底座物品的名称。
 */
public record RenamePartPacket(String newName) implements CustomPacketPayload {

    public static final Type<RenamePartPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColdWeaponCraftsmanship.MODID, "rename_part"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenamePartPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RenamePartPacket::newName, RenamePartPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
